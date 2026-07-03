package com.meetingos.tduck;

import com.meetingos.attendee.Attendee;
import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.attendee.CheckinStatus;
import com.meetingos.attendee.ReviewStatus;
import com.meetingos.common.BusinessException;
import com.meetingos.common.ErrorCode;
import com.meetingos.common.PageRequests;
import com.meetingos.common.PageResponse;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import com.meetingos.meeting.MeetingStatus;
import com.meetingos.publicapi.RegistrationInfo;
import com.meetingos.settings.SystemSettingsService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class TduckService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String REBINDING_DISABLED_REASON = "当前会议已绑定 TDuck 表单，不能选择其他表单";
    private static final String TDUCK_CANCEL_REASON = "TDuck 报名删除或撤回";

    private final MeetingRepository meetingRepository;
    private final TduckFormBindingRepository bindingRepository;
    private final TduckFieldMappingRepository mappingRepository;
    private final TduckSyncLogRepository syncLogRepository;
    private final AttendeeRepository attendeeRepository;
    private final TduckFieldMapper fieldMapper;
    private final SystemSettingsService settingsService;
    private final TduckClient tduckClient;
    private final TduckSyncLogService syncLogService;

    public TduckService(MeetingRepository meetingRepository,
                        TduckFormBindingRepository bindingRepository,
                        TduckFieldMappingRepository mappingRepository,
                        TduckSyncLogRepository syncLogRepository,
                        AttendeeRepository attendeeRepository,
                        TduckFieldMapper fieldMapper,
                        SystemSettingsService settingsService,
                        TduckClient tduckClient,
                        TduckSyncLogService syncLogService) {
        this.meetingRepository = meetingRepository;
        this.bindingRepository = bindingRepository;
        this.mappingRepository = mappingRepository;
        this.syncLogRepository = syncLogRepository;
        this.attendeeRepository = attendeeRepository;
        this.fieldMapper = fieldMapper;
        this.settingsService = settingsService;
        this.tduckClient = tduckClient;
        this.syncLogService = syncLogService;
    }

    @Transactional
    public TduckBindingResponse bindForm(BindFormRequest request) {
        Meeting meeting = meetingRepository.findById(request.meetingId())
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        Optional<TduckFormBinding> existingBinding = bindingRepository.findByMeetingId(request.meetingId());
        TduckFormBinding binding = existingBinding.orElseGet(TduckFormBinding::new);
        String previousFormKey = binding.getFormKey();
        boolean replacingDifferentForm = existingBinding.isPresent() && !request.formKey().equals(previousFormKey);
        if (replacingDifferentForm && !canReplaceExistingBinding(binding)) {
            throw BusinessException.conflict(REBINDING_DISABLED_REASON);
        }
        TduckFormDetails form = validateFormForBinding(request.formKey());
        ensureFormKeyNotBoundToAnotherMeeting(form.formKey(), meeting.getId());
        binding.setMeeting(meeting);
        binding.setFormKey(form.formKey());
        binding.setFormName(form.formName());
        // 报名入口统一由 MeetingOS 根据当前 TDuck 前端地址生成，避免历史手填 URL 指向错误表单或错误域名。
        binding.setRegistrationUrl(tduckClient.registrationUrlFor(form.formKey()));
        if (existingBinding.isEmpty() || replacingDifferentForm) {
            binding.setRegistrationEnabled(true);
        }
        binding.setBindingStatus(request.bindingStatus() == null ? TduckBindingStatus.BOUND : request.bindingStatus());
        try {
            /*
             * webhookStatus 不能再信任前端传参：历史问题正是数据库显示 CONFIGURED，
             * 但 TDuck 端没有真实配置，导致报名提交后没有回调 MeetingOS。
             * 保存绑定前先调用 TDuck 配置接口，成功后才把本地状态标为 CONFIGURED。
             */
            tduckClient.configureWebhook(form.formKey());
            binding.setWebhookStatus(WebhookStatus.CONFIGURED);
        } catch (TduckClientException ex) {
            throw BusinessException.badRequest("TDuck Webhook 配置失败：" + ex.getMessage());
        }
        if (binding.getMappingStatus() == null) {
            binding.setMappingStatus(MappingStatus.NOT_CONFIGURED);
        }
        TduckBindingResponse response = TduckBindingResponse.valid(bindingRepository.save(binding), currentRegistrationUrl(binding));
        return withRebindingLockStatus(response, meeting.getId());
    }

    @Transactional
    public TduckBindingResponse updateRegistrationEnabled(Long meetingId, TduckRegistrationStatusRequest request) {
        boolean enabled = Boolean.TRUE.equals(request.enabled());
        meetingRepository.findById(meetingId)
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        TduckFormBinding binding = bindingRepository.findByMeetingId(meetingId)
                .orElseThrow(() -> BusinessException.notFound("会议未绑定 TDuck 表单"));
        if (!StringUtils.hasText(binding.getFormKey())) {
            throw BusinessException.badRequest("TDuck 表单绑定不完整，请管理员重新选择表单");
        }
        try {
            /*
             * 这个开关同时控制 MeetingOS /r/{meetingCode} 和 TDuck /tduck/s/{formKey}。
             * 先调用 TDuck 发布/停止接口，成功后再落库，避免本地显示关闭但原始 TDuck 地址仍可提交。
             */
            tduckClient.setFormPublished(binding.getFormKey(), enabled);
        } catch (TduckClientException ex) {
            throw BusinessException.badRequest("同步 TDuck 报名入口失败：" + ex.getMessage());
        }
        binding.setRegistrationEnabled(enabled);
        binding.setRegistrationUrl(currentRegistrationUrl(binding));
        return withRebindingLockStatus(validateExistingBinding(bindingRepository.save(binding)), meetingId);
    }

    @Transactional(readOnly = true)
    public TduckBindingResponse binding(Long meetingId) {
        TduckFormBinding binding = bindingRepository.findByMeetingId(meetingId)
                .orElseThrow(() -> BusinessException.notFound("会议未绑定 TDuck 表单"));
        return withRebindingLockStatus(validateExistingBinding(binding), meetingId);
    }

    @Transactional(readOnly = true)
    public List<TduckFormOption> formOptions(String keyword, int page, int size) {
        try {
            return tduckClient.listForms(keyword, page, size);
        } catch (TduckClientException ex) {
            throw BusinessException.badRequest(ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public TduckBrowserSessionResponse browserSession(Long meetingId) {
        meetingRepository.findById(meetingId)
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        try {
            return tduckClient.browserSession();
        } catch (TduckClientException ex) {
            throw BusinessException.badRequest(ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public TduckFormOption createBlankForm(CreateTduckFormRequest request) {
        Meeting meeting = meetingRepository.findById(request.meetingId())
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        String formName = StringUtils.hasText(request.formName())
                ? request.formName().trim()
                : meeting.getName() + " 报名表";
        String description = StringUtils.hasText(request.description())
                ? request.description().trim()
                : "MeetingOS 创建";
        try {
            /*
             * 只负责在 TDuck 中创建一张可编辑的空表单，不在这里自动绑定会议。
             * TDuck 新表单默认未发布，强行写入 MeetingOS 绑定会让公开报名入口指向不可填写表单；
             * 管理员编辑并发布后，再从列表中保存绑定，流程更明确。
             */
            return tduckClient.createForm(formName, description);
        } catch (TduckClientException ex) {
            throw BusinessException.badRequest(ex.getMessage());
        }
    }

    @Transactional
    public List<TduckFieldMapping> updateMappings(Long meetingId, UpdateFieldMappingsRequest request) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        List<FieldMappingItem> mappings = normalizeFieldMappings(request.mappings());
        ensureRequiredMapping(mappings, AttendeeField.NAME);
        ensureRequiredMapping(mappings, AttendeeField.PHONE);

        mappingRepository.deleteByMeetingId(meetingId);
        // 同一事务内会重新插入 meeting_id + attendee_field 相同的映射；先 flush 删除，避免数据库唯一键先看到旧行。
        mappingRepository.flush();
        List<TduckFieldMapping> saved = mappings.stream()
                .map(item -> toMappingEntity(meeting, item))
                .map(mappingRepository::save)
                .toList();

        bindingRepository.findByMeetingId(meetingId).ifPresent(binding -> binding.setMappingStatus(MappingStatus.VALID));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<TduckFieldMappingResponse> mappings(Long meetingId) {
        List<TduckFieldMapping> savedMappings = mappingRepository.findByMeetingId(meetingId);
        TduckFormBinding binding = bindingRepository.findByMeetingId(meetingId).orElse(null);
        if (binding == null || !StringUtils.hasText(binding.getFormKey())) {
            return savedMappings.stream()
                    .map(TduckFieldMappingResponse::saved)
                    .toList();
        }

        /*
         * 参会人列表需要和 TDuck 填写页字段保持一致，因此这里以 TDuck 表单字段为主：
         * - TDuck 返回顺序就是填写页顺序，不能再按 MeetingOS 标准字段枚举排序；
         * - 已保存映射只用于补充 attendeeField/requiredMapping，未映射的自定义字段仍返回给前端展示；
         * - TDuck 临时不可访问时退回已保存映射，保证管理页不因为外部服务波动整体不可用。
         */
        Map<String, TduckFieldMapping> mappingByTduckKey = new LinkedHashMap<>();
        for (TduckFieldMapping mapping : savedMappings) {
            if (StringUtils.hasText(mapping.getTduckFieldKey())) {
                mappingByTduckKey.putIfAbsent(mapping.getTduckFieldKey(), mapping);
            }
        }
        try {
            List<TduckFormFieldOption> fields = tduckClient.formFields(binding.getFormKey());
            if (fields.isEmpty()) {
                return savedMappings.stream()
                        .map(TduckFieldMappingResponse::saved)
                        .toList();
            }
            return fields.stream()
                    .filter(field -> StringUtils.hasText(field.fieldKey()))
                    .map(field -> TduckFieldMappingResponse.fromField(field, mappingByTduckKey.get(field.fieldKey())))
                    .toList();
        } catch (TduckClientException ex) {
            return savedMappings.stream()
                    .map(TduckFieldMappingResponse::saved)
                    .toList();
        }
    }

    @Transactional
    public TduckFieldSyncResponse syncFields(Long meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        TduckFormBinding binding = bindingRepository.findByMeetingId(meetingId)
                .orElseThrow(() -> BusinessException.notFound("会议未绑定 TDuck 表单"));
        List<TduckFormFieldOption> fields;
        try {
            fields = tduckClient.formFields(binding.getFormKey());
        } catch (TduckClientException ex) {
            throw BusinessException.badRequest(ex.getMessage());
        }
        if (fields.isEmpty()) {
            throw BusinessException.badRequest("TDuck 表单没有可同步字段");
        }

        /*
         * 字段同步既要刷新 TDuck 最新字段清单，也必须保留管理员已经确认过的映射。
         * 之前这里每次都删除旧映射并按自动识别结果重建，导致“保存映射”后再次点同步字段时，
         * 手工调整过的映射状态被覆盖。现在的策略是：
         * - TDuck 字段 key 仍存在时，优先使用已保存映射；
         * - 只有新字段才走启发式自动识别；
         * - 自动识别结果如果和已保存目标字段冲突，则保持未配置，避免制造重复目标字段。
         */
        List<TduckFieldMapping> savedMappings = mappingRepository.findByMeetingId(meetingId);
        Map<String, TduckFieldMapping> savedMappingByTduckKey = new LinkedHashMap<>();
        for (TduckFieldMapping mapping : savedMappings) {
            if (StringUtils.hasText(mapping.getTduckFieldKey())) {
                savedMappingByTduckKey.putIfAbsent(mapping.getTduckFieldKey(), mapping);
            }
        }
        List<String> currentFieldKeys = fields.stream()
                .map(TduckFormFieldOption::fieldKey)
                .filter(StringUtils::hasText)
                .toList();
        List<AttendeeField> reservedSavedAttendeeFields = savedMappingByTduckKey.values()
                .stream()
                .filter(mapping -> currentFieldKeys.contains(mapping.getTduckFieldKey()))
                .map(TduckFieldMapping::getAttendeeField)
                .filter(java.util.Objects::nonNull)
                .toList();
        List<TduckFieldSyncItem> preview = new ArrayList<>();
        List<FieldMappingItem> desiredMappings = new ArrayList<>();
        Map<AttendeeField, FieldMappingItem> desiredMappingByAttendeeField = new LinkedHashMap<>();
        for (TduckFormFieldOption field : fields) {
            TduckFieldMapping savedMapping = savedMappingByTduckKey.get(field.fieldKey());
            AttendeeField attendeeField = null;
            boolean requiredMapping = false;
            MappingStatus status = MappingStatus.NOT_CONFIGURED;
            if (savedMapping != null) {
                attendeeField = savedMapping.getAttendeeField();
                /*
                 * attendeeField 为空表示管理员明确清空了这个 TDuck 字段。同步时必须保留该“忽略”选择，
                 * 不能再按字段名自动识别，否则“保存映射”后再次同步会把已移除字段重新映射回来。
                 */
                requiredMapping = attendeeField != null && savedMapping.isRequiredMapping();
                status = attendeeField == null ? MappingStatus.NOT_CONFIGURED : MappingStatus.VALID;
                FieldMappingItem retained = new FieldMappingItem(
                        attendeeField,
                        field.fieldKey(),
                        firstNonBlank(field.label(), savedMapping.getTduckFieldLabel()),
                        requiredMapping
                );
                desiredMappings.add(retained);
                if (attendeeField != null) {
                    desiredMappingByAttendeeField.putIfAbsent(attendeeField, retained);
                }
            } else {
                Optional<AttendeeField> matched = matchAttendeeField(field);
                if (matched.isPresent()
                        && !reservedSavedAttendeeFields.contains(matched.get())
                        && !desiredMappingByAttendeeField.containsKey(matched.get())) {
                    attendeeField = matched.get();
                    requiredMapping = attendeeField == AttendeeField.NAME || attendeeField == AttendeeField.PHONE;
                    status = MappingStatus.VALID;
                    FieldMappingItem inferred = new FieldMappingItem(
                            attendeeField,
                            field.fieldKey(),
                            field.label(),
                            requiredMapping
                    );
                    desiredMappings.add(inferred);
                    desiredMappingByAttendeeField.put(attendeeField, inferred);
                }
            }
            preview.add(new TduckFieldSyncItem(
                    field.fieldKey(),
                    field.label(),
                    field.type(),
                    field.required(),
                    attendeeField,
                    requiredMapping,
                    status
            ));
        }

        boolean hasName = desiredMappingByAttendeeField.containsKey(AttendeeField.NAME);
        boolean hasPhone = desiredMappingByAttendeeField.containsKey(AttendeeField.PHONE);
        MappingStatus mappingStatus = hasName && hasPhone ? MappingStatus.VALID : MappingStatus.NOT_CONFIGURED;

        mappingRepository.deleteByMeetingId(meetingId);
        // 同步字段会按“保留后的目标状态”重建映射；先 flush 删除，避免唯一索引 uk_mapping_meeting_target 冲突。
        mappingRepository.flush();
        desiredMappings.stream()
                .sorted(Comparator
                        .comparing((FieldMappingItem item) -> item.attendeeField() == null ? Integer.MAX_VALUE : item.attendeeField().ordinal())
                        .thenComparing(FieldMappingItem::tduckFieldKey))
                .map(item -> toMappingEntity(meeting, item))
                .forEach(mappingRepository::save);
        binding.setMappingStatus(mappingStatus);

        return new TduckFieldSyncResponse(
                binding.getFormKey(),
                binding.getFormName(),
                fields.size(),
                desiredMappingByAttendeeField.size(),
                mappingStatus,
                preview
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<TduckSyncLog> syncLogs(Long meetingId, int page, int size) {
        // 同步日志主要用于排障和联调回看，默认展示最新事件，避免真实联调后还停留在旧日志页首。
        var pageRequest = PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        if (meetingId == null) {
            return PageResponse.from(syncLogRepository.findAll(pageRequest));
        }
        return PageResponse.from(syncLogRepository.findByMeetingId(meetingId, pageRequest));
    }

    @Transactional(readOnly = true)
    public String resolveRedirectUrl(String meetingCode) {
        RegistrationInfo info = registrationInfo(meetingCode);
        if (!info.open()) {
            throw BusinessException.badRequest(info.message());
        }
        return info.registrationUrl();
    }

    @Transactional(readOnly = true)
    public RegistrationInfo registrationInfo(String meetingCode) {
        Meeting meeting = meetingRepository.findByCode(meetingCode)
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        TduckFormBinding binding = bindingRepository.findByMeetingId(meeting.getId()).orElse(null);
        String message = registrationClosedReason(meeting, binding);
        if (message == null) {
            /*
             * 公开报名入口不能只看 MeetingOS 会议状态。本地绑定可能指向已删除、未发布或字段为空的 TDuck 表单；
             * 这种情况下应停在 MeetingOS 的明确提示页，而不是把用户跳到 TDuck 的“暂时无法填写”空状态。
             */
            message = registrationUnavailableReason(binding);
        }
        boolean open = message == null;
        return new RegistrationInfo(
                meeting.getId(),
                meeting.getCode(),
                meeting.getName(),
                meeting.getStatus(),
                open,
                open ? "报名开放中" : message,
                binding == null ? null : binding.getFormName(),
                binding == null ? null : binding.getFormKey(),
                open ? currentRegistrationUrl(binding) : null
        );
    }

    @Transactional
    public TduckWebhookResult handleWebhook(TduckWebhookRequest request, String signature, String token, String rawBody) {
        if (!verifyWebhookCredential(signature, token, rawBody)) {
            TduckSyncLog log = failedLog(request, null, "Webhook 签名校验失败");
            throw new BusinessException(ErrorCode.WEBHOOK_SIGNATURE_INVALID, "Webhook 签名校验失败，已记录同步日志: " + log.getId());
        }

        String eventId = normalizeEventId(request, rawBody);
        if (eventId != null) {
            var existing = syncLogRepository.findTopByEventIdAndStatusOrderByIdDesc(eventId, SyncStatus.SUCCESS);
            if (existing.isPresent()) {
                TduckSyncLog log = existing.get();
                return new TduckWebhookResult(SyncStatus.DUPLICATE_IGNORED, log.getId(), log.getAttendeeId(), "重复 Webhook 已忽略");
            }
        }

        Meeting meeting = null;
        try {
            TduckFormBinding binding = bindingRepository.findByFormKey(request.formKey())
                    .orElseThrow(() -> BusinessException.badRequest("formKey 未绑定会议"));
            meeting = binding.getMeeting();
            if (isCancellationEvent(request.eventType())) {
                Attendee attendee = cancelAttendeeByWebhook(meeting, request);
                TduckSyncLog log = successLog(request, meeting, eventId, attendee, "已根据 TDuck 删除/撤回事件自动取消参会");
                return new TduckWebhookResult(SyncStatus.SUCCESS, log.getId(), attendee.getId(), "已自动取消参会");
            }
            List<TduckFieldMapping> mappings = mappingRepository.findByMeetingId(meeting.getId());
            MappedAttendeeData mapped = fieldMapper.map(mappings, request.data());
            Attendee attendee = upsertAttendee(meeting, mapped, request);
            TduckSyncLog log = successLog(request, meeting, eventId, attendee);
            return new TduckWebhookResult(SyncStatus.SUCCESS, log.getId(), attendee.getId(), "同步成功");
        } catch (BusinessException ex) {
            TduckSyncLog log = failedLog(request, meeting, ex.getMessage());
            return new TduckWebhookResult(SyncStatus.FAILED, log.getId(), null, ex.getMessage());
        } catch (RuntimeException ex) {
            TduckSyncLog log = failedLog(request, meeting, "数据格式异常: " + ex.getMessage());
            return new TduckWebhookResult(SyncStatus.FAILED, log.getId(), null, log.getMessage());
        }
    }

    @Transactional
    public TduckWebhookResult manualSync(TduckManualSyncRequest request) {
        TduckFormBinding binding = bindingRepository.findByMeetingId(request.meetingId())
                .orElseThrow(() -> BusinessException.notFound("会议未绑定 TDuck 表单"));
        String eventType = request.eventType() == null || request.eventType().isBlank() ? "MANUAL_SYNC" : request.eventType();
        Map<String, Object> data = request.data() == null ? Map.of() : request.data();
        TduckWebhookRequest webhookRequest = new TduckWebhookRequest(
                request.eventId() == null || request.eventId().isBlank()
                        ? "manual-sync-" + binding.getFormKey() + "-" + eventType + "-" + request.dataId()
                        : request.eventId(),
                eventType,
                binding.getFormKey(),
                request.dataId(),
                data,
                Map.of("source", "manual-sync", "dataId", request.dataId(), "data", data)
        );
        return syncTrusted(webhookRequest, "manual-sync");
    }

    @Transactional
    public TduckWebhookResult pullSync(TduckPullSyncRequest request) {
        TduckFormBinding binding = bindingRepository.findByMeetingId(request.meetingId())
                .orElseThrow(() -> BusinessException.notFound("会议未绑定 TDuck 表单"));
        TduckFormDataDetails details = fetchRemoteData(binding.getFormKey(), request.dataId());
        TduckWebhookRequest webhookRequest = new TduckWebhookRequest(
                "pull-sync-" + binding.getFormKey() + "-" + details.dataId(),
                "PULL_SYNC",
                binding.getFormKey(),
                details.dataId(),
                details.formData(),
                Map.of("source", "pull-sync", "dataId", details.dataId(), "data", details.formData(), "raw", details.raw())
        );
        return syncTrusted(webhookRequest, "pull-sync");
    }

    @Transactional
    public TduckBatchPullSyncResponse batchPullSync(TduckBatchPullSyncRequest request) {
        TduckFormBinding binding = bindingRepository.findByMeetingId(request.meetingId())
                .orElseThrow(() -> BusinessException.notFound("会议未绑定 TDuck 表单"));
        int limit = normalizeBatchLimit(request.limit());
        List<String> dataIds;
        try {
            dataIds = tduckClient.listFormDataIds(binding.getFormKey(), limit);
        } catch (TduckClientException ex) {
            throw BusinessException.badRequest("调用 TDuck 报名列表失败: " + ex.getMessage());
        }

        int success = 0;
        int duplicateIgnored = 0;
        int failed = 0;
        List<TduckBatchPullSyncFailure> failures = new ArrayList<>();
        for (String dataId : dataIds) {
            try {
                TduckFormDataDetails details = fetchRemoteData(binding.getFormKey(), dataId);
                ensureRemoteDataBelongsToBinding(binding.getFormKey(), details);
                TduckWebhookRequest webhookRequest = new TduckWebhookRequest(
                        "pull-sync-" + binding.getFormKey() + "-" + details.dataId(),
                        "PULL_SYNC",
                        binding.getFormKey(),
                        details.dataId(),
                        details.formData(),
                        Map.of("source", "batch-pull-sync", "dataId", details.dataId(), "data", details.formData(), "raw", details.raw())
                );
                TduckWebhookResult result = syncTrusted(webhookRequest, "pull-sync");
                if (result.status() == SyncStatus.SUCCESS) {
                    success++;
                } else if (result.status() == SyncStatus.DUPLICATE_IGNORED) {
                    duplicateIgnored++;
                } else {
                    failed++;
                    addBatchFailure(failures, details.dataId(), result.message());
                }
            } catch (RuntimeException ex) {
                /*
                 * 一键同步是补偿工具，必须让单条脏数据或映射问题暴露出来但不能中断整批。
                 * 具体失败仍会进入同步日志；响应里只保留少量样例，避免前端弹窗过大。
                 */
                failed++;
                addBatchFailure(failures, dataId, ex.getMessage());
            }
        }
        String message = "已扫描 " + dataIds.size()
                + " 条，成功 " + success
                + " 条，已同步跳过 " + duplicateIgnored
                + " 条，失败 " + failed + " 条";
        return new TduckBatchPullSyncResponse(dataIds.size(), success, duplicateIgnored, failed, failures, message);
    }

    private Attendee upsertAttendee(Meeting meeting, MappedAttendeeData mapped, TduckWebhookRequest request) {
        // 同一会议手机号唯一：TDuck 重推或用户修改报名时更新原记录，而不是生成第二条业务主数据。
        Attendee attendee = attendeeRepository.findByMeetingAndPhone(meeting, mapped.phone())
                .orElseGet(() -> {
                    Attendee created = new Attendee();
                    created.setMeeting(meeting);
                    created.setRegisteredAt(LocalDateTime.now());
                    created.setAttendeeCode(meeting.getCode() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
                    // TDuck 报名数据进入 MeetingOS 后默认视为有效报名；后续由管理员在参会人列表中按需拒绝。
                    created.setReviewStatus(ReviewStatus.APPROVED);
                    created.setReviewedAt(LocalDateTime.now());
                    created.setReviewedBy("system");
                    created.setCheckinStatus(CheckinStatus.NOT_CHECKED_IN);
                    return created;
                });
        attendee.setName(mapped.name());
        attendee.setPhone(mapped.phone());
        attendee.setOrganization(mapped.organization());
        attendee.setTitle(mapped.title());
        attendee.setIdentityNo(mapped.identityNo());
        attendee.setEmail(mapped.email());
        attendee.setAttendeeType(mapped.attendeeType());
        attendee.setAccommodationRequired(mapped.accommodationRequired());
        attendee.setRemark(mapped.remark());
        attendee.setTduckDataId(request.dataId());
        attendee.setRawDataSnapshot(new LinkedHashMap<>(request.data() == null ? Map.of() : request.data()));
        if (attendee.getReviewStatus() == ReviewStatus.CANCELLED && TDUCK_CANCEL_REASON.equals(attendee.getReviewReason())) {
            /*
             * 若用户在 TDuck 端撤回后重新提交，后续 add/update 代表报名源头重新有效。
             * 这里只恢复由 TDuck 删除事件自动取消的记录，不覆盖管理员手工取消或拒绝的判断。
             */
            attendee.setReviewStatus(ReviewStatus.APPROVED);
            attendee.setReviewReason(null);
            attendee.setReviewRemark(null);
            attendee.setReviewedAt(LocalDateTime.now());
            attendee.setReviewedBy("tduck-webhook");
        }
        return attendeeRepository.save(attendee);
    }

    private Attendee cancelAttendeeByWebhook(Meeting meeting, TduckWebhookRequest request) {
        Attendee attendee = findAttendeeForCancellation(meeting, request)
                .orElseThrow(() -> BusinessException.notFound("未找到需要取消的 TDuck 参会人"));

        /*
         * TDuck 删除/撤回代表报名源头已失效，MeetingOS 不能物理删除参会人，否则会丢失排障和审计线索。
         * 这里使用既有审核终态 CANCELLED，公开查询、签到、自动排座等依赖“审核通过”的流程会自然拦截。
         */
        attendee.setReviewStatus(ReviewStatus.CANCELLED);
        attendee.setReviewReason(TDUCK_CANCEL_REASON);
        attendee.setReviewRemark("TDuck eventType=" + safeEventType(request.eventType()) + ", dataId=" + safeValue(request.dataId()));
        attendee.setReviewedAt(LocalDateTime.now());
        attendee.setReviewedBy("tduck-webhook");
        attendee.setTduckDataId(firstNonBlank(attendee.getTduckDataId(), request.dataId()));
        if (request.data() != null && !request.data().isEmpty()) {
            Map<String, Object> snapshot = new LinkedHashMap<>(request.data());
            snapshot.put("tduckCancelEventType", safeEventType(request.eventType()));
            attendee.setRawDataSnapshot(snapshot);
        }
        return attendeeRepository.save(attendee);
    }

    private Optional<Attendee> findAttendeeForCancellation(Meeting meeting, TduckWebhookRequest request) {
        if (StringUtils.hasText(request.dataId())) {
            Optional<Attendee> byDataId = attendeeRepository.findByMeetingIdAndTduckDataId(meeting.getId(), request.dataId().trim());
            if (byDataId.isPresent()) {
                return byDataId;
            }
        }
        /*
         * 少数 TDuck 版本删除事件仍会带完整 originalData。此时允许按字段映射解析手机号兜底，
         * 但删除事件不能强依赖映射，因为常见 payload 只有 dataId/formKey。
         */
        if (request.data() != null && !request.data().isEmpty()) {
            List<TduckFieldMapping> mappings = mappingRepository.findByMeetingId(meeting.getId());
            MappedAttendeeData mapped = fieldMapper.map(mappings, request.data());
            return attendeeRepository.findByMeetingAndPhone(meeting, mapped.phone())
                    .filter(attendee -> !StringUtils.hasText(request.dataId()));
        }
        return Optional.empty();
    }

    private boolean isCancellationEvent(String eventType) {
        if (!StringUtils.hasText(eventType)) {
            return false;
        }
        String normalized = eventType.trim().toLowerCase().replace('-', '_');
        return normalized.equals("form_data_delete")
                || normalized.equals("form_data_deleted")
                || normalized.equals("form_data_remove")
                || normalized.equals("form_data_removed")
                || normalized.equals("form_data_revoke")
                || normalized.equals("form_data_revoked")
                || normalized.equals("form_data_withdraw")
                || normalized.equals("form_data_withdrawn")
                || normalized.equals("form_data_cancel")
                || normalized.equals("form_data_cancelled")
                || normalized.equals("form_data_canceled")
                || normalized.equals("delete")
                || normalized.equals("deleted")
                || normalized.equals("remove")
                || normalized.equals("removed")
                || normalized.equals("revoke")
                || normalized.equals("revoked")
                || normalized.equals("withdraw")
                || normalized.equals("withdrawn")
                || normalized.equals("cancel")
                || normalized.equals("cancelled")
                || normalized.equals("canceled");
    }

    private TduckWebhookResult syncTrusted(TduckWebhookRequest request, String source) {
        String eventId = normalizeEventId(request, source + ":" + request.formKey() + ":" + request.dataId());
        /*
         * 只有历史成功日志才视为幂等命中。失败日志要允许再次补偿，否则管理员修好字段映射或
         * Webhook 配置后，使用同一个 dataId 仍会被“重复补偿同步”挡住，数据永远进不来。
         */
        return syncLogRepository.findTopByEventIdAndStatusOrderByIdDesc(eventId, SyncStatus.SUCCESS)
                .map(log -> new TduckWebhookResult(SyncStatus.DUPLICATE_IGNORED, log.getId(), log.getAttendeeId(), "重复补偿同步已忽略"))
                .orElseGet(() -> syncMappedData(request, eventId));
    }

    private TduckWebhookResult syncMappedData(TduckWebhookRequest request, String eventId) {
        Meeting meeting = null;
        try {
            TduckFormBinding binding = bindingRepository.findByFormKey(request.formKey())
                    .orElseThrow(() -> BusinessException.badRequest("formKey 未绑定会议"));
            meeting = binding.getMeeting();
            if (isCancellationEvent(request.eventType())) {
                Attendee attendee = cancelAttendeeByWebhook(meeting, request);
                TduckSyncLog log = successLog(request, meeting, eventId, attendee, "已根据 TDuck 删除/撤回事件自动取消参会");
                return new TduckWebhookResult(SyncStatus.SUCCESS, log.getId(), attendee.getId(), "已自动取消参会");
            }
            List<TduckFieldMapping> mappings = mappingRepository.findByMeetingId(meeting.getId());
            MappedAttendeeData mapped = fieldMapper.map(mappings, request.data());
            Attendee attendee = upsertAttendee(meeting, mapped, request);
            TduckSyncLog log = successLog(request, meeting, eventId, attendee);
            return new TduckWebhookResult(SyncStatus.SUCCESS, log.getId(), attendee.getId(), "同步成功");
        } catch (BusinessException ex) {
            TduckSyncLog log = failedLog(request, meeting, ex.getMessage());
            return new TduckWebhookResult(SyncStatus.FAILED, log.getId(), null, ex.getMessage());
        } catch (RuntimeException ex) {
            TduckSyncLog log = failedLog(request, meeting, "数据格式异常: " + ex.getMessage());
            return new TduckWebhookResult(SyncStatus.FAILED, log.getId(), null, log.getMessage());
        }
    }

    private TduckFormDataDetails fetchRemoteData(String formKey, String dataId) {
        try {
            TduckFormDataDetails details = tduckClient.formDataDetails(dataId);
            ensureRemoteDataBelongsToBinding(formKey, details);
            return details;
        } catch (BusinessException ex) {
            throw ex;
        } catch (TduckClientException ex) {
            throw BusinessException.badRequest("调用 TDuck 单条报名详情失败: " + ex.getMessage());
        }
    }

    private void ensureRemoteDataBelongsToBinding(String formKey, TduckFormDataDetails details) {
        if (!StringUtils.hasText(details.formKey())) {
            throw BusinessException.badRequest("TDuck 报名数据缺少 formKey，无法确认来源表单");
        }
        if (!formKey.equals(details.formKey())) {
            throw BusinessException.badRequest("TDuck 报名数据不属于当前绑定表单，请检查 dataId 是否填错");
        }
        if (details.formData() == null || details.formData().isEmpty()) {
            throw BusinessException.badRequest("TDuck 同步接口返回空数据");
        }
    }

    private int normalizeBatchLimit(Integer limit) {
        if (limit == null) {
            return 1_000;
        }
        return Math.min(Math.max(limit, 1), 1_000);
    }

    private void addBatchFailure(List<TduckBatchPullSyncFailure> failures, String dataId, String message) {
        if (failures.size() >= 10) {
            return;
        }
        failures.add(new TduckBatchPullSyncFailure(
                safeValue(dataId),
                firstNonBlank(message, "同步失败")
        ));
    }

    private TduckSyncLog successLog(TduckWebhookRequest request, Meeting meeting, String eventId, Attendee attendee) {
        return successLog(request, meeting, eventId, attendee, "同步成功");
    }

    private TduckSyncLog successLog(TduckWebhookRequest request, Meeting meeting, String eventId, Attendee attendee, String message) {
        return syncLogService.success(request, meeting, eventId, attendee, message);
    }

    private TduckSyncLog failedLog(TduckWebhookRequest request, Meeting meeting, String message) {
        return syncLogService.failed(request, meeting, message);
    }

    private boolean verifyWebhookCredential(String signature, String token, String rawBody) {
        // MVP 兼容两种部署方式：
        // 1. TDuck 只支持静态 token 时，使用 query/header token。
        // 2. 接入网关可计算 HMAC 时，使用 X-TDuck-Signature。
        String webhookSecret = settingsService.webhookToken();
        if (token != null && !token.isBlank() && token.equals(webhookSecret)) {
            return true;
        }
        return verifySignature(signature, rawBody, webhookSecret);
    }

    private boolean verifySignature(String signature, String rawBody, String webhookSecret) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            String expected = HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
            return expected.equalsIgnoreCase(signature.replace("sha256=", ""));
        } catch (Exception ex) {
            return false;
        }
    }

    private String normalizeEventId(TduckWebhookRequest request, String rawBody) {
        if (request.eventId() != null && !request.eventId().isBlank()) {
            return request.eventId();
        }
        // TDuck 社区版 Webhook 字段可能随版本略有差异；缺少事件 ID 时使用 payload hash 兜底，保证幂等性。
        return DigestUtils.md5DigestAsHex(rawBody.getBytes(StandardCharsets.UTF_8));
    }

    private void ensureRequiredMapping(List<FieldMappingItem> mappings, AttendeeField field) {
        boolean ok = mappings.stream().anyMatch(item -> item.attendeeField() == field && item.requiredMapping());
        if (!ok) {
            throw BusinessException.badRequest("必须配置必填字段映射: " + field);
        }
    }

    private List<FieldMappingItem> normalizeFieldMappings(List<FieldMappingItem> mappings) {
        Map<AttendeeField, FieldMappingItem> mappingByAttendeeField = new LinkedHashMap<>();
        Map<String, FieldMappingItem> mappingByTduckFieldKey = new LinkedHashMap<>();
        List<FieldMappingItem> normalized = new ArrayList<>();
        for (FieldMappingItem item : mappings) {
            FieldMappingItem deduplicated = mappingByTduckFieldKey.putIfAbsent(item.tduckFieldKey(), item);
            if (deduplicated != null) {
                throw BusinessException.badRequest("TDuck 字段重复，请检查字段映射: " + item.tduckFieldKey());
            }
            if (item.attendeeField() == null) {
                // attendeeField 为空表示明确不映射；requiredMapping 对这类行没有意义，统一落库为 false。
                normalized.add(new FieldMappingItem(null, item.tduckFieldKey(), item.tduckFieldLabel(), false));
                continue;
            }
            FieldMappingItem existing = mappingByAttendeeField.putIfAbsent(item.attendeeField(), item);
            if (existing != null) {
                /*
                 * 数据库唯一键按 meeting_id + attendee_field 限制一对一映射。这里提前拦截重复目标字段，
                 * 避免把唯一键异常包装成 500，也避免保存时静默覆盖导致 Webhook 入库字段不可预测。
                 */
                throw BusinessException.badRequest("字段映射重复，请确保每个 MeetingOS 字段只绑定一个 TDuck 字段: " + item.attendeeField());
            }
            normalized.add(item);
        }
        return normalized;
    }

    private TduckFieldMapping toMappingEntity(Meeting meeting, FieldMappingItem item) {
        TduckFieldMapping mapping = new TduckFieldMapping();
        mapping.setMeeting(meeting);
        mapping.setAttendeeField(item.attendeeField());
        mapping.setTduckFieldKey(item.tduckFieldKey());
        mapping.setTduckFieldLabel(item.tduckFieldLabel());
        mapping.setRequiredMapping(item.requiredMapping());
        return mapping;
    }

    private Optional<AttendeeField> matchAttendeeField(TduckFormFieldOption field) {
        String key = normalizeFieldText(field.fieldKey());
        String label = normalizeFieldText(field.label());
        String combined = key + " " + label;
        if (containsAny(combined, "emergencycontact", "紧急联系人", "应急联系人")) {
            return Optional.of(AttendeeField.EMERGENCY_CONTACT);
        }
        if (containsAny(combined, "emergencyphone", "emergencymobile", "紧急联系电话", "紧急电话", "应急电话", "应急手机号")) {
            return Optional.of(AttendeeField.EMERGENCY_PHONE);
        }
        if (containsAny(combined, "invoiceemail", "billingemail", "发票邮箱", "收票邮箱")) {
            return Optional.of(AttendeeField.INVOICE_EMAIL);
        }
        if (containsAny(combined, "taxno", "taxnumber", "taxid", "纳税人识别号", "税号")) {
            return Optional.of(AttendeeField.TAX_NO);
        }
        if (containsAny(combined, "invoicetitle", "billingtitle", "发票抬头", "开票抬头")) {
            return Optional.of(AttendeeField.INVOICE_TITLE);
        }
        if (containsAny(combined, "name", "姓名", "参会人", "联系人")) {
            return Optional.of(AttendeeField.NAME);
        }
        if (containsAny(combined, "phone", "mobile", "tel", "手机号", "手机", "电话", "联系方式")) {
            return Optional.of(AttendeeField.PHONE);
        }
        if (containsAny(combined, "organizationtype", "companytype", "unittype", "单位类型", "公司类型", "机构类型")) {
            return Optional.of(AttendeeField.ORGANIZATION_TYPE);
        }
        if (containsAny(combined, "organization", "company", "corp", "单位", "公司", "机构")) {
            return Optional.of(AttendeeField.ORGANIZATION);
        }
        if (containsAny(combined, "title", "position", "job", "职务", "职位", "岗位")) {
            return Optional.of(AttendeeField.TITLE);
        }
        if (containsAny(combined, "identity", "idcard", "idnumber", "证件", "身份证")) {
            return Optional.of(AttendeeField.IDENTITY_NO);
        }
        if (containsAny(combined, "email", "mail", "邮箱")) {
            return Optional.of(AttendeeField.EMAIL);
        }
        if (containsAny(combined, "attendeetype", "guesttype", "参会类型", "嘉宾类型", "人员类型")) {
            return Optional.of(AttendeeField.ATTENDEE_TYPE);
        }
        if (containsAny(combined, "gender", "sex", "性别")) {
            return Optional.of(AttendeeField.GENDER);
        }
        if (containsAny(combined, "countryregion", "country", "region", "国家地区", "国家", "地区")) {
            return Optional.of(AttendeeField.COUNTRY_REGION);
        }
        if (containsAny(combined, "province", "state", "省份", "省市", "省")) {
            return Optional.of(AttendeeField.PROVINCE);
        }
        if (containsAny(combined, "city", "城市", "市")) {
            return Optional.of(AttendeeField.CITY);
        }
        if (containsAny(combined, "industry", "sector", "行业", "产业")) {
            return Optional.of(AttendeeField.INDUSTRY);
        }
        if (containsAny(combined, "department", "dept", "部门", "科室")) {
            return Optional.of(AttendeeField.DEPARTMENT);
        }
        if (containsAny(combined, "address", "addr", "通讯地址", "联系地址", "地址")) {
            return Optional.of(AttendeeField.ADDRESS);
        }
        if (containsAny(combined, "dietary", "meal", "food", "allergy", "halal", "vegetarian", "餐饮", "用餐", "忌口", "清真", "素食", "过敏")) {
            return Optional.of(AttendeeField.DIETARY_REQUIREMENT);
        }
        if (containsAny(combined, "departureflight", "departuretrain", "departureno", "leaveno", "returnflight", "returntrain", "离开航班", "离开车次", "返程航班", "返程车次")) {
            return Optional.of(AttendeeField.DEPARTURE_FLIGHT_TRAIN);
        }
        if (containsAny(combined, "departuretransport", "leavetransport", "returntransport", "departuretraffic", "返程交通", "离开交通", "离店交通")) {
            return Optional.of(AttendeeField.DEPARTURE_TRANSPORT);
        }
        if (containsAny(combined, "departuredate", "departuretime", "leavedate", "leavetime", "returndate", "returntime", "离开时间", "离开日期", "返程时间", "返程日期")) {
            return Optional.of(AttendeeField.DEPARTURE_DATE);
        }
        if (containsAny(combined, "arrivalflight", "arrivaltrain", "arriveno", "arrivetrip", "抵达航班", "到达航班", "抵达车次", "到达车次", "来程航班", "来程车次")) {
            return Optional.of(AttendeeField.ARRIVAL_FLIGHT_TRAIN);
        }
        if (containsAny(combined, "arrivaltransport", "arrivetransport", "arrivaltraffic", "arrivetraffic", "抵达交通", "到达交通", "来程交通", "交通方式")) {
            return Optional.of(AttendeeField.ARRIVAL_TRANSPORT);
        }
        if (containsAny(combined, "arrivaldate", "arrivaltime", "arrivedate", "抵达时间", "到达时间", "抵达日期", "到达日期", "来程时间")) {
            return Optional.of(AttendeeField.ARRIVAL_DATE);
        }
        if (containsAny(combined, "companioncount", "guestcount", "accompanycount", "随行人数", "陪同人数", "同伴人数")) {
            return Optional.of(AttendeeField.COMPANION_COUNT);
        }
        if (containsAny(combined, "paymentstatus", "paystatus", "paidstatus", "缴费状态", "支付状态", "付款状态")) {
            return Optional.of(AttendeeField.PAYMENT_STATUS);
        }
        if (containsAny(combined, "signupsource", "registrationsource", "source", "channel", "报名来源", "报名渠道", "来源渠道", "渠道")) {
            return Optional.of(AttendeeField.SIGNUP_SOURCE);
        }
        if (containsAny(combined, "inviter", "referrer", "inviteby", "邀请人", "推荐人")) {
            return Optional.of(AttendeeField.INVITER);
        }
        if (containsAny(combined, "photoconsent", "portraitconsent", "imageconsent", "肖像授权", "拍照授权", "照片授权", "影像授权")) {
            return Optional.of(AttendeeField.CONSENT_PHOTO);
        }
        if (containsAny(combined, "privacyconsent", "privacyagree", "agreement", "隐私同意", "隐私协议", "用户协议", "信息授权")) {
            return Optional.of(AttendeeField.CONSENT_PRIVACY);
        }
        /*
         * TDuck 报名表里的“房号/住宿备注”通常是报名需求或原始扩展信息，
         * 不能自动写入 MeetingOS 的住宿主数据。房间号只能由住宿管理导入或手动分配产生。
         */
        if (containsAny(combined, "needroom", "accommodation", "hotel", "是否住宿", "住宿", "入住")) {
            return Optional.of(AttendeeField.ACCOMMODATION_REQUIRED);
        }
        if (containsAny(combined, "remark", "note", "备注", "说明")) {
            return Optional.of(AttendeeField.REMARK);
        }
        return Optional.empty();
    }

    private String normalizeFieldText(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase()
                .replaceAll("[_\\-\\s]+", "")
                .trim();
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(normalizeFieldText(candidate))) {
                return true;
            }
        }
        return false;
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private String safeEventType(String eventType) {
        return StringUtils.hasText(eventType) ? eventType.trim() : "UNKNOWN";
    }

    private String safeValue(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
    }

    private String registrationClosedReason(Meeting meeting, TduckFormBinding binding) {
        if (binding == null) {
            return "会议未绑定报名表";
        }
        if (!StringUtils.hasText(binding.getFormKey())) {
            return "TDuck 表单绑定不完整，请管理员重新选择表单";
        }
        if (!binding.isRegistrationEnabled()) {
            return "报名入口已关闭";
        }
        if (!allowsPublicRegistrationEntry(meeting.getStatus())) {
            return "会议当前不在报名开放状态";
        }
        LocalDateTime now = LocalDateTime.now();
        if (meeting.getRegistrationStartTime() != null && now.isBefore(meeting.getRegistrationStartTime())) {
            return "报名尚未开始";
        }
        if (meeting.getRegistrationEndTime() != null && now.isAfter(meeting.getRegistrationEndTime())) {
            return "报名已截止";
        }
        return null;
    }

    private boolean allowsPublicRegistrationEntry(MeetingStatus status) {
        /*
         * 会议进入“进行中”后，现场仍可能需要扫码补报名、查看报名回执或处理迟到人员。
         * 因此报名入口不再只绑定“报名中”主状态；时间窗口和 TDuck 表单可用性仍会继续拦截。
         */
        return status == MeetingStatus.REGISTRATION_OPEN || status == MeetingStatus.IN_PROGRESS;
    }

    private TduckFormDetails validateFormForBinding(String formKey) {
        try {
            TduckFormDetails details = tduckClient.formDetails(formKey)
                    .orElseThrow(() -> BusinessException.badRequest("TDuck 表单不存在"));
            String error = invalidFormReason(details);
            if (error != null) {
                throw BusinessException.badRequest(error);
            }
            return details;
        } catch (BusinessException ex) {
            throw ex;
        } catch (TduckClientException ex) {
            throw BusinessException.badRequest(ex.getMessage());
        }
    }

    private TduckBindingResponse validateExistingBinding(TduckFormBinding binding) {
        String localError = localBindingInvalidReason(binding);
        if (localError != null) {
            return TduckBindingResponse.of(binding, TduckBindingValidationStatus.INVALID, localError, currentRegistrationUrl(binding));
        }
        try {
            var details = tduckClient.formDetails(binding.getFormKey());
            if (details.isEmpty()) {
                return TduckBindingResponse.of(binding, TduckBindingValidationStatus.INVALID, "TDuck 表单不存在", currentRegistrationUrl(binding));
            }
            TduckFormDetails formDetails = details.get();
            String remoteError = invalidBoundFormReason(formDetails, binding.isRegistrationEnabled());
            if (remoteError != null) {
                return TduckBindingResponse.of(binding, TduckBindingValidationStatus.INVALID, remoteError, currentRegistrationUrl(binding), formDetails.published());
            }
            if (!binding.isRegistrationEnabled()) {
                String message = formDetails.published()
                        ? "报名入口已关闭，但 TDuck 表单仍处于发布状态，请重新关闭入口同步"
                        : "报名入口已关闭，TDuck 表单已停止发布";
                return TduckBindingResponse.of(binding, TduckBindingValidationStatus.VALID, message, currentRegistrationUrl(binding), formDetails.published());
            }
            return TduckBindingResponse.of(binding, TduckBindingValidationStatus.VALID, "TDuck 表单可访问且已发布", currentRegistrationUrl(binding), formDetails.published());
        } catch (TduckClientException ex) {
            return TduckBindingResponse.of(binding, TduckBindingValidationStatus.UNKNOWN, ex.getMessage(), currentRegistrationUrl(binding));
        }
    }

    private TduckBindingResponse withRebindingLockStatus(TduckBindingResponse response, Long meetingId) {
        if (response.validationStatus() == TduckBindingValidationStatus.INVALID) {
            return response.withRebindingLock(false, "当前 TDuck 绑定已失效，请重新选择已发布表单");
        }
        return response.withRebindingLock(true, REBINDING_DISABLED_REASON);
    }

    private boolean canReplaceExistingBinding(TduckFormBinding binding) {
        if (binding == null) {
            return true;
        }
        return validateExistingBinding(binding).validationStatus() == TduckBindingValidationStatus.INVALID;
    }

    private void ensureFormKeyNotBoundToAnotherMeeting(String formKey, Long meetingId) {
        bindingRepository.findByFormKey(formKey).ifPresent(existing -> {
            Long boundMeetingId = existing.getMeeting() == null ? null : existing.getMeeting().getId();
            if (boundMeetingId != null && !boundMeetingId.equals(meetingId)) {
                /*
                 * formKey 在数据库层有唯一约束。提前返回业务错误，避免用户在重新绑定时只看到
                 * “系统异常”，也避免同一个 TDuck 表单的 Webhook 同时归属多个会议造成报名串会。
                 */
                throw BusinessException.conflict("该 TDuck 表单已绑定其他会议，请选择未绑定表单");
            }
        });
    }

    private String localBindingInvalidReason(TduckFormBinding binding) {
        if (!StringUtils.hasText(binding.getFormKey())) {
            return "TDuck formKey 为空，请重新选择表单";
        }
        return null;
    }

    private String currentRegistrationUrl(TduckFormBinding binding) {
        /*
         * 旧版本把完整报名地址固化在绑定表里，一旦本地端口或线上域名变化，
         * 历史绑定就会继续返回旧地址。公开入口只需要 formKey，因此对外统一按当前系统设置实时拼接。
         */
        return tduckClient.registrationUrlFor(binding.getFormKey());
    }

    private String registrationUnavailableReason(TduckFormBinding binding) {
        TduckBindingResponse response = validateExistingBinding(binding);
        if (response.validationStatus() == TduckBindingValidationStatus.VALID) {
            return null;
        }
        if (response.validationStatus() == TduckBindingValidationStatus.UNKNOWN) {
            return "报名表暂时无法校验，请稍后重试或联系会议管理员";
        }
        return "报名表不可用：" + response.validationMessage();
    }

    private String invalidFormReason(TduckFormDetails details) {
        return invalidBoundFormReason(details, true);
    }

    private String invalidBoundFormReason(TduckFormDetails details, boolean requirePublished) {
        if (details == null || !StringUtils.hasText(details.formKey())) {
            return "TDuck 表单不存在";
        }
        if (requirePublished && !details.published()) {
            return "TDuck 表单未发布，暂不可填写";
        }
        if (details.itemCount() <= 0) {
            return "TDuck 表单没有可填写字段";
        }
        return null;
    }
}
