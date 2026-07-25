package com.meetingos.tduck;

import com.meetingos.attendee.Attendee;
import com.meetingos.attendee.AttendeeCodeGenerator;
import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.attendee.CheckinStatus;
import com.meetingos.attendee.ReviewStatus;
import com.meetingos.common.BusinessException;
import com.meetingos.common.ErrorCode;
import com.meetingos.common.PageRequests;
import com.meetingos.common.PageResponse;
import com.meetingos.invitation.InvitationAttributionService;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import com.meetingos.meeting.MeetingStatus;
import com.meetingos.publicapi.RegistrationInfo;
import com.meetingos.security.SecurityUtils;
import com.meetingos.settings.SystemSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Service
public class TduckService {

    private static final Logger log = LoggerFactory.getLogger(TduckService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String REBINDING_DISABLED_REASON = "当前会议已绑定 TDuck 表单，不能选择其他表单";
    private static final String TDUCK_CANCEL_REASON = "TDuck 报名删除或撤回";
    private static final int MAX_EVENT_ID_LENGTH = 120;
    private static final int MAX_TDUCK_DATA_ID_LENGTH = 80;
    private static final List<DateTimeFormatter> TDUCK_SUBMISSION_TIME_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
    );

    private final MeetingRepository meetingRepository;
    private final TduckFormBindingRepository bindingRepository;
    private final TduckFieldMappingRepository mappingRepository;
    private final TduckSyncLogRepository syncLogRepository;
    private final TduckSubmissionRepository submissionRepository;
    private final AttendeeRepository attendeeRepository;
    private final TduckFieldMapper fieldMapper;
    private final SystemSettingsService settingsService;
    private final TduckClient tduckClient;
    private final TduckSyncLogService syncLogService;
    private final TduckWebhookParser webhookParser;
    private final TduckTransactionRunner transactionRunner;
    private final AttendeeCodeGenerator attendeeCodeGenerator;
    private final InvitationAttributionService invitationAttributionService;

    public TduckService(MeetingRepository meetingRepository,
                        TduckFormBindingRepository bindingRepository,
                        TduckFieldMappingRepository mappingRepository,
                        TduckSyncLogRepository syncLogRepository,
                        TduckSubmissionRepository submissionRepository,
                        AttendeeRepository attendeeRepository,
                        TduckFieldMapper fieldMapper,
                        SystemSettingsService settingsService,
                        TduckClient tduckClient,
                        TduckSyncLogService syncLogService,
                        TduckWebhookParser webhookParser,
                        TduckTransactionRunner transactionRunner,
                        InvitationAttributionService invitationAttributionService) {
        this(
                meetingRepository,
                bindingRepository,
                mappingRepository,
                syncLogRepository,
                submissionRepository,
                attendeeRepository,
                fieldMapper,
                settingsService,
                tduckClient,
                syncLogService,
                webhookParser,
                transactionRunner,
                new AttendeeCodeGenerator(attendeeRepository),
                invitationAttributionService
        );
    }

