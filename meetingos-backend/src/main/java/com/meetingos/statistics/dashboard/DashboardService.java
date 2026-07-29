package com.meetingos.statistics.dashboard;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingos.attendee.Attendee;
import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.attendee.AttendeeType;
import com.meetingos.attendee.CheckinStatus;
import com.meetingos.attendee.ReviewStatus;
import com.meetingos.common.BusinessException;
import com.meetingos.dinner.DinnerAssignmentRepository;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import com.meetingos.seating.SeatAssignmentRepository;
import com.meetingos.tduck.AttendeeField;
import com.meetingos.tduck.TduckFieldMapping;
import com.meetingos.tduck.TduckFieldMappingRepository;
import com.meetingos.tduck.TduckFieldMappingResponse;
import com.meetingos.tduck.TduckService;
import com.meetingos.tduck.TduckSubmissionFields;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * 会议级自定义统计看板。
 *
 * <p>当前单场会议规模不超过约一千人，查询时按会议一次性读取参会人及其 TDuck 快照，
 * 再对已验证的字段目录做内存聚合。这样既避免数据库 JSON 字段的方言差异，也杜绝将
 * 字段名、聚合方式或筛选条件直接拼接为 SQL。</p>
 */
@Service
public class DashboardService {

    private static final int MAX_FIELD_OPTIONS = 50;
    private static final String EMPTY_VALUE_KEY = "__EMPTY__";
    private static final String EMPTY_VALUE_LABEL = "未填写";
    private static final String TDUCK_OPTION_LABEL_SUFFIX = "label";

    private final MeetingRepository meetingRepository;
    private final AttendeeRepository attendeeRepository;
    private final SeatAssignmentRepository seatAssignmentRepository;
    private final DinnerAssignmentRepository dinnerAssignmentRepository;
    private final TduckFieldMappingRepository fieldMappingRepository;
    private final TduckService tduckService;
    private final MeetingDashboardConfigRepository configRepository;
    private final ObjectMapper objectMapper;