    @Autowired
    public TduckService(MeetingRepository meetingRepository,
                        TduckFormBindingRepository bindingRepository,
                        TduckFieldMappingRepository mappingRepository,
                        TduckSyncLogRepository syncLogRepository,
                        TduckSubmissionRepository submissionRepository,
                        AttendeeRepository attendeeRepository,
                        TduckFieldMapper fieldMapper,
                        SystemSettingsService settingsService,
                        TduckClient tduckClient,
                        TduckSyncLogService syncLogService,
                        TduckWebhookParser webhookParser,
                        TduckTransactionRunner transactionRunner,
                        AttendeeCodeGenerator attendeeCodeGenerator,
                        InvitationAttributionService invitationAttributionService) {
        this.meetingRepository = meetingRepository;
        this.bindingRepository = bindingRepository;
        this.mappingRepository = mappingRepository;
        this.syncLogRepository = syncLogRepository;
        this.submissionRepository = submissionRepository;
        this.attendeeRepository = attendeeRepository;
        this.fieldMapper = fieldMapper;
        this.settingsService = settingsService;
        this.tduckClient = tduckClient;
        this.syncLogService = syncLogService;
        this.webhookParser = webhookParser;
        this.transactionRunner = transactionRunner;
        this.attendeeCodeGenerator = attendeeCodeGenerator;
        this.invitationAttributionService = invitationAttributionService;
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

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
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
    public PageResponse<TduckSyncLog> syncLogs(Long meetingId, int page, int size, SyncStatus status) {
        // 同步日志主要用于排障和联调回看，默认展示最新事件，避免真实联调后还停留在旧日志页首。
        var pageRequest = PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        if (meetingId == null) {
            return PageResponse.from(syncLogRepository.findAll(pageRequest));
        }
        if (status != null) {
            return PageResponse.from(syncLogRepository.findByMeetingIdAndStatus(meetingId, status, pageRequest));
        }
        return PageResponse.from(syncLogRepository.findByMeetingId(meetingId, pageRequest));
    }

    @Transactional(readOnly = true)
    public TduckSyncLogDetailResponse syncLogDetail(Long meetingId, Long syncLogId) {
        TduckSyncLog syncLog = syncLogRepository.findByIdAndMeetingId(syncLogId, meetingId)
                .orElseThrow(() -> BusinessException.notFound("TDuck 同步日志不存在"));
        return TduckSyncLogDetailResponse.from(syncLog);
    }

    @Transactional(readOnly = true)
    public PageResponse<TduckSubmissionConflictResponse> submissionConflicts(Long meetingId, int page, int size) {
        var pageRequest = PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        var result = PageResponse.from(submissionRepository.findByMeetingIdAndStatus(
                meetingId,
                TduckSubmissionStatus.PENDING_CONFLICT,
                pageRequest
        ));
        return new PageResponse<>(
                result.content().stream().map(TduckSubmissionConflictResponse::from).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages()
        );
    }

    @Transactional(readOnly = true)
    public TduckSubmissionConflictResponse submissionConflict(Long meetingId, Long submissionId) {
        TduckSubmission submission = submissionRepository.findById(submissionId)
                .filter(value -> value.getMeeting().getId().equals(meetingId))
                .orElseThrow(() -> BusinessException.notFound("TDuck 重复报名记录不存在"));
        return TduckSubmissionConflictResponse.from(submission);
    }

    @Transactional
    public TduckSubmissionConflictResponse resolveSubmissionConflict(Long submissionId,
                                                                      TduckSubmissionResolveRequest request) {
        // 同步与人工处理都先锁会议，再锁 dataId/参会人，避免不同 dataId 同手机号并发时产生双当前来源。
        meetingRepository.findByIdForUpdate(request.meetingId())
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        TduckSubmission submission = submissionRepository.findByIdForUpdate(submissionId)
                .filter(value -> value.getMeeting().getId().equals(request.meetingId()))
                .orElseThrow(() -> BusinessException.notFound("TDuck 重复报名记录不存在"));
        Attendee attendee = attendeeRepository.findByIdForUpdate(submission.getAttendee().getId())
                .orElseThrow(() -> BusinessException.notFound("关联参会人不存在"));

        TduckSubmissionStatus resolvedStatus = request.resolution() == TduckSubmissionResolution.KEEP_EXISTING
                ? TduckSubmissionStatus.IGNORED
                : TduckSubmissionStatus.ACTIVE;
        if (submission.getStatus() != TduckSubmissionStatus.PENDING_CONFLICT) {
            if (submission.getStatus() == resolvedStatus) {
                return TduckSubmissionConflictResponse.from(submission);
            }
            throw BusinessException.conflict("该重复报名已由其他操作处理，请刷新后查看结果");
        }
        if (!attendee.getPhone().equals(submission.getPhone())) {
            throw BusinessException.conflict("当前参会人手机号已变更，不能直接采用这条历史报名");
        }

        if (request.resolution() == TduckSubmissionResolution.ADOPT_INCOMING) {
            /*
             * 只替换报名资料与当前 dataId。参会人 ID、审核、签到、座位、住宿和晚宴关系均不变，
             * 因而不会让现场业务链路指向另一条报名记录。
             */
            List<TduckSubmission> active = submissionRepository.findByAttendeeIdAndStatusForUpdate(
                    attendee.getId(),
                    TduckSubmissionStatus.ACTIVE
            );
            for (TduckSubmission current : active) {
                if (!current.getId().equals(submission.getId())) {
                    current.setStatus(TduckSubmissionStatus.SUPERSEDED);
                }
            }
            // 先释放 (meeting_id, active_attendee_id) 唯一键，再把待处理记录提升为当前来源。
            submissionRepository.flush();
            applySubmissionToAttendee(attendee, submission);
            submission.setStatus(TduckSubmissionStatus.ACTIVE);
        } else {
            submission.setStatus(TduckSubmissionStatus.IGNORED);
        }
        submission.setResolvedAt(LocalDateTime.now());
        submission.setResolvedBy(SecurityUtils.currentUsernameOrSystem());
        submission.setResolutionNote(trimToLength(request.note(), 500));
        // 只让当前 ACTIVE dataId 保持有效归因；采用新提交和保留原提交都要重新收口历史状态。
        invitationAttributionService.synchronizeSubmissionAttributionState(request.meetingId(), attendee.getId());
        return TduckSubmissionConflictResponse.from(submission);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public String resolveRedirectUrl(String meetingCode) {
        RegistrationInfo info = registrationInfo(meetingCode);
        if (!info.open()) {
            throw BusinessException.badRequest(info.message());
        }
        return info.registrationUrl();
    }

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

    public TduckWebhookResult handleWebhook(String rawBody, String signature, String token) {
        if (!verifyWebhookCredential(signature, token, rawBody)) {
            // 未通过鉴权的数据既不解析也不进入同步日志，避免把攻击载荷当成可信业务数据持久化。
            TduckSyncLog log = failedLog(null, null, "Webhook 签名校验失败");
            throw new BusinessException(ErrorCode.WEBHOOK_SIGNATURE_INVALID, "Webhook 签名校验失败，已记录同步日志: " + log.getId());
        }
        TduckWebhookRequest request;
        try {
            request = webhookParser.parse(rawBody);
        } catch (BusinessException ex) {
            // 已通过鉴权但解析失败时只记录固定摘要，不保存无法确认结构与敏感字段范围的原始数据。
            TduckSyncLog log = failedLog(null, null, "Webhook 请求体解析失败");
            throw new VerifiedWebhookParseException(log.getId());
        }
        return runVerifiedWebhook(request, rawBody);
    }

    /**
     * 兼容内部调用和既有测试。对外 HTTP 入口必须使用原始请求体重载，以保证验签先于 JSON 解析。
     */
    public TduckWebhookResult handleWebhook(TduckWebhookRequest request, String signature, String token, String rawBody) {
        if (!verifyWebhookCredential(signature, token, rawBody)) {
            TduckSyncLog log = failedLog(null, null, "Webhook 签名校验失败");
            throw new BusinessException(ErrorCode.WEBHOOK_SIGNATURE_INVALID, "Webhook 签名校验失败，已记录同步日志: " + log.getId());
        }
        return runVerifiedWebhook(request, rawBody);
    }

    private TduckWebhookResult runVerifiedWebhook(TduckWebhookRequest request, String rawBody) {
        String eventId = normalizeEventId(request, rawBody);
        TduckWebhookResult result = runIdempotentTransaction(
                eventId,
                "重复 Webhook 已忽略",
                () -> handleVerifiedWebhook(request, eventId)
        );
        return applyInvitationAttribution(request, result, false);
    }

    private TduckWebhookResult handleVerifiedWebhook(TduckWebhookRequest request, String eventId) {
        if (eventId != null) {
            var existing = syncLogRepository.findBySuccessEventId(eventId);
            if (existing.isPresent()) {
                TduckSyncLog log = existing.get();
                return new TduckWebhookResult(
                        SyncStatus.DUPLICATE_IGNORED,
                        log.getId(),
                        attributionRetryAttendeeId(log),
                        "重复 Webhook 已忽略"
                );
            }
        }

        return syncMappedData(request, eventId);
    }

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
        return runTrustedSync(webhookRequest, "manual-sync");
    }

    public TduckWebhookResult pullSync(TduckPullSyncRequest request) {
        TduckFormBinding binding = bindingRepository.findByMeetingId(request.meetingId())
                .orElseThrow(() -> BusinessException.notFound("会议未绑定 TDuck 表单"));
        TduckFormDataDetails details = fetchRemoteData(binding.getFormKey(), request.dataId());
        TduckWebhookRequest webhookRequest = new TduckWebhookRequest(
                "pull-sync-" + binding.getFormKey() + "-" + details.dataId(),
                "PULL_SYNC",
                binding.getFormKey(),
                details.dataId(),
                details.extValue(),
                details.formData(),
                Map.of("source", "pull-sync", "dataId", details.dataId(), "data", details.formData(), "raw", details.raw())
        );
        return runTrustedSync(webhookRequest, "pull-sync");
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TduckBatchPullSyncResponse batchPullSync(TduckBatchPullSyncRequest request) {
        TduckFormBinding binding = bindingRepository.findByMeetingId(request.meetingId())
                .orElseThrow(() -> BusinessException.notFound("会议未绑定 TDuck 表单"));
        return batchPullSync(binding.getFormKey(), normalizeBatchLimit(request.limit()), ignored -> { });
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TduckBatchPullSyncResponse batchPullSync(String formKey,
                                                    int limit,
                                                    Consumer<TduckBatchPullSyncProgress> progressConsumer) {
        List<String> dataIds;
        try {
            dataIds = tduckClient.listFormDataIds(formKey, normalizeBatchLimit(limit));
        } catch (TduckClientException ex) {
            throw BusinessException.badRequest("调用 TDuck 报名列表失败: " + ex.getMessage());
        }

        int success = 0;
        int duplicateIgnored = 0;
        int failed = 0;
        List<TduckBatchPullSyncFailure> failures = new ArrayList<>();
        progressConsumer.accept(new TduckBatchPullSyncProgress(
                dataIds.size(), 0, success, duplicateIgnored, failed, List.copyOf(failures)
        ));
        int processed = 0;
        for (String dataId : dataIds) {
            try {
                TduckFormDataDetails details = fetchRemoteData(formKey, dataId);
                ensureRemoteDataBelongsToBinding(formKey, details);
                TduckWebhookRequest webhookRequest = new TduckWebhookRequest(
                        "pull-sync-" + formKey + "-" + details.dataId(),
                        "PULL_SYNC",
                        formKey,
                        details.dataId(),
                        details.extValue(),
                        details.formData(),
                        Map.of("source", "batch-pull-sync", "dataId", details.dataId(), "data", details.formData(), "raw", details.raw())
                );
                // 每条报名独立提交，既能捕获并发幂等竞争，也避免一条异常把整批已成功数据回滚。
                TduckWebhookResult result = runTrustedSync(webhookRequest, "pull-sync");
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
            } finally {
                processed++;
                progressConsumer.accept(new TduckBatchPullSyncProgress(
                        dataIds.size(), processed, success, duplicateIgnored, failed, List.copyOf(failures)
                ));
            }
        }
        String message = "已扫描 " + dataIds.size()
                + " 条，成功 " + success
                + " 条，已同步跳过 " + duplicateIgnored
                + " 条，失败 " + failed + " 条";
        return new TduckBatchPullSyncResponse(dataIds.size(), success, duplicateIgnored, failed, failures, message);
    }

    private AttendeeSyncOutcome upsertAttendee(Meeting meeting, MappedAttendeeData mapped, TduckWebhookRequest request) {
        String dataId = normalizeTduckDataId(request.dataId());
        if (dataId == null) {
            return AttendeeSyncOutcome.failed("TDuck 报名缺少 dataId，不能安全识别重复提交");
        }

        Optional<TduckSubmission> knownSubmission = submissionRepository
                .findByMeetingIdAndTduckDataIdForUpdate(meeting.getId(), dataId);
        Optional<Attendee> attendeeByPhone = attendeeRepository
                .findByMeetingIdAndPhoneForUpdate(meeting.getId(), mapped.phone());

        if (knownSubmission.isPresent()) {
            return syncKnownSubmission(knownSubmission.get(), attendeeByPhone, mapped, request);
        }

        /*
         * 兼容 V9 升级前极端情况下回填失败或历史手工修数。正常环境迁移已把现有 dataId
         * 写入 tduck_submissions；此处仍按原有 dataId 主键保护，不能把两个人误合并。
         */
        Optional<Attendee> attendeeByDataId = attendeeRepository.findByMeetingIdAndTduckDataIdForUpdate(meeting.getId(), dataId);
        if (attendeeByDataId.isPresent()) {
            Attendee attendee = attendeeByDataId.get();
            if (attendeeByPhone.isPresent() && !sameAttendee(attendee, attendeeByPhone.get())) {
                return AttendeeSyncOutcome.failed("TDuck dataId 与手机号分别属于不同参会人，请人工核对");
            }
            TduckSubmission submission = createSubmission(meeting, attendee, mapped, request, TduckSubmissionStatus.ACTIVE);
            attendee = applyMappedAttendee(attendee, mapped, request, dataId);
            updateSubmissionSnapshot(submission, mapped, request);
            return AttendeeSyncOutcome.success(attendee);
        }

        if (attendeeByPhone.isEmpty()) {
            Attendee attendee = newAttendee(meeting);
            attendee = applyMappedAttendee(attendee, mapped, request, dataId);
            createSubmission(meeting, attendee, mapped, request, TduckSubmissionStatus.ACTIVE);
            return AttendeeSyncOutcome.success(attendee);
        }

        Attendee attendee = attendeeByPhone.get();
        if (!StringUtils.hasText(attendee.getTduckDataId())) {
            attendee = applyMappedAttendee(attendee, mapped, request, dataId);
            createSubmission(meeting, attendee, mapped, request, TduckSubmissionStatus.ACTIVE);
            return AttendeeSyncOutcome.success(attendee);
        }

        TduckSubmission activeSubmission = ensureActiveSubmission(meeting, attendee, request);
        if (isOlderThanCurrent(activeSubmission, request)) {
            createSubmission(meeting, attendee, mapped, request, TduckSubmissionStatus.IGNORED);
            return AttendeeSyncOutcome.ignored(attendee, "同手机号存在较新的 TDuck 报名，已忽略历史记录");
        }

        createSubmission(meeting, attendee, mapped, request, TduckSubmissionStatus.PENDING_CONFLICT);
        return AttendeeSyncOutcome.pending(attendee, "该手机号已有有效 TDuck 报名，新提交正在等待管理员处理");
    }

    private AttendeeSyncOutcome syncKnownSubmission(TduckSubmission submission,
                                                     Optional<Attendee> attendeeByPhone,
                                                     MappedAttendeeData mapped,
                                                     TduckWebhookRequest request) {
        Attendee referencedAttendee = submission.getAttendee();
        if (attendeeByPhone.isPresent() && !sameAttendee(referencedAttendee, attendeeByPhone.get())) {
            return AttendeeSyncOutcome.failed("TDuck dataId 与手机号分别属于不同参会人，请人工核对");
        }

        if (submission.getStatus() == TduckSubmissionStatus.PENDING_CONFLICT) {
            /*
             * 同一 dataId 的 TDuck 编辑不是新的重复报名。管理员裁决前必须看到最新资料，
             * 但 submittedAt 保留首次提交时间，避免一次编辑改变新旧提交的比较依据。
             */
            updateSubmissionSnapshot(submission, mapped, request);
            // 首次入队时已与 submission 一起创建 inactive 归因；后续更新不重复触发归因写入。
            return AttendeeSyncOutcome.ignored(referencedAttendee, "该 TDuck 报名正在等待管理员处理重复冲突");
        }
        if (submission.getStatus() == TduckSubmissionStatus.IGNORED
                || submission.getStatus() == TduckSubmissionStatus.SUPERSEDED
                || submission.getStatus() == TduckSubmissionStatus.CANCELLED) {
            return AttendeeSyncOutcome.ignored(referencedAttendee, "该 TDuck 报名已按历史处理结果忽略");
        }

        // dataId 命中后仍要锁主参会人，防止 TDuck 回调以旧实体快照覆盖管理员刚更新的会务状态。
        Attendee attendee = attendeeRepository.findByIdForUpdate(referencedAttendee.getId())
                .orElseThrow(() -> BusinessException.notFound("关联参会人不存在"));
        Attendee saved = applyMappedAttendee(attendee, mapped, request, submission.getTduckDataId());
        updateSubmissionSnapshot(submission, mapped, request);
        return AttendeeSyncOutcome.success(saved);
    }

    private Attendee newAttendee(Meeting meeting) {
        Attendee attendee = new Attendee();
        attendee.setMeeting(meeting);
        attendee.setRegisteredAt(LocalDateTime.now());
        attendee.setAttendeeCode(attendeeCodeGenerator.generate(meeting));
        // TDuck 报名数据进入 MeetingOS 后默认视为有效报名；后续由管理员在参会人列表中按需拒绝。
        attendee.setReviewStatus(ReviewStatus.APPROVED);
        attendee.setReviewedAt(LocalDateTime.now());
        attendee.setReviewedBy("system");
        attendee.setCheckinStatus(CheckinStatus.NOT_CHECKED_IN);
        return attendee;
    }

    private Attendee applyMappedAttendee(Attendee attendee,
                                         MappedAttendeeData mapped,
                                         TduckWebhookRequest request,
                                         String dataId) {
        attendee.setName(mapped.name());
        attendee.setPhone(mapped.phone());
        attendee.setOrganization(mapped.organization());
        attendee.setTitle(mapped.title());
        attendee.setIdentityNo(mapped.identityNo());
        attendee.setEmail(mapped.email());
        attendee.setAttendeeType(mapped.attendeeType());
        attendee.setAccommodationRequired(mapped.accommodationRequired());
        attendee.setRemark(mapped.remark());
        attendee.setTduckDataId(dataId);
        attendee.setRawDataSnapshot(new LinkedHashMap<>(request.data() == null ? Map.of() : request.data()));
        restoreTduckCancelledAttendee(attendee);
        return attendeeRepository.save(attendee);
    }

    private Attendee applySubmissionToAttendee(Attendee attendee, TduckSubmission submission) {
        attendee.setName(submission.getName());
        attendee.setPhone(submission.getPhone());
        attendee.setOrganization(submission.getOrganization());
        attendee.setTitle(submission.getTitle());
        attendee.setIdentityNo(submission.getIdentityNo());
        attendee.setEmail(submission.getEmail());
        attendee.setAttendeeType(submission.getAttendeeType());
        attendee.setAccommodationRequired(submission.isAccommodationRequired());
        attendee.setRemark(submission.getRemark());
        attendee.setTduckDataId(submission.getTduckDataId());
        attendee.setRawDataSnapshot(submission.getRawDataSnapshot());
        restoreTduckCancelledAttendee(attendee);
        return attendeeRepository.save(attendee);
    }

    private void restoreTduckCancelledAttendee(Attendee attendee) {
        if (attendee.getReviewStatus() != ReviewStatus.CANCELLED || !TDUCK_CANCEL_REASON.equals(attendee.getReviewReason())) {
            return;
        }
        /*
         * 仅恢复 TDuck 撤回导致的取消；管理员手工取消/拒绝仍属于人工决定，不能被重复报名覆盖。
         */
        attendee.setReviewStatus(ReviewStatus.APPROVED);
        attendee.setReviewReason(null);
        attendee.setReviewRemark(null);
        attendee.setReviewedAt(LocalDateTime.now());
        attendee.setReviewedBy("tduck-webhook");
    }

    private TduckSubmission ensureActiveSubmission(Meeting meeting, Attendee attendee, TduckWebhookRequest request) {
        List<TduckSubmission> active = submissionRepository.findByAttendeeIdAndStatusForUpdate(
                attendee.getId(),
                TduckSubmissionStatus.ACTIVE
        );
        if (!active.isEmpty()) {
            return active.get(0);
        }

        String currentDataId = normalizeTduckDataId(attendee.getTduckDataId());
        if (currentDataId == null) {
            throw BusinessException.conflict("参会人 TDuck 绑定不完整，请人工核对");
        }
        TduckSubmission legacy = new TduckSubmission();
        legacy.setMeeting(meeting);
        legacy.setAttendee(attendee);
        legacy.setFormKey(firstNonBlank(request.formKey(), "LEGACY"));
        legacy.setTduckDataId(currentDataId);
        legacy.setStatus(TduckSubmissionStatus.ACTIVE);
        legacy.setSubmittedAt(attendee.getRegisteredAt() == null ? attendee.getCreatedAt() : attendee.getRegisteredAt());
        legacy.setName(attendee.getName());
        legacy.setPhone(attendee.getPhone());
        legacy.setOrganization(attendee.getOrganization());
        legacy.setTitle(attendee.getTitle());
        legacy.setIdentityNo(attendee.getIdentityNo());
        legacy.setEmail(attendee.getEmail());
        legacy.setAttendeeType(attendee.getAttendeeType());
        legacy.setAccommodationRequired(attendee.isAccommodationRequired());
        legacy.setRemark(attendee.getRemark());
        legacy.setRawDataSnapshot(attendee.getRawDataSnapshot());
        return submissionRepository.save(legacy);
    }

    private TduckSubmission createSubmission(Meeting meeting,
                                             Attendee attendee,
                                             MappedAttendeeData mapped,
                                             TduckWebhookRequest request,
                                             TduckSubmissionStatus status) {
        TduckSubmission submission = new TduckSubmission();
        submission.setMeeting(meeting);
        submission.setAttendee(attendee);
        submission.setFormKey(firstNonBlank(request.formKey(), "UNKNOWN"));
        submission.setTduckDataId(normalizeTduckDataId(request.dataId()));
        submission.setStatus(status);
        updateSubmissionSnapshot(submission, mapped, request);
        return submissionRepository.save(submission);
    }

    private void updateSubmissionSnapshot(TduckSubmission submission,
                                          MappedAttendeeData mapped,
                                          TduckWebhookRequest request) {
        submission.setSubmittedAt(firstNonNull(submission.getSubmittedAt(), submissionTime(request)));
        submission.setName(mapped.name());
        submission.setPhone(mapped.phone());
        submission.setOrganization(mapped.organization());
        submission.setTitle(mapped.title());
        submission.setIdentityNo(mapped.identityNo());
        submission.setEmail(mapped.email());
        submission.setAttendeeType(mapped.attendeeType());
        submission.setAccommodationRequired(mapped.accommodationRequired());
        submission.setRemark(mapped.remark());
        submission.setRawDataSnapshot(businessSubmissionSnapshot(request.data()));
    }

    private boolean isOlderThanCurrent(TduckSubmission activeSubmission, TduckWebhookRequest request) {
        LocalDateTime incomingTime = submissionTime(request);
        LocalDateTime currentTime = activeSubmission.getSubmittedAt();
        if (incomingTime != null && currentTime != null && !incomingTime.equals(currentTime)) {
            return incomingTime.isBefore(currentTime);
        }

        /*
         * 老版 TDuck payload 可能没有 createTime。纯数字 dataId 在 TDuck 当前实现中由自增主键产生，
         * 仅作为时间缺失时的保守兜底；无法比较时转人工队列，不根据批处理遍历顺序猜测新旧。
         */
        try {
            return Long.parseLong(request.dataId().trim()) < Long.parseLong(activeSubmission.getTduckDataId().trim());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean sameAttendee(Attendee first, Attendee second) {
        if (first == second) {
            return true;
        }
        return first.getId() != null && first.getId().equals(second.getId());
    }

    private CancellationOutcome cancelAttendeeByWebhook(Meeting meeting, TduckWebhookRequest request) {
        String dataId = normalizeTduckDataId(request.dataId());
        CancellationOutcome outcome;
        if (dataId != null) {
            Optional<TduckSubmission> submission = submissionRepository
                    .findByMeetingIdAndTduckDataIdForUpdate(meeting.getId(), dataId);
            if (submission.isPresent()) {
                outcome = cancelKnownSubmission(submission.get(), request);
                invitationAttributionService.synchronizeSubmissionAttributionState(
                        meeting.getId(),
                        outcome.attendee().getId()
                );
                return outcome;
            }
        }

        Attendee attendee = findAttendeeForCancellation(meeting, request)
                .orElseThrow(() -> BusinessException.notFound("未找到需要取消的 TDuck 参会人"));
        cancelAttendee(attendee, request);
        outcome = new CancellationOutcome(
                attendeeRepository.save(attendee),
                "已自动取消参会"
        );
        invitationAttributionService.synchronizeSubmissionAttributionState(meeting.getId(), outcome.attendee().getId());
        return outcome;
    }

    private CancellationOutcome cancelKnownSubmission(TduckSubmission submission, TduckWebhookRequest request) {
        // 撤回会更新参会人审核状态或恢复资料，必须与后台审核/签到等写操作串行。
        Attendee attendee = attendeeRepository.findByIdForUpdate(submission.getAttendee().getId())
                .orElseThrow(() -> BusinessException.notFound("关联参会人不存在"));
        if (submission.getStatus() == TduckSubmissionStatus.ACTIVE) {
            List<TduckSubmission> active = submissionRepository.findByAttendeeIdAndStatusForUpdate(
                    attendee.getId(),
                    TduckSubmissionStatus.ACTIVE
            );
            boolean hasAnotherActive = active.stream().anyMatch(item -> !item.getId().equals(submission.getId()));
            submission.setStatus(TduckSubmissionStatus.CANCELLED);
            if (!hasAnotherActive) {
                List<TduckSubmission> superseded = submissionRepository.findByAttendeeIdAndStatusForUpdate(
                        attendee.getId(),
                        TduckSubmissionStatus.SUPERSEDED
                );
                if (!superseded.isEmpty()) {
                    /*
                     * active_attendee_id 的唯一键确保同一参会人只能有一个当前来源。
                     * 先把被撤回的来源落库为 CANCELLED，再提升回退来源，避免 Hibernate
                     * 在一次 flush 中以不确定顺序更新两条记录而触发临时唯一键冲突。
                     */
                    submissionRepository.flush();
                    TduckSubmission fallback = superseded.stream()
                            .max(Comparator
                                    .comparing(TduckSubmission::getSubmittedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                                    .thenComparing(TduckSubmission::getId))
                            .orElseThrow();
                    fallback.setStatus(TduckSubmissionStatus.ACTIVE);
                    Attendee restored = applySubmissionToAttendee(attendee, fallback);
                    return new CancellationOutcome(restored, "已取消当前 TDuck 报名，已恢复上一份有效报名");
                }
                cancelAttendee(attendee, request);
                return new CancellationOutcome(attendeeRepository.save(attendee), "已自动取消参会");
            }
            return new CancellationOutcome(attendee, "已取消对应 TDuck 报名，当前参会人仍有其他有效报名");
        }

        if (submission.getStatus() != TduckSubmissionStatus.CANCELLED) {
            /*
             * 已被替代或管理员忽略的历史 dataId 也可能在 TDuck 端被撤回。
             * 必须记为 CANCELLED，否则当前来源以后撤回时会把已撤回的 SUPERSEDED
             * 记录错误恢复成当前报名。
             */
            submission.setStatus(TduckSubmissionStatus.CANCELLED);
        }
        return new CancellationOutcome(attendee, "已取消历史 TDuck 报名，不影响当前参会人");
    }

    private void cancelAttendee(Attendee attendee, TduckWebhookRequest request) {
        /*
         * TDuck 删除/撤回代表当前有效报名源头已失效，MeetingOS 不物理删除参会人，
         * 否则会丢失签到、座位、住宿和审计线索。
         */
        attendee.setReviewStatus(ReviewStatus.CANCELLED);
        attendee.setReviewReason(TDUCK_CANCEL_REASON);
        attendee.setReviewRemark("TDuck eventType=" + safeEventType(request.eventType()) + ", dataId=" + safeValue(request.dataId()));
        attendee.setReviewedAt(LocalDateTime.now());
        attendee.setReviewedBy("tduck-webhook");
        if (request.data() != null && !request.data().isEmpty()) {
            Map<String, Object> snapshot = new LinkedHashMap<>(request.data());
            snapshot.put("tduckCancelEventType", safeEventType(request.eventType()));
            attendee.setRawDataSnapshot(snapshot);
        }
    }

    private Optional<Attendee> findAttendeeForCancellation(Meeting meeting, TduckWebhookRequest request) {
        String dataId = normalizeTduckDataId(request.dataId());
        if (dataId != null) {
            Optional<Attendee> byDataId = attendeeRepository.findByMeetingIdAndTduckDataIdForUpdate(meeting.getId(), dataId);
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
            return attendeeRepository.findByMeetingIdAndPhoneForUpdate(meeting.getId(), mapped.phone())
                    .filter(attendee -> dataId == null);
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
        return syncLogRepository.findBySuccessEventId(eventId)
                .map(log -> duplicateTrustedSyncResult(request, source, log))
                .orElseGet(() -> syncMappedData(request, eventId));
    }

    private TduckWebhookResult duplicateTrustedSyncResult(TduckWebhookRequest request,
                                                          String source,
                                                          TduckSyncLog log) {
        if ("pull-sync".equals(source) && log.getStatus() == SyncStatus.DUPLICATE_IGNORED) {
            Optional<String> refreshFailure = refreshPendingSubmissionSnapshot(request, log);
            if (refreshFailure.isPresent()) {
                return new TduckWebhookResult(SyncStatus.FAILED, null, null, refreshFailure.get());
            }
        }
        return new TduckWebhookResult(
                SyncStatus.DUPLICATE_IGNORED,
                log.getId(),
                attributionRetryAttendeeId(log),
                "重复补偿同步已忽略"
        );
    }

    private Optional<String> refreshPendingSubmissionSnapshot(TduckWebhookRequest request, TduckSyncLog log) {
        String dataId = normalizeTduckDataId(request.dataId());
        if (dataId == null) {
            return Optional.of("待裁决报名快照未刷新：TDuck 报名缺少 dataId");
        }
        Long meetingId = log.getMeeting() == null ? null : log.getMeeting().getId();
        if (meetingId == null) {
            return Optional.of("待裁决报名快照未刷新：历史同步日志缺少会议关联");
        }

        try {
            /*
             * 拉取同步使用固定 dataId 幂等键。待裁决记录的填写人若在 TDuck 修改资料，
             * 仍需让管理员看到最新快照；锁序与人工裁决保持一致，避免裁决时读到旧资料。
             */
            Meeting meeting = meetingRepository.findByIdForUpdate(meetingId)
                    .orElseThrow(() -> BusinessException.notFound("会议不存在"));
            Optional<TduckSubmission> pending = submissionRepository
                    .findByMeetingIdAndTduckDataIdForUpdate(meeting.getId(), dataId);
            if (pending.isEmpty() || pending.get().getStatus() != TduckSubmissionStatus.PENDING_CONFLICT) {
                return Optional.empty();
            }

            TduckSubmission submission = pending.get();
            // 与人工裁决竞争时先锁住当前关联的参会人，但这里不更新其资料或下游会务状态。
            attendeeRepository.findByIdForUpdate(submission.getAttendee().getId())
                    .orElseThrow(() -> BusinessException.notFound("关联参会人不存在"));
            MappedAttendeeData mapped = fieldMapper.map(
                    mappingRepository.findByMeetingId(meeting.getId()),
                    request.data()
            );
            Optional<Attendee> attendeeByPhone = attendeeRepository
                    .findByMeetingIdAndPhoneForUpdate(meeting.getId(), mapped.phone());
            if (attendeeByPhone.isPresent() && !sameAttendee(submission.getAttendee(), attendeeByPhone.get())) {
                return Optional.of("待裁决报名快照未刷新：TDuck dataId 与手机号分别属于不同参会人，请人工核对");
            }
            updateSubmissionSnapshot(submission, mapped, request);
            return Optional.empty();
        } catch (BusinessException ex) {
            // 已有成功幂等日志不能被覆盖；刷新失败只反馈当前拉取结果，留待管理员修复后重试。
            return Optional.of("待裁决报名快照未刷新：" + ex.getMessage());
        }
    }

    private TduckWebhookResult runTrustedSync(TduckWebhookRequest request, String source) {
        String eventId = normalizeEventId(request, source + ":" + request.formKey() + ":" + request.dataId());
        TduckWebhookResult result = runIdempotentTransaction(
                eventId,
                "重复补偿同步已忽略",
                () -> syncTrusted(request, source)
        );
        /*
         * 主动拉取代表 TDuck 当前快照，不是历史事件重放。即使参会人同步已幂等命中，
         * 也要重试幂等归因，补偿上一次在主事务提交后进程退出或归因仓储瞬时失败。
         */
        return applyInvitationAttribution(request, result, true);
    }

    private TduckWebhookResult applyInvitationAttribution(TduckWebhookRequest request,
                                                           TduckWebhookResult result,
                                                           boolean retryDuplicate) {
        if (result == null
                || (result.status() != SyncStatus.SUCCESS
                    && !(retryDuplicate && result.status() == SyncStatus.DUPLICATE_IGNORED))
                || result.attendeeId() == null
                || request == null
                || !StringUtils.hasText(request.formKey())
                || !StringUtils.hasText(request.dataId())) {
            return result;
        }

        try {
            Optional<Long> meetingId = bindingRepository.findMeetingIdByFormKey(request.formKey());
            if (meetingId.isEmpty()) {
                return result;
            }
            if (isCancellationEvent(request.eventType())) {
                invitationAttributionService.markInactive(meetingId.get(), request.dataId());
            } else {
                invitationAttributionService.attributeFirstSubmission(
                        meetingId.get(),
                        result.attendeeId(),
                        request.formKey(),
                        request.dataId(),
                        request.extValue()
                );
            }
        } catch (RuntimeException ex) {
            /*
             * 渠道统计是报名后的附加能力。归因仓储、签名解析或历史脏数据异常都不能把
             * 已成功提交的参会人回滚，也不能让 TDuck 把成功 Webhook 误判为失败后反复重试。
             */
            log.warn(
                    "TDuck 报名已同步，但邀请函归因处理失败: formKey={}, dataId={}, eventType={}",
                    request.formKey(),
                    safeValue(request.dataId()),
                    safeEventType(request.eventType()),
                    ex
            );
        }
        return result;
    }

    private TduckWebhookResult runIdempotentTransaction(String eventId,
                                                         String duplicateMessage,
                                                         Supplier<TduckWebhookResult> action) {
        try {
            return transactionRunner.requiresNew(action);
        } catch (DataIntegrityViolationException ex) {
            /*
             * 两个相同事件可能同时通过“尚无成功日志”的检查。唯一键会让后提交者完整回滚；
             * 必须等该事务退出后再查询胜出日志，不能在 rollback-only 事务中尝试恢复。
             */
            Optional<TduckSyncLog> existing = transactionRunner.requiresNew(
                    () -> syncLogRepository.findBySuccessEventId(eventId)
            );
            if (existing.isPresent()) {
                TduckSyncLog log = existing.get();
                return new TduckWebhookResult(
                        SyncStatus.DUPLICATE_IGNORED,
                        log.getId(),
                        attributionRetryAttendeeId(log),
                        duplicateMessage
                );
            }
            // dataId、手机号等其他唯一键冲突不能伪装成幂等命中，应交由统一异常处理暴露。
            throw ex;
        }
    }

    private TduckWebhookResult syncMappedData(TduckWebhookRequest request, String eventId) {
        Meeting meeting = null;
        try {
            TduckFormBinding binding = bindingRepository.findByFormKey(request.formKey())
                    .orElseThrow(() -> BusinessException.badRequest("formKey 未绑定会议"));
            meeting = meetingRepository.findByIdForUpdate(binding.getMeeting().getId())
                    .orElseThrow(() -> BusinessException.notFound("会议不存在"));
            if (isCancellationEvent(request.eventType())) {
                CancellationOutcome outcome = cancelAttendeeByWebhook(meeting, request);
                TduckSyncLog log = successLog(request, meeting, eventId, outcome.attendee(), outcome.message());
                return new TduckWebhookResult(SyncStatus.SUCCESS, log.getId(), outcome.attendee().getId(), outcome.message());
            }
            List<TduckFieldMapping> mappings = mappingRepository.findByMeetingId(meeting.getId());
            MappedAttendeeData mapped = fieldMapper.map(mappings, request.data());
            AttendeeSyncOutcome outcome = upsertAttendee(meeting, mapped, request);
            if (outcome.status() == SyncStatus.FAILED) {
                TduckSyncLog log = failedLog(request, meeting, outcome.message());
                return new TduckWebhookResult(SyncStatus.FAILED, log.getId(), null, outcome.message());
            }
            if (outcome.status() == SyncStatus.DUPLICATE_IGNORED) {
                if (outcome.pendingConflict()) {
                    invitationAttributionService.preparePendingSubmissionAttribution(
                            meeting.getId(),
                            outcome.attendee().getId(),
                            request.formKey(),
                            request.dataId(),
                            request.extValue()
                    );
                }
                TduckSyncLog log = syncLogService.ignored(request, meeting, eventId, outcome.attendee(), outcome.message());
                // 仅待处理的新 dataId 预建 inactive 归因；已裁决的历史重复仍不重复计入转化。
                return new TduckWebhookResult(SyncStatus.DUPLICATE_IGNORED, log.getId(), null, outcome.message());
            }
            TduckSyncLog log = successLog(request, meeting, eventId, outcome.attendee());
            return new TduckWebhookResult(SyncStatus.SUCCESS, log.getId(), outcome.attendee().getId(), "同步成功");
        } catch (BusinessException ex) {
            TduckSyncLog log = failedLog(request, meeting, ex.getMessage());
            return new TduckWebhookResult(SyncStatus.FAILED, log.getId(), null, ex.getMessage());
        } catch (DataIntegrityViolationException ex) {
            throw ex;
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
        // 与异步任务保持一致：0/空值表示按 TDuck 分页读取至末页，而不是截断在 1,000 条。
        return limit == null || limit <= 0 ? 0 : limit;
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
        if (StringUtils.hasText(token) && StringUtils.hasText(webhookSecret)
                && constantTimeEquals(token, webhookSecret)) {
            return true;
        }
        return verifySignature(signature, rawBody, webhookSecret);
    }

    private boolean verifySignature(String signature, String rawBody, String webhookSecret) {
        if (!StringUtils.hasText(signature) || !StringUtils.hasText(rawBody) || !StringUtils.hasText(webhookSecret)) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            String expected = HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
            String normalizedSignature = signature.trim();
            if (normalizedSignature.regionMatches(true, 0, "sha256=", 0, "sha256=".length())) {
                normalizedSignature = normalizedSignature.substring("sha256=".length());
            }
            return constantTimeEquals(
                    expected.toLowerCase(Locale.ROOT),
                    normalizedSignature.toLowerCase(Locale.ROOT)
            );
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean constantTimeEquals(String first, String second) {
        return MessageDigest.isEqual(
                first.getBytes(StandardCharsets.UTF_8),
                second.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String normalizeEventId(TduckWebhookRequest request, String rawBody) {
        if (request.eventId() != null && !request.eventId().isBlank()) {
            String eventId = request.eventId().trim();
            if (eventId.length() > MAX_EVENT_ID_LENGTH) {
                throw BusinessException.badRequest("Webhook eventId 长度不能超过 " + MAX_EVENT_ID_LENGTH + " 个字符");
            }
            return eventId;
        }
        // TDuck 社区版 Webhook 字段可能随版本略有差异；缺少事件 ID 时使用 payload hash 兜底，保证幂等性。
        return DigestUtils.md5DigestAsHex(rawBody.getBytes(StandardCharsets.UTF_8));
    }

    private String normalizeTduckDataId(String dataId) {
        if (!StringUtils.hasText(dataId)) {
            return null;
        }
        String normalized = dataId.trim();
        if (normalized.length() > MAX_TDUCK_DATA_ID_LENGTH) {
            throw BusinessException.badRequest("TDuck dataId 长度不能超过 " + MAX_TDUCK_DATA_ID_LENGTH + " 个字符");
        }
        return normalized;
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

    private Map<String, Object> businessSubmissionSnapshot(Map<String, Object> data) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (data == null || data.isEmpty()) {
            return snapshot;
        }
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!TduckSubmissionFields.isMetaField(entry.getKey())) {
                snapshot.put(entry.getKey(), entry.getValue());
            }
        }
        return snapshot;
    }

    private LocalDateTime submissionTime(TduckWebhookRequest request) {
        if (request == null) {
            return null;
        }
        LocalDateTime mappedDataTime = submissionTime(request.data());
        if (mappedDataTime != null) {
            return mappedDataTime;
        }
        /*
         * Webhook 解析会从 request.data 中排除 createTime、completeTime 等 TDuck 元字段，
         * 以免它们进入参会人资料快照。冲突判定仍需要这类时间，因此只在已验签的 raw
         * 载荷中做只读提取，且不把它回写到业务字段。
         */
        return submissionTimeFromRawPayload(request.raw(), 0);
    }

    private LocalDateTime submissionTimeFromRawPayload(Map<String, Object> payload, int depth) {
        if (payload == null || payload.isEmpty() || depth > 2) {
            return null;
        }
        LocalDateTime directTime = submissionTime(payload);
        if (directTime != null) {
            return directTime;
        }
        for (String key : List.of("data", "originalData", "formData", "raw")) {
            Object nested = payload.get(key);
            if (!(nested instanceof Map<?, ?> nestedMap)) {
                continue;
            }
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : nestedMap.entrySet()) {
                if (entry.getKey() instanceof String nestedKey) {
                    normalized.put(nestedKey, entry.getValue());
                }
            }
            LocalDateTime nestedTime = submissionTimeFromRawPayload(normalized, depth + 1);
            if (nestedTime != null) {
                return nestedTime;
            }
        }
        return null;
    }

    private LocalDateTime submissionTime(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        for (String key : List.of("createTime", "completeTime", "submittedAt", "submitTime", "createdAt")) {
            LocalDateTime parsed = parseSubmissionTime(data.get(key));
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private LocalDateTime parseSubmissionTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return epochToLocalDateTime(number.longValue());
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return epochToLocalDateTime(Long.parseLong(text));
        } catch (NumberFormatException ignored) {
            // 继续尝试 TDuck 常见的文本日期格式。
        }
        try {
            return LocalDateTime.ofInstant(Instant.parse(text), ZoneId.systemDefault());
        } catch (DateTimeParseException ignored) {
            // 文本不含时区时按 TDuck 表单保存的本地时间处理。
        }
        try {
            return OffsetDateTime.parse(text).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // 继续按无时区格式解析。
        }
        try {
            return LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            // 继续尝试兼容格式。
        }
        for (DateTimeFormatter formatter : TDUCK_SUBMISSION_TIME_FORMATS) {
            try {
                return LocalDateTime.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
                // 尝试下一种格式。
            }
        }
        return null;
    }

    private LocalDateTime epochToLocalDateTime(long timestamp) {
        Instant instant = Math.abs(timestamp) >= 10_000_000_000L
                ? Instant.ofEpochMilli(timestamp)
                : Instant.ofEpochSecond(timestamp);
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    private LocalDateTime firstNonNull(LocalDateTime first, LocalDateTime second) {
        return first == null ? second : first;
    }

    private String trimToLength(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private Long attributionRetryAttendeeId(TduckSyncLog log) {
        /*
         * SUCCESS 的重复拉取允许补偿一次可能丢失的归因写入。历史忽略和待处理冲突
         * 同样会占用 success_event_id 以保持幂等，但它们不是当前有效报名，绝不能
         * 因下一次全量拉取而重新激活邀请函归因。
         */
        return log.getStatus() == SyncStatus.SUCCESS ? log.getAttendeeId() : null;
    }

    private record AttendeeSyncOutcome(SyncStatus status,
                                       Attendee attendee,
                                       String message,
                                       boolean pendingConflict) {
        private static AttendeeSyncOutcome success(Attendee attendee) {
            return new AttendeeSyncOutcome(SyncStatus.SUCCESS, attendee, "同步成功", false);
        }

        private static AttendeeSyncOutcome ignored(Attendee attendee, String message) {
            return new AttendeeSyncOutcome(SyncStatus.DUPLICATE_IGNORED, attendee, message, false);
        }

        private static AttendeeSyncOutcome pending(Attendee attendee, String message) {
            return new AttendeeSyncOutcome(SyncStatus.DUPLICATE_IGNORED, attendee, message, true);
        }

        private static AttendeeSyncOutcome failed(String message) {
            return new AttendeeSyncOutcome(SyncStatus.FAILED, null, message, false);
        }
    }

    private record CancellationOutcome(Attendee attendee, String message) {
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