    public DashboardService(MeetingRepository meetingRepository,
                            AttendeeRepository attendeeRepository,
                            SeatAssignmentRepository seatAssignmentRepository,
                            DinnerAssignmentRepository dinnerAssignmentRepository,
                            TduckFieldMappingRepository fieldMappingRepository,
                            TduckService tduckService,
                            MeetingDashboardConfigRepository configRepository,
                            ObjectMapper objectMapper) {
        this.meetingRepository = meetingRepository;
        this.attendeeRepository = attendeeRepository;
        this.seatAssignmentRepository = seatAssignmentRepository;
        this.dinnerAssignmentRepository = dinnerAssignmentRepository;
        this.fieldMappingRepository = fieldMappingRepository;
        this.tduckService = tduckService;
        this.configRepository = configRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public DashboardFieldCatalogResponse fields(Long meetingId) {
        ensureMeetingExists(meetingId);
        List<DashboardRow> rows = loadRows(meetingId);
        Map<String, FieldDefinition> catalog = fieldCatalog(meetingId, rows, currentTduckFieldLabels(meetingId));
        return new DashboardFieldCatalogResponse(
                catalog.values().stream().map(FieldDefinition::descriptor).toList()
        );
    }

    @Transactional(readOnly = true)
    public DashboardConfigResponse config(Long meetingId) {
        ensureMeetingExists(meetingId);
        return configRepository.findByMeetingId(meetingId)
                .map(config -> {
                    Map<String, FieldDefinition> catalog = fieldCatalog(meetingId, loadRows(meetingId));
                    return new DashboardConfigResponse(
                            meetingId,
                            readStoredComponents(config.getConfiguration(), catalog),
                            config.getUpdatedAt(),
                            false
                    );
                })
                .orElseGet(() -> new DashboardConfigResponse(meetingId, defaultComponents(), null, false));
    }

    @Transactional
    public DashboardConfigResponse saveConfig(DashboardConfigRequest request) {
        // 会议行锁将同一会议的首次配置创建串行化，避免两个管理员同时插入时命中唯一索引。
        Meeting meeting = meetingRepository.findByIdForUpdate(request.meetingId())
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        List<DashboardComponentRequest> components = normalizeComponents(request.components());
        Map<String, FieldDefinition> catalog = fieldCatalog(request.meetingId(), loadRows(request.meetingId()));
        components = canonicalizeComponentFieldKeys(components, catalog);
        for (DashboardComponentRequest component : components) {
            try {
                validateComponent(component, catalog);
            } catch (UnknownDashboardFieldException exception) {
                // 保存配置是显式用户操作，失效字段应作为 400 返回而不是暴露内部异常类型。
                throw BusinessException.badRequest(exception.getMessage());
            }
        }

        MeetingDashboardConfig config = configRepository.findByMeetingId(request.meetingId())
                .orElseGet(() -> {
                    MeetingDashboardConfig created = new MeetingDashboardConfig();
                    created.setMeeting(meeting);
                    return created;
                });
        config.setConfiguration(toStoredConfiguration(components));
        MeetingDashboardConfig saved = configRepository.save(config);
        return new DashboardConfigResponse(request.meetingId(), components, saved.getUpdatedAt(), false);
    }

    @Transactional(readOnly = true)
    public DashboardQueryResponse query(DashboardQueryRequest request) {
        ensureMeetingExists(request.meetingId());
        DateRange range = resolveRange(request.startDate(), request.endDate());
        List<DashboardRow> rows = loadRows(request.meetingId());
        Map<String, FieldDefinition> catalog = fieldCatalog(request.meetingId(), rows);
        List<DashboardRow> rangedRows = applyDateRange(rows, range);
        List<DashboardComponentResult> results = new ArrayList<>();
        for (DashboardComponentRequest component : request.components()) {
            results.add(queryComponent(component, rangedRows, catalog));
        }
        return new DashboardQueryResponse(List.copyOf(results), LocalDateTime.now());
    }

    private DashboardComponentResult queryComponent(DashboardComponentRequest component,
                                                    List<DashboardRow> rows,
                                                    Map<String, FieldDefinition> catalog) {
        if (component == null) {
            return invalidConfiguration(null, null, null, null, "组件不能为空");
        }
        try {
            DashboardComponentRequest normalized = canonicalizeComponentFieldKeys(normalizeComponent(component), catalog);
            FieldDefinition definition = catalog.get(normalized.fieldKey());
            if (definition == null) {
                return new DashboardComponentResult(
                        normalized.id(), normalized.title(), normalized.fieldKey(), normalized.aggregation(), normalized.visualization(),
                        DashboardComponentStatus.INVALID_FIELD, "统计字段已不存在或无权使用", null, 0, List.of()
                );
            }
            validateComponent(normalized, catalog);
            List<DashboardRow> filteredRows = applyFilters(rows, normalized.filters(), catalog);
            return aggregate(normalized, definition, filteredRows);
        } catch (UnknownDashboardFieldException exception) {
            return new DashboardComponentResult(
                    component.id(), component.title(), component.fieldKey(), component.aggregation(), component.visualization(),
                    DashboardComponentStatus.INVALID_FIELD, exception.getMessage(), null, 0, List.of()
            );
        } catch (BusinessException | IllegalArgumentException exception) {
            return invalidConfiguration(
                    component.id(), component.title(), component.fieldKey(), component.aggregation(), exception.getMessage(), component.visualization()
            );
        }
    }

    private DashboardComponentResult aggregate(DashboardComponentRequest component,
                                               FieldDefinition definition,
                                               List<DashboardRow> rows) {
        long total = rows.size();
        if (component.aggregation() == DashboardAggregation.COUNT) {
            BigDecimal count = BigDecimal.valueOf(total);
            return new DashboardComponentResult(
                    component.id(), resolvedTitle(component, definition), component.fieldKey(), component.aggregation(), component.visualization(),
                    total == 0 ? DashboardComponentStatus.EMPTY : DashboardComponentStatus.SUCCESS,
                    total == 0 ? "当前条件下没有数据" : null, count, total, List.of()
            );
        }

        if (component.aggregation() == DashboardAggregation.DISTRIBUTION) {
            Map<String, Long> counts = new HashMap<>();
            for (DashboardRow row : rows) {
                List<String> values = canonicalValues(definition, row);
                if (values.isEmpty()) {
                    counts.merge(EMPTY_VALUE_KEY, 1L, Long::sum);
                    continue;
                }
                for (String value : new LinkedHashSet<>(values)) {
                    counts.merge(value, 1L, Long::sum);
                }
            }
            List<DashboardDistributionItem> items = counts.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                            .thenComparing(entry -> displayValue(definition, entry.getKey())))
                    .limit(component.limit())
                    .map(entry -> new DashboardDistributionItem(
                            entry.getKey(), displayValue(definition, entry.getKey()), entry.getValue()
                    ))
                    .toList();
            return new DashboardComponentResult(
                    component.id(), resolvedTitle(component, definition), component.fieldKey(), component.aggregation(), component.visualization(),
                    rows.isEmpty() ? DashboardComponentStatus.EMPTY : DashboardComponentStatus.SUCCESS,
                    rows.isEmpty() ? "当前条件下没有数据" : null,
                    BigDecimal.valueOf(total), total, items
            );
        }

        List<String> values = rows.stream()
                .flatMap(row -> canonicalValues(definition, row).stream())
                .toList();
        if (component.aggregation() == DashboardAggregation.FILLED_COUNT) {
            long count = rows.stream().filter(row -> !canonicalValues(definition, row).isEmpty()).count();
            return numericResult(component, definition, total, BigDecimal.valueOf(count), count == 0);
        }
        if (component.aggregation() == DashboardAggregation.DISTINCT_COUNT) {
            long count = new LinkedHashSet<>(values).size();
            return numericResult(component, definition, total, BigDecimal.valueOf(count), count == 0);
        }

        List<BigDecimal> numbers = values.stream()
                .map(this::parseStrictDecimal)
                .filter(Objects::nonNull)
                .toList();
        if (numbers.isEmpty()) {
            return numericResult(component, definition, total, BigDecimal.ZERO, true);
        }
        BigDecimal value = switch (component.aggregation()) {
            case SUM -> numbers.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            case AVERAGE -> numbers.stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(numbers.size()), 4, RoundingMode.HALF_UP);
            case MIN -> numbers.stream().min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
            case MAX -> numbers.stream().max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
            default -> throw BusinessException.badRequest("不支持的数值聚合方式");
        };
        return numericResult(component, definition, total, value, false);
    }

    private DashboardComponentResult numericResult(DashboardComponentRequest component,
                                                   FieldDefinition definition,
                                                   long total,
                                                   BigDecimal value,
                                                   boolean empty) {
        return new DashboardComponentResult(
                component.id(), resolvedTitle(component, definition), component.fieldKey(), component.aggregation(), component.visualization(),
                empty ? DashboardComponentStatus.EMPTY : DashboardComponentStatus.SUCCESS,
                empty ? "当前条件下没有可统计的字段值" : null,
                normalizeDecimal(value), total, List.of()
        );
    }

    private DashboardComponentResult invalidConfiguration(String id,
                                                          String title,
                                                          String fieldKey,
                                                          DashboardAggregation aggregation,
                                                          String message) {
        return invalidConfiguration(id, title, fieldKey, aggregation, message, null);
    }

    private DashboardComponentResult invalidConfiguration(String id,
                                                          String title,
                                                          String fieldKey,
                                                          DashboardAggregation aggregation,
                                                          String message,
                                                          DashboardVisualization visualization) {
        return new DashboardComponentResult(
                id, title, fieldKey, aggregation, visualization,
                DashboardComponentStatus.INVALID_CONFIGURATION,
                firstNonBlank(message, "组件配置无效"), null, 0, List.of()
        );
    }

    private String resolvedTitle(DashboardComponentRequest component, FieldDefinition definition) {
        return firstNonBlank(component.title(), definition.descriptor().label());
    }

    private List<DashboardRow> applyFilters(List<DashboardRow> rows,
                                            List<DashboardFilterRequest> filters,
                                            Map<String, FieldDefinition> catalog) {
        if (filters.isEmpty()) {
            return rows;
        }
        List<ResolvedFilter> resolved = new ArrayList<>();
        for (DashboardFilterRequest filter : filters) {
            FieldDefinition definition = catalog.get(trimToNull(filter.fieldKey()));
            if (definition == null) {
                throw new UnknownDashboardFieldException("筛选字段已不存在或无权使用");
            }
            validateFilter(filter, definition);
            resolved.add(new ResolvedFilter(definition, filter.operator(), normalizedFilterValues(filter, definition)));
        }
        return rows.stream().filter(row -> resolved.stream().allMatch(filter -> filter.matches(row))).toList();
    }

    private void validateComponent(DashboardComponentRequest component,
                                   Map<String, FieldDefinition> catalog) {
        if (component == null) {
            throw BusinessException.badRequest("组件不能为空");
        }
        FieldDefinition definition = catalog.get(component.fieldKey());
        if (definition == null) {
            throw new UnknownDashboardFieldException("统计字段已不存在或无权使用");
        }
        if (!definition.descriptor().allowedAggregations().contains(component.aggregation())) {
            throw BusinessException.badRequest("字段不支持该聚合方式");
        }
        if (component.aggregation() == DashboardAggregation.DISTRIBUTION) {
            if (component.visualization() == DashboardVisualization.NUMBER) {
                throw BusinessException.badRequest("分布统计必须使用柱图、饼图或表格展示");
            }
        } else if (component.visualization() != DashboardVisualization.NUMBER) {
            throw BusinessException.badRequest("数值统计必须使用数值卡展示");
        }
        if (!definition.descriptor().allowedVisualizations().contains(component.visualization())) {
            throw BusinessException.badRequest("字段不支持该展示形式");
        }
        for (DashboardFilterRequest filter : component.filters()) {
            FieldDefinition filterDefinition = catalog.get(filter.fieldKey());
            if (filterDefinition == null) {
                throw new UnknownDashboardFieldException("筛选字段已不存在或无权使用");
            }
            validateFilter(filter, filterDefinition);
        }
    }

    private void validateFilter(DashboardFilterRequest filter, FieldDefinition definition) {
        if (!definition.descriptor().allowedOperators().contains(filter.operator())) {
            throw BusinessException.badRequest("筛选字段不支持该条件");
        }
        int size = filter.values().size();
        switch (filter.operator()) {
            case IS_EMPTY, IS_NOT_EMPTY -> {
                if (size != 0) {
                    throw BusinessException.badRequest("空值筛选不能传入筛选值");
                }
            }
            case BETWEEN -> {
                if (size != 2) {
                    throw BusinessException.badRequest("区间筛选必须传入两个值");
                }
            }
            case EQUALS, NOT_EQUALS, GREATER_THAN, GREATER_THAN_OR_EQUAL, LESS_THAN, LESS_THAN_OR_EQUAL -> {
                if (size != 1) {
                    throw BusinessException.badRequest("该筛选条件必须传入一个值");
                }
            }
            case IN, NOT_IN -> {
                if (size == 0) {
                    throw BusinessException.badRequest("集合筛选至少传入一个值");
                }
            }
        }
        if (filter.operator() == DashboardFilterOperator.BETWEEN
                || filter.operator() == DashboardFilterOperator.GREATER_THAN
                || filter.operator() == DashboardFilterOperator.GREATER_THAN_OR_EQUAL
                || filter.operator() == DashboardFilterOperator.LESS_THAN
                || filter.operator() == DashboardFilterOperator.LESS_THAN_OR_EQUAL) {
            if (definition.descriptor().dataType() != DashboardFieldDataType.NUMBER
                    && definition.descriptor().dataType() != DashboardFieldDataType.DATE) {
                throw BusinessException.badRequest("只有数字和日期字段支持范围筛选");
            }
        }
        for (String value : filter.values()) {
            if (canonicalFilterValue(definition, value) == null) {
                throw BusinessException.badRequest("筛选值格式不正确");
            }
        }
    }

    private List<String> normalizedFilterValues(DashboardFilterRequest filter, FieldDefinition definition) {
        return filter.values().stream()
                .map(value -> canonicalFilterValue(definition, value))
                .filter(Objects::nonNull)
                .toList();
    }

    private Map<String, FieldDefinition> fieldCatalog(Long meetingId, List<DashboardRow> rows) {
        return fieldCatalog(meetingId, rows, Map.of());
    }

    private Map<String, FieldDefinition> fieldCatalog(Long meetingId,
                                                       List<DashboardRow> rows,
                                                       Map<String, String> currentTduckFieldLabels) {
        LinkedHashMap<String, FieldSeed> seeds = systemFieldSeeds();
        List<TduckFieldMapping> mappings = fieldMappingRepository.findByMeetingIdOrderByIdAsc(meetingId);
        Set<String> knownTduckFieldKeys = knownTduckFieldKeys(mappings, currentTduckFieldLabels);
        for (TduckFieldMapping mapping : mappings) {
            String rawFieldKey = canonicalTduckRawFieldKey(
                    trimToNull(mapping.getTduckFieldKey()),
                    knownTduckFieldKeys,
                    Set.of()
            );
            if (rawFieldKey == null) {
                continue;
            }
            String key = tduckCatalogKey(rawFieldKey);
            seeds.putIfAbsent(key, tduckFieldSeed(
                    key,
                    firstNonBlank(
                            currentTduckFieldLabels.get(rawFieldKey),
                            firstNonBlank(mapping.getTduckFieldLabel(), rawFieldKey)
                    ),
                    rawFieldKey,
                    mapping.getAttendeeField(),
                    prefersTduckOptionLabel(rawFieldKey, knownTduckFieldKeys)
            ));
        }

        /*
         * 已同步映射表可能落后于 TDuck 表单调整。保留快照中存在的未知键，保证管理员
         * 至少可以对历史已入库的全部表单字段做只读统计；key 始终作为精确 Map key 校验，
         * 不会成为查询表达式或数据库列名。
         */
        for (DashboardRow row : rows) {
            Map<String, Object> snapshot = row.attendee().getRawDataSnapshot();
            if (snapshot == null) {
                continue;
            }
            Set<String> snapshotFieldKeys = snapshot.keySet().stream()
                    .filter(this::hasText)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            for (String rawFieldKey : snapshot.keySet()) {
                if (!isEligibleRawField(rawFieldKey)) {
                    continue;
                }
                String canonicalRawFieldKey = canonicalTduckRawFieldKey(
                        rawFieldKey,
                        knownTduckFieldKeys,
                        snapshotFieldKeys
                );
                String key = tduckCatalogKey(canonicalRawFieldKey);
                seeds.putIfAbsent(key, tduckFieldSeed(
                        key,
                        firstNonBlank(currentTduckFieldLabels.get(canonicalRawFieldKey), canonicalRawFieldKey),
                        canonicalRawFieldKey,
                        null,
                        prefersTduckOptionLabel(canonicalRawFieldKey, knownTduckFieldKeys)
                ));
            }
        }

        LinkedHashMap<String, FieldDefinition> catalog = new LinkedHashMap<>();
        for (FieldSeed seed : seeds.values()) {
            DashboardFieldDataType dataType = resolveDataType(seed, rows);
            Map<String, String> labels = new LinkedHashMap<>(seed.staticLabels());
            long coverageCount = rows.stream().filter(row -> !canonicalValues(seed, dataType, row).isEmpty()).count();
            List<DashboardFieldOption> options = seed.staticLabels().isEmpty()
                    ? observedOptions(seed, dataType, rows)
                    : seed.staticLabels().entrySet().stream()
                            .map(entry -> new DashboardFieldOption(entry.getKey(), entry.getValue()))
                            .toList();
            DashboardFieldDescriptor descriptor = new DashboardFieldDescriptor(
                    seed.key(), seed.label(), seed.category(), seed.source(), dataType, seed.sensitive(), coverageCount,
                    allowedAggregations(dataType),
                    allowedVisualizations(dataType),
                    allowedOperators(dataType),
                    options
            );
            catalog.put(seed.key(), new FieldDefinition(descriptor, seed, labels));
        }
        return catalog;
    }

    private Set<String> knownTduckFieldKeys(List<TduckFieldMapping> mappings,
                                             Map<String, String> currentTduckFieldLabels) {
        Set<String> keys = new LinkedHashSet<>();
        for (TduckFieldMapping mapping : mappings) {
            String key = trimToNull(mapping.getTduckFieldKey());
            if (key != null) {
                keys.add(key);
            }
        }
        for (String key : currentTduckFieldLabels.keySet()) {
            String normalized = trimToNull(key);
            if (normalized != null) {
                keys.add(normalized);
            }
        }
        return keys;
    }

    private Map<String, String> currentTduckFieldLabels(Long meetingId) {
        /*
         * 只在字段目录加载时读取当前 TDuck 元数据，使未映射的自定义题目立即显示填写页标题。
         * 看板保存和聚合查询继续走本地快照与映射，避免统计操作受外部 TDuck 可用性和延迟影响。
         */
        LinkedHashMap<String, String> labels = new LinkedHashMap<>();
        List<TduckFieldMappingResponse> fields = tduckService.mappings(meetingId);
        if (fields == null) {
            return labels;
        }
        for (TduckFieldMappingResponse field : fields) {
            String rawFieldKey = trimToNull(field.tduckFieldKey());
            String label = trimToNull(field.tduckFieldLabel());
            if (rawFieldKey != null && label != null) {
                labels.putIfAbsent(rawFieldKey, label);
            }
        }
        return labels;
    }

    private LinkedHashMap<String, FieldSeed> systemFieldSeeds() {
        LinkedHashMap<String, FieldSeed> seeds = new LinkedHashMap<>();
        seeds.put("system:attendee", systemSeed("system:attendee", "报名总人数", "报名", DashboardFieldDataType.RECORD,
                false, row -> List.of(), Map.of()));
        seeds.put("system:name", systemSeed("system:name", "姓名", "报名", DashboardFieldDataType.TEXT,
                true, row -> singleValue(row.attendee().getName()), Map.of()));
        seeds.put("system:phone", systemSeed("system:phone", "手机号", "报名", DashboardFieldDataType.TEXT,
                true, row -> singleValue(row.attendee().getPhone()), Map.of()));
        seeds.put("system:organization", systemSeed("system:organization", "单位", "报名", DashboardFieldDataType.TEXT,
                false, row -> singleValue(row.attendee().getOrganization()), Map.of()));
        seeds.put("system:title", systemSeed("system:title", "职务", "报名", DashboardFieldDataType.TEXT,
                false, row -> singleValue(row.attendee().getTitle()), Map.of()));
        seeds.put("system:identityNo", systemSeed("system:identityNo", "证件号", "报名", DashboardFieldDataType.TEXT,
                true, row -> singleValue(row.attendee().getIdentityNo()), Map.of()));
        seeds.put("system:email", systemSeed("system:email", "邮箱", "报名", DashboardFieldDataType.TEXT,
                true, row -> singleValue(row.attendee().getEmail()), Map.of()));
        seeds.put("system:attendeeType", systemSeed("system:attendeeType", "参会人类型", "报名", DashboardFieldDataType.TEXT,
                false, row -> singleValue(row.attendee().getAttendeeType()), attendeeTypeLabels()));
        seeds.put("system:reviewStatus", systemSeed("system:reviewStatus", "审核状态", "报名", DashboardFieldDataType.TEXT,
                false, row -> singleValue(row.attendee().getReviewStatus()), reviewStatusLabels()));
        seeds.put("system:checkinStatus", systemSeed("system:checkinStatus", "签到状态", "签到", DashboardFieldDataType.TEXT,
                false, row -> singleValue(row.attendee().getCheckinStatus()), checkinStatusLabels()));
        seeds.put("system:accommodationRequired", systemSeed("system:accommodationRequired", "是否需要住宿", "住宿", DashboardFieldDataType.BOOLEAN,
                false, row -> singleValue(row.attendee().isAccommodationRequired()), booleanLabels()));
        seeds.put("system:roomNo", systemSeed("system:roomNo", "房间号", "住宿", DashboardFieldDataType.TEXT,
                false, row -> singleValue(row.attendee().getRoomNo()), Map.of()));
        seeds.put("system:roomRemark", systemSeed("system:roomRemark", "住宿备注", "住宿", DashboardFieldDataType.TEXT,
                false, row -> singleValue(row.attendee().getRoomRemark()), Map.of()));
        seeds.put("system:roomAssigned", systemSeed("system:roomAssigned", "是否已安排住宿", "住宿", DashboardFieldDataType.BOOLEAN,
                false, row -> singleValue(hasText(row.attendee().getRoomNo())), booleanLabels()));
        seeds.put("system:seatAssigned", systemSeed("system:seatAssigned", "是否已安排座位", "座位", DashboardFieldDataType.BOOLEAN,
                false, row -> singleValue(row.seatAssigned()), booleanLabels()));
        seeds.put("system:dinnerAssigned", systemSeed("system:dinnerAssigned", "是否已安排晚宴", "晚宴", DashboardFieldDataType.BOOLEAN,
                false, row -> singleValue(row.dinnerAssigned()), booleanLabels()));
        seeds.put("system:registeredAt", systemSeed("system:registeredAt", "报名时间", "报名", DashboardFieldDataType.DATE,
                false, row -> singleValue(row.attendee().getRegisteredAt()), Map.of()));
        seeds.put("system:checkedInAt", systemSeed("system:checkedInAt", "签到时间", "签到", DashboardFieldDataType.DATE,
                false, row -> singleValue(row.attendee().getCheckedInAt()), Map.of()));
        seeds.put("system:checkinEntrance", systemSeed("system:checkinEntrance", "签到入口", "签到", DashboardFieldDataType.TEXT,
                false, row -> singleValue(row.attendee().getCheckinEntrance()), Map.of()));
        seeds.put("system:checkinOperator", systemSeed("system:checkinOperator", "签到操作人", "签到", DashboardFieldDataType.TEXT,
                false, row -> singleValue(row.attendee().getCheckinOperator()), Map.of()));
        seeds.put("system:reviewedAt", systemSeed("system:reviewedAt", "审核时间", "报名", DashboardFieldDataType.DATE,
                false, row -> singleValue(row.attendee().getReviewedAt()), Map.of()));
        seeds.put("system:reviewedBy", systemSeed("system:reviewedBy", "审核操作人", "报名", DashboardFieldDataType.TEXT,
                false, row -> singleValue(row.attendee().getReviewedBy()), Map.of()));
        seeds.put("system:reviewReason", systemSeed("system:reviewReason", "审核原因", "报名", DashboardFieldDataType.TEXT,
                false, row -> singleValue(row.attendee().getReviewReason()), Map.of()));
        seeds.put("system:reviewRemark", systemSeed("system:reviewRemark", "审核备注", "报名", DashboardFieldDataType.TEXT,
                false, row -> singleValue(row.attendee().getReviewRemark()), Map.of()));
        seeds.put("system:remark", systemSeed("system:remark", "报名备注", "报名", DashboardFieldDataType.TEXT,
                false, row -> singleValue(row.attendee().getRemark()), Map.of()));
        seeds.put("system:attendeeCode", systemSeed("system:attendeeCode", "参会编号", "报名", DashboardFieldDataType.TEXT,
                false, row -> singleValue(row.attendee().getAttendeeCode()), Map.of()));
        seeds.put("system:tags", systemSeed("system:tags", "标签", "报名", DashboardFieldDataType.MULTI_SELECT,
                false, row -> splitTags(row.attendee().getTags()), Map.of()));
        return seeds;
    }

    private FieldSeed systemSeed(String key,
                                 String label,
                                 String category,
                                 DashboardFieldDataType type,
                                 boolean sensitive,
                                 Function<DashboardRow, List<Object>> valueReader,
                                 Map<String, String> staticLabels) {
        return new FieldSeed(key, label, category, DashboardFieldSource.SYSTEM, type, sensitive,
                null, false, valueReader, staticLabels, false);
    }

    private FieldSeed tduckFieldSeed(String key,
                                     String label,
                                     String rawFieldKey,
                                     AttendeeField attendeeField,
                                     boolean preferOptionLabel) {
        DashboardFieldDataType type = dataTypeFor(attendeeField);
        boolean sensitive = isSensitive(attendeeField, rawFieldKey, label);
        return new FieldSeed(
                key,
                label,
                "TDuck 表单",
                DashboardFieldSource.TDUCK,
                type,
                sensitive,
                rawFieldKey,
                preferOptionLabel,
                row -> rawSnapshotValues(row.attendee().getRawDataSnapshot(), rawFieldKey, preferOptionLabel),
                Map.of(),
                type == DashboardFieldDataType.NUMBER
        );
    }

    private DashboardFieldDataType resolveDataType(FieldSeed seed, List<DashboardRow> rows) {
        if (seed.declaredType() != null) {
            return seed.declaredType();
        }
        if (seed.rawFieldKey() == null) {
            return DashboardFieldDataType.TEXT;
        }
        boolean sawValue = false;
        boolean allNumbers = true;
        boolean allBooleans = true;
        boolean multiValue = false;
        for (DashboardRow row : rows) {
            Object raw = preferredRawSnapshotValue(
                    row.attendee().getRawDataSnapshot(),
                    seed.rawFieldKey(),
                    seed.preferOptionLabel()
            );
            if (!hasRawValue(raw)) {
                continue;
            }
            sawValue = true;
            multiValue |= isMultipleRawValue(raw);
            for (Object item : flattenRawValue(raw)) {
                allNumbers &= item instanceof Number;
                allBooleans &= item instanceof Boolean;
            }
        }
        if (multiValue || isLikelyMultiSelect(seed.rawFieldKey(), seed.label())) {
            return DashboardFieldDataType.MULTI_SELECT;
        }
        // 只接受 JSON 原生 Number/Boolean，不能把任意文本 "001" 或日期文本猜成数字/日期。
        if (sawValue && allNumbers) {
            return DashboardFieldDataType.NUMBER;
        }
        if (sawValue && allBooleans) {
            return DashboardFieldDataType.BOOLEAN;
        }
        return DashboardFieldDataType.TEXT;
    }

    private List<DashboardFieldOption> observedOptions(FieldSeed seed,
                                                       DashboardFieldDataType dataType,
                                                       List<DashboardRow> rows) {
        if (dataType == DashboardFieldDataType.RECORD || dataType == DashboardFieldDataType.NUMBER
                || dataType == DashboardFieldDataType.DATE) {
            return List.of();
        }
        Map<String, Long> counts = new HashMap<>();
        for (DashboardRow row : rows) {
            for (String value : canonicalValues(seed, dataType, row)) {
                counts.merge(value, 1L, Long::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .limit(MAX_FIELD_OPTIONS)
                .map(entry -> new DashboardFieldOption(entry.getKey(), entry.getKey()))
                .toList();
    }

    private List<DashboardAggregation> allowedAggregations(DashboardFieldDataType dataType) {
        if (dataType == DashboardFieldDataType.RECORD) {
            return List.of(DashboardAggregation.COUNT);
        }
        List<DashboardAggregation> result = new ArrayList<>(List.of(
                DashboardAggregation.COUNT,
                DashboardAggregation.FILLED_COUNT,
                DashboardAggregation.DISTINCT_COUNT
        ));
        result.add(DashboardAggregation.DISTRIBUTION);
        if (dataType == DashboardFieldDataType.NUMBER) {
            result.add(DashboardAggregation.SUM);
            result.add(DashboardAggregation.AVERAGE);
            result.add(DashboardAggregation.MIN);
            result.add(DashboardAggregation.MAX);
        }
        return List.copyOf(result);
    }

    private List<DashboardVisualization> allowedVisualizations(DashboardFieldDataType dataType) {
        if (dataType == DashboardFieldDataType.RECORD) {
            return List.of(DashboardVisualization.NUMBER);
        }
        return List.of(DashboardVisualization.NUMBER, DashboardVisualization.BAR,
                DashboardVisualization.PIE, DashboardVisualization.TABLE);
    }

    private List<DashboardFilterOperator> allowedOperators(DashboardFieldDataType dataType) {
        if (dataType == DashboardFieldDataType.RECORD) {
            return List.of();
        }
        List<DashboardFilterOperator> operators = new ArrayList<>(List.of(
                DashboardFilterOperator.EQUALS,
                DashboardFilterOperator.NOT_EQUALS,
                DashboardFilterOperator.IN,
                DashboardFilterOperator.NOT_IN,
                DashboardFilterOperator.IS_EMPTY,
                DashboardFilterOperator.IS_NOT_EMPTY
        ));
        if (dataType == DashboardFieldDataType.NUMBER || dataType == DashboardFieldDataType.DATE) {
            operators.addAll(List.of(
                    DashboardFilterOperator.GREATER_THAN,
                    DashboardFilterOperator.GREATER_THAN_OR_EQUAL,
                    DashboardFilterOperator.LESS_THAN,
                    DashboardFilterOperator.LESS_THAN_OR_EQUAL,
                    DashboardFilterOperator.BETWEEN
            ));
        }
        return List.copyOf(operators);
    }

    private List<DashboardRow> loadRows(Long meetingId) {
        List<Attendee> attendees = attendeeRepository.findByMeetingIdOrderByIdAsc(meetingId);
        Set<Long> seatAssignedAttendees = new LinkedHashSet<>(seatAssignmentRepository.findAssignedAttendeeIdsByMeetingId(meetingId));
        Set<Long> dinnerAssignedAttendees = new LinkedHashSet<>(dinnerAssignmentRepository.findAssignedAttendeeIdsByMeetingId(meetingId));
        return attendees.stream()
                .map(attendee -> new DashboardRow(
                        attendee,
                        seatAssignedAttendees.contains(attendee.getId()),
                        dinnerAssignedAttendees.contains(attendee.getId())
                ))
                .toList();
    }

    private List<DashboardRow> applyDateRange(List<DashboardRow> rows, DateRange range) {
        if (range == null) {
            return rows;
        }
        return rows.stream().filter(row -> {
            LocalDateTime registeredAt = row.attendee().getRegisteredAt();
            return registeredAt != null
                    && !registeredAt.isBefore(range.start().atStartOfDay())
                    && registeredAt.isBefore(range.end().plusDays(1).atStartOfDay());
        }).toList();
    }

    private DateRange resolveRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) {
            return null;
        }
        LocalDate resolvedEnd = endDate == null ? LocalDate.now() : endDate;
        LocalDate resolvedStart = startDate == null ? resolvedEnd.minusDays(6) : startDate;
        if (resolvedStart.isAfter(resolvedEnd)) {
            throw BusinessException.badRequest("统计开始日期不能晚于结束日期");
        }
        if (resolvedStart.plusDays(366).isBefore(resolvedEnd)) {
            throw BusinessException.badRequest("统计时间范围不能超过 366 天");
        }
        return new DateRange(resolvedStart, resolvedEnd);
    }

    private List<String> canonicalValues(FieldDefinition definition, DashboardRow row) {
        return canonicalValues(definition.seed(), definition.descriptor().dataType(), row);
    }

    private List<String> canonicalValues(FieldSeed seed, DashboardFieldDataType dataType, DashboardRow row) {
        return seed.valueReader().apply(row).stream()
                .map(value -> canonicalValue(value, dataType, seed.coerceStringNumber()))
                .filter(Objects::nonNull)
                .flatMap(value -> dataType == DashboardFieldDataType.MULTI_SELECT
                        ? splitMultiSelectValue(value).stream()
                        : java.util.stream.Stream.of(value))
                .distinct()
                .toList();
    }

    private String canonicalValue(Object value, DashboardFieldDataType dataType, boolean coerceStringNumber) {
        if (value == null) {
            return null;
        }
        return switch (dataType) {
            case NUMBER -> {
                BigDecimal number = toDecimal(value, coerceStringNumber);
                yield number == null ? null : normalizeDecimal(number).toPlainString();
            }
            case DATE -> canonicalDate(value);
            case BOOLEAN -> canonicalBoolean(value);
            case RECORD -> null;
            case TEXT, MULTI_SELECT -> textValue(value);
        };
    }

    private String canonicalFilterValue(FieldDefinition definition, String value) {
        return canonicalValue(value, definition.descriptor().dataType(), true);
    }

    private String canonicalDate(Object value) {
        if (value instanceof LocalDate date) {
            return date.toString();
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toLocalDate().toString();
        }
        if (value instanceof OffsetDateTime dateTime) {
            return dateTime.toLocalDate().toString();
        }
        if (value instanceof ZonedDateTime dateTime) {
            return dateTime.toLocalDate().toString();
        }
        if (value instanceof Instant instant) {
            return instant.atZone(ZoneId.systemDefault()).toLocalDate().toString();
        }
        if (value instanceof TemporalAccessor) {
            try {
                return LocalDate.from((TemporalAccessor) value).toString();
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        String text = textValue(value);
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text).toString();
        } catch (RuntimeException ignored) {
            try {
                return OffsetDateTime.parse(text).toLocalDate().toString();
            } catch (RuntimeException ignoredAgain) {
                return null;
            }
        }
    }

    private String canonicalBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return Boolean.toString(bool);
        }
        String text = textValue(value);
        if (text == null) {
            return null;
        }
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "true", "1", "yes", "y", "是", "需要" -> "true";
            case "false", "0", "no", "n", "否", "不需要" -> "false";
            default -> null;
        };
    }

    private BigDecimal toDecimal(Object value, boolean allowString) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            try {
                return new BigDecimal(number.toString());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        if (!allowString) {
            return null;
        }
        String text = textValue(value);
        if (text == null || !text.matches("[-+]?(?:\\d+|\\d*\\.\\d+)")) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private BigDecimal parseStrictDecimal(String value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String textValue(Object value) {
        if (value == null) {
            return null;
        }
        String text;
        if (value instanceof Enum<?> enumeration) {
            text = enumeration.name();
        } else if (value instanceof BigDecimal decimal) {
            text = normalizeDecimal(decimal).toPlainString();
        } else {
            text = String.valueOf(value);
        }
        text = text.trim();
        return text.isEmpty() ? null : text;
    }

    private String displayValue(FieldDefinition definition, String value) {
        if (EMPTY_VALUE_KEY.equals(value)) {
            return EMPTY_VALUE_LABEL;
        }
        return definition.valueLabels().getOrDefault(value, value);
    }

    private Map<String, Object> toStoredConfiguration(List<DashboardComponentRequest> components) {
        List<Map<String, Object>> storedComponents = components.stream()
                .map(component -> objectMapper.convertValue(component, new TypeReference<Map<String, Object>>() { }))
                .toList();
        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("components", storedComponents);
        return configuration;
    }

    private List<DashboardComponentRequest> readStoredComponents(Map<String, Object> configuration,
                                                                  Map<String, FieldDefinition> catalog) {
        if (configuration == null || !(configuration.get("components") instanceof Collection<?> values)) {
            return List.of();
        }
        List<DashboardComponentRequest> components = new ArrayList<>();
        for (Object value : values) {
            try {
                components.add(canonicalizeComponentFieldKeys(
                        objectMapper.convertValue(value, DashboardComponentRequest.class),
                        catalog
                ));
            } catch (IllegalArgumentException ignored) {
                // 历史 JSON 中的单个坏组件不应让整个统计页不可打开，查询接口会返回组件级状态。
            }
        }
        return List.copyOf(components);
    }

    private List<DashboardComponentRequest> defaultComponents() {
        return List.of(
                new DashboardComponentRequest("registration-count", "报名总人数", "system:attendee",
                        DashboardAggregation.COUNT, DashboardVisualization.NUMBER, DashboardLimits.DEFAULT_TOP_LIMIT, List.of()),
                new DashboardComponentRequest("review-status", "审核状态", "system:reviewStatus",
                        DashboardAggregation.DISTRIBUTION, DashboardVisualization.PIE, DashboardLimits.DEFAULT_TOP_LIMIT, List.of()),
                new DashboardComponentRequest("checkin-status", "签到状态", "system:checkinStatus",
                        DashboardAggregation.DISTRIBUTION, DashboardVisualization.BAR, DashboardLimits.DEFAULT_TOP_LIMIT, List.of()),
                new DashboardComponentRequest("seat-status", "座位安排情况", "system:seatAssigned",
                        DashboardAggregation.DISTRIBUTION, DashboardVisualization.PIE, DashboardLimits.DEFAULT_TOP_LIMIT, List.of())
        );
    }

    private List<DashboardComponentRequest> normalizeComponents(List<DashboardComponentRequest> components) {
        if (components.size() > 12) {
            throw BusinessException.badRequest("看板最多配置 12 个组件");
        }
        List<DashboardComponentRequest> normalized = components.stream().map(this::normalizeComponent).toList();
        Set<String> ids = new LinkedHashSet<>();
        for (DashboardComponentRequest component : normalized) {
            if (!ids.add(component.id())) {
                throw BusinessException.badRequest("组件标识不能重复");
            }
        }
        return normalized;
    }

    private DashboardComponentRequest normalizeComponent(DashboardComponentRequest component) {
        if (component == null) {
            throw BusinessException.badRequest("组件不能为空");
        }
        String id = requireText(component.id(), "组件标识不能为空");
        String fieldKey = requireText(component.fieldKey(), "统计字段不能为空");
        if (component.aggregation() == null || component.visualization() == null) {
            throw BusinessException.badRequest("组件聚合方式和展示形式不能为空");
        }
        int limit = component.limit() == null ? DashboardLimits.DEFAULT_TOP_LIMIT : component.limit();
        if (limit < DashboardLimits.MIN_TOP_LIMIT || limit > DashboardLimits.MAX_TOP_LIMIT) {
            throw BusinessException.badRequest("组件 Top N 范围必须在 1 到 100 之间");
        }
        List<DashboardFilterRequest> filters = new ArrayList<>();
        if (component.filters().size() > 8) {
            throw BusinessException.badRequest("单个组件最多配置 8 个筛选条件");
        }
        for (DashboardFilterRequest filter : component.filters()) {
            if (filter == null || filter.operator() == null) {
                throw BusinessException.badRequest("筛选条件不能为空");
            }
            String filterKey = requireText(filter.fieldKey(), "筛选字段不能为空");
            List<String> values = filter.values().stream()
                    .map(value -> requireText(value, "筛选值不能为空"))
                    .toList();
            filters.add(new DashboardFilterRequest(filterKey, filter.operator(), values));
        }
        return new DashboardComponentRequest(
                id,
                trimToNull(component.title()),
                fieldKey,
                component.aggregation(),
                component.visualization(),
                limit,
                filters,
                component.width()
        );
    }

    private List<DashboardComponentRequest> canonicalizeComponentFieldKeys(List<DashboardComponentRequest> components,
                                                                            Map<String, FieldDefinition> catalog) {
        return components.stream()
                .map(component -> canonicalizeComponentFieldKeys(component, catalog))
                .toList();
    }

    private DashboardComponentRequest canonicalizeComponentFieldKeys(DashboardComponentRequest component,
                                                                      Map<String, FieldDefinition> catalog) {
        List<DashboardFilterRequest> filters = component.filters().stream()
                .map(filter -> new DashboardFilterRequest(
                        canonicalDashboardFieldKey(filter.fieldKey(), catalog),
                        filter.operator(),
                        filter.values()
                ))
                .toList();
        return new DashboardComponentRequest(
                component.id(),
                component.title(),
                canonicalDashboardFieldKey(component.fieldKey(), catalog),
                component.aggregation(),
                component.visualization(),
                component.limit(),
                filters,
                component.width()
        );
    }

    private boolean isEligibleRawField(String rawFieldKey) {
        if (!hasText(rawFieldKey) || TduckSubmissionFields.isMetaField(rawFieldKey)
                || rawFieldKey.startsWith("tduckCancel") || "source".equals(rawFieldKey)) {
            return false;
        }
        return true;
    }

    private String canonicalDashboardFieldKey(String fieldKey, Map<String, FieldDefinition> catalog) {
        if (fieldKey == null || !fieldKey.startsWith("tduck:") || catalog.containsKey(fieldKey)) {
            return fieldKey;
        }
        String rawFieldKey = fieldKey.substring("tduck:".length());
        if (!rawFieldKey.endsWith(TDUCK_OPTION_LABEL_SUFFIX)
                || rawFieldKey.length() <= TDUCK_OPTION_LABEL_SUFFIX.length()) {
            return fieldKey;
        }
        String baseFieldKey = tduckCatalogKey(rawFieldKey.substring(
                0,
                rawFieldKey.length() - TDUCK_OPTION_LABEL_SUFFIX.length()
        ));
        return catalog.containsKey(baseFieldKey) ? baseFieldKey : fieldKey;
    }

    private String canonicalTduckRawFieldKey(String rawFieldKey,
                                              Set<String> knownTduckFieldKeys,
                                              Set<String> snapshotFieldKeys) {
        if (rawFieldKey == null || !rawFieldKey.endsWith(TDUCK_OPTION_LABEL_SUFFIX)
                || rawFieldKey.length() <= TDUCK_OPTION_LABEL_SUFFIX.length()) {
            return rawFieldKey;
        }
        String baseFieldKey = rawFieldKey.substring(0, rawFieldKey.length() - TDUCK_OPTION_LABEL_SUFFIX.length());
        /*
         * TDuck 单选、多选会用 xxxlabel 保存选项文字，但 "label" 也可能是管理员定义的真实 key 的一部分。
         * 只有已同步/当前表单已知基础键，或同一份快照同时有基础键时，才把它认定为展示值，避免丢失真实字段。
         */
        if (knownTduckFieldKeys.contains(rawFieldKey)) {
            return rawFieldKey;
        }
        return knownTduckFieldKeys.contains(baseFieldKey) || snapshotFieldKeys.contains(baseFieldKey)
                ? baseFieldKey
                : rawFieldKey;
    }

    private boolean prefersTduckOptionLabel(String rawFieldKey, Set<String> knownTduckFieldKeys) {
        return rawFieldKey != null && !knownTduckFieldKeys.contains(rawFieldKey + TDUCK_OPTION_LABEL_SUFFIX);
    }

    private List<Object> rawSnapshotValues(Map<String, Object> snapshot,
                                           String fieldKey,
                                           boolean preferOptionLabel) {
        return flattenRawValue(preferredRawSnapshotValue(snapshot, fieldKey, preferOptionLabel));
    }

    private Object preferredRawSnapshotValue(Map<String, Object> snapshot,
                                             String fieldKey,
                                             boolean preferOptionLabel) {
        if (snapshot == null || fieldKey == null) {
            return null;
        }
        if (!preferOptionLabel) {
            return snapshot.get(fieldKey);
        }
        Object label = snapshot.get(fieldKey + "label");
        return hasRawValue(label) ? label : snapshot.get(fieldKey);
    }

    private boolean hasRawValue(Object value) {
        return !flattenRawValue(value).isEmpty();
    }

    private boolean isMultipleRawValue(Object value) {
        if (value instanceof Collection<?> || (value != null && value.getClass().isArray())) {
            return true;
        }
        if (value instanceof Map<?, ?> map) {
            Object nested = firstMapValue(map, "label", "labels", "value", "values", "text", "name");
            return nested instanceof Collection<?> || (nested != null && nested.getClass().isArray());
        }
        return false;
    }

    private List<Object> flattenRawValue(Object value) {
        return flattenRawValue(value, 0);
    }

    private List<Object> flattenRawValue(Object value, int depth) {
        if (value == null || depth > 4) {
            return List.of();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().flatMap(item -> flattenRawValue(item, depth + 1).stream()).toList();
        }
        if (value.getClass().isArray()) {
            List<Object> values = new ArrayList<>();
            int length = Array.getLength(value);
            for (int index = 0; index < length; index++) {
                values.addAll(flattenRawValue(Array.get(value, index), depth + 1));
            }
            return values;
        }
        if (value instanceof Map<?, ?> map) {
            Object nested = firstMapValue(map, "label", "labels", "value", "values", "text", "name");
            return nested == null ? List.of() : flattenRawValue(nested, depth + 1);
        }
        return textValue(value) == null ? List.of() : List.of(value);
    }

    private Object firstMapValue(Map<?, ?> values, String... keys) {
        for (String key : keys) {
            Object value = values.get(key);
            if (hasRawValue(value)) {
                return value;
            }
        }
        return null;
    }

    private List<Object> singleValue(Object value) {
        return value == null ? List.of() : List.of(value);
    }

    private List<Object> splitTags(String tags) {
        if (!hasText(tags)) {
            return List.of();
        }
        return List.of(tags.split("[,，;；]"));
    }

    private List<String> splitMultiSelectValue(String value) {
        if (!hasText(value)) {
            return List.of();
        }
        return List.of(value.split("[,，;；]")).stream()
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .toList();
    }

    private boolean isLikelyMultiSelect(String fieldKey, String label) {
        String text = (firstNonBlank(fieldKey, "") + " " + firstNonBlank(label, "")).toLowerCase(Locale.ROOT);
        return text.contains("checkbox") || text.contains("multi_select") || text.contains("multiselect")
                || text.contains("multiple") || text.contains("多选") || text.contains("复选");
    }

    private DashboardFieldDataType dataTypeFor(AttendeeField attendeeField) {
        if (attendeeField == null) {
            return null;
        }
        return switch (attendeeField) {
            case COMPANION_COUNT -> DashboardFieldDataType.NUMBER;
            case ARRIVAL_DATE, DEPARTURE_DATE -> DashboardFieldDataType.DATE;
            case ACCOMMODATION_REQUIRED, CONSENT_PHOTO, CONSENT_PRIVACY -> DashboardFieldDataType.BOOLEAN;
            default -> DashboardFieldDataType.TEXT;
        };
    }

    private boolean isSensitive(AttendeeField attendeeField, String fieldKey, String label) {
        if (attendeeField != null && EnumSet.of(
                AttendeeField.NAME,
                AttendeeField.PHONE,
                AttendeeField.IDENTITY_NO,
                AttendeeField.EMAIL,
                AttendeeField.ADDRESS,
                AttendeeField.EMERGENCY_CONTACT,
                AttendeeField.EMERGENCY_PHONE,
                AttendeeField.INVOICE_TITLE,
                AttendeeField.TAX_NO,
                AttendeeField.INVOICE_EMAIL
        ).contains(attendeeField)) {
            return true;
        }
        String text = (firstNonBlank(fieldKey, "") + " " + firstNonBlank(label, "")).toLowerCase(Locale.ROOT);
        return text.contains("phone") || text.contains("mobile") || text.contains("email")
                || text.contains("identity") || text.contains("tax") || text.contains("address")
                || text.contains("emergency") || text.contains("手机号") || text.contains("电话")
                || text.contains("邮箱") || text.contains("身份证") || text.contains("税号")
                || text.contains("地址") || text.contains("紧急") || text.equals("name") || text.contains("姓名");
    }

    private Map<String, String> attendeeTypeLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(AttendeeType.NORMAL.name(), "普通参会人");
        labels.put(AttendeeType.VIP.name(), "贵宾");
        labels.put(AttendeeType.GUEST.name(), "嘉宾");
        labels.put(AttendeeType.MEDIA.name(), "媒体");
        labels.put(AttendeeType.STAFF.name(), "工作人员");
        return labels;
    }

    private Map<String, String> reviewStatusLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(ReviewStatus.PENDING.name(), "待审核");
        labels.put(ReviewStatus.APPROVED.name(), "已通过");
        labels.put(ReviewStatus.REJECTED.name(), "已拒绝");
        labels.put(ReviewStatus.CANCELLED.name(), "已取消");
        return labels;
    }

    private Map<String, String> checkinStatusLabels() {
        return Map.of(
                CheckinStatus.CHECKED_IN.name(), "已签到",
                CheckinStatus.NOT_CHECKED_IN.name(), "未签到"
        );
    }

    private Map<String, String> booleanLabels() {
        return Map.of("true", "是", "false", "否");
    }

    private String tduckCatalogKey(String rawFieldKey) {
        return "tduck:" + rawFieldKey;
    }

    private void ensureMeetingExists(Long meetingId) {
        if (meetingId == null || !meetingRepository.existsById(meetingId)) {
            throw BusinessException.notFound("会议不存在");
        }
    }

    private String requireText(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw BusinessException.badRequest(message);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean hasText(String value) {
        return trimToNull(value) != null;
    }

    private String firstNonBlank(String first, String fallback) {
        return hasText(first) ? first.trim() : fallback;
    }

    private BigDecimal normalizeDecimal(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.scale() < 0 ? normalized.setScale(0) : normalized;
    }

    private record DashboardRow(Attendee attendee, boolean seatAssigned, boolean dinnerAssigned) {
    }

    private record FieldSeed(
            String key,
            String label,
            String category,
            DashboardFieldSource source,
            DashboardFieldDataType declaredType,
            boolean sensitive,
            String rawFieldKey,
            boolean preferOptionLabel,
            Function<DashboardRow, List<Object>> valueReader,
            Map<String, String> staticLabels,
            boolean coerceStringNumber
    ) {
    }

    private record FieldDefinition(
            DashboardFieldDescriptor descriptor,
            FieldSeed seed,
            Map<String, String> valueLabels
    ) {
    }

    private record DateRange(LocalDate start, LocalDate end) {
    }

    private final class ResolvedFilter {
        private final FieldDefinition definition;
        private final DashboardFilterOperator operator;
        private final List<String> expected;

        private ResolvedFilter(FieldDefinition definition,
                               DashboardFilterOperator operator,
                               List<String> expected) {
            this.definition = definition;
            this.operator = operator;
            this.expected = expected;
        }

        private boolean matches(DashboardRow row) {
            List<String> actual = canonicalValues(definition, row);
            return switch (operator) {
                case IS_EMPTY -> actual.isEmpty();
                case IS_NOT_EMPTY -> !actual.isEmpty();
                case EQUALS -> actual.stream().anyMatch(value -> value.equals(expected.get(0)));
                case NOT_EQUALS -> actual.stream().noneMatch(value -> value.equals(expected.get(0)));
                case IN -> actual.stream().anyMatch(expected::contains);
                case NOT_IN -> actual.stream().noneMatch(expected::contains);
                case GREATER_THAN -> actual.stream().anyMatch(value -> compare(value, expected.get(0)) > 0);
                case GREATER_THAN_OR_EQUAL -> actual.stream().anyMatch(value -> compare(value, expected.get(0)) >= 0);
                case LESS_THAN -> actual.stream().anyMatch(value -> compare(value, expected.get(0)) < 0);
                case LESS_THAN_OR_EQUAL -> actual.stream().anyMatch(value -> compare(value, expected.get(0)) <= 0);
                case BETWEEN -> actual.stream().anyMatch(value -> compare(value, expected.get(0)) >= 0
                        && compare(value, expected.get(1)) <= 0);
            };
        }

        private int compare(String left, String right) {
            if (definition.descriptor().dataType() == DashboardFieldDataType.NUMBER) {
                return new BigDecimal(left).compareTo(new BigDecimal(right));
            }
            return LocalDate.parse(left).compareTo(LocalDate.parse(right));
        }
    }

    private static final class UnknownDashboardFieldException extends RuntimeException {
        private UnknownDashboardFieldException(String message) {
            super(message);
        }
    }
}
