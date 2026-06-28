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
        if (replacingDifferentForm) {
            throw BusinessException.conflict(REBINDING_DISABLED_REASON);
        }
        TduckFormDetails form = validateFormForBinding(request.formKey());
        binding.setMeeting(meeting);
        binding.setFormKey(form.formKey());
        binding.setFormName(form.formName());
        // 报名入口统一由 MeetingOS 根据当前 TDuck 前端地址生成，避免历史手填 URL 指向错误表单或错误域名。
        binding.setRegistrationUrl(tduckClient.registrationUrlFor(form.formKey()));
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

    @Transactional
    public List<TduckFieldMapping> updateMappings(Long meetingId, UpdateFieldMappingsRequest request) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        ensureRequiredMapping(request.mappings(), AttendeeField.NAME);
        ensureRequiredMapping(request.mappings(), AttendeeField.PHONE);

        mappingRepository.deleteByMeetingId(meetingId);
        // 同一事务内会重新插入 meeting_id + attendee_field 相同的映射；先 flush 删除，避免数据库唯一键先看到旧行。
        mappingRepository.flush();
        List<TduckFieldMapping> saved = request.mappings().stream().map(item -> {
            TduckFieldMapping mapping = new TduckFieldMapping();
            mapping.setMeeting(meeting);
            mapping.setAttendeeField(item.attendeeField());
            mapping.setTduckFieldKey(item.tduckFieldKey());
            mapping.setTduckFieldLabel(item.tduckFieldLabel());
            mapping.setRequiredMapping(item.requiredMapping());
            return mapping;
        }).map(mappingRepository::save).toList();

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
            mappingByTduckKey.putIfAbsent(mapping.getTduckFieldKey(), mapping);
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
         * 自动同步只做“低风险的首轮匹配”：
         * - NAME 和 PHONE 是 Webhook 入库的最低要求，匹配不到则保持待确认，整体 mappingStatus 不是 VALID。
         * - 其他字段按中文标签、常见英文 key 做启发式匹配；匹配结果落库，未匹配字段仍返回给前端展示。
         * - 不删除 TDuck 端存在但无法识别的字段，避免把自定义业务字段误写到错误的 MeetingOS 字段。
         */
        List<TduckFieldSyncItem> preview = new ArrayList<>();
        List<FieldMappingItem> matchedMappings = new ArrayList<>();
        for (TduckFormFieldOption field : fields) {
            Optional<AttendeeField> matched = matchAttendeeField(field);
            MappingStatus status = matched.isPresent() ? MappingStatus.VALID : MappingStatus.NOT_CONFIGURED;
            boolean requiredMapping = matched
                    .map(item -> item == AttendeeField.NAME || item == AttendeeField.PHONE)
                    .orElse(false);
            matched.ifPresent(attendeeField -> matchedMappings.add(new FieldMappingItem(
                    attendeeField,
                    field.fieldKey(),
                    field.label(),
                    requiredMapping
            )));
            preview.add(new TduckFieldSyncItem(
                    field.fieldKey(),
                    field.label(),
                    field.type(),
                    field.required(),
                    matched.orElse(null),
                    requiredMapping,
                    status
            ));
        }

        boolean hasName = matchedMappings.stream().anyMatch(item -> item.attendeeField() == AttendeeField.NAME);
        boolean hasPhone = matchedMappings.stream().anyMatch(item -> item.attendeeField() == AttendeeField.PHONE);
        MappingStatus mappingStatus = hasName && hasPhone ? MappingStatus.VALID : MappingStatus.NOT_CONFIGURED;

        mappingRepository.deleteByMeetingId(meetingId);
        // 同步字段会重建映射；必须先把旧映射删除落到数据库，避免唯一索引 uk_mapping_meeting_target 冲突。
        mappingRepository.flush();
        matchedMappings.stream()
                // 同一个 MeetingOS 字段可能被多个 TDuck 字段模糊匹配到，保留最靠前的一个，避免唯一索引冲突。
                .collect(LinkedHashMap<AttendeeField, FieldMappingItem>::new,
                        (map, item) -> map.putIfAbsent(item.attendeeField(), item),
                        LinkedHashMap::putAll)
                .values()
                .stream()
                .sorted(Comparator.comparing(item -> item.attendeeField().ordinal()))
                .map(item -> toMappingEntity(meeting, item))
                .forEach(mappingRepository::save);
        binding.setMappingStatus(mappingStatus);

        return new TduckFieldSyncResponse(
                binding.getFormKey(),
                binding.getFormName(),
                fields.size(),
                matchedMappings.size(),
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
            if (!StringUtils.hasText(details.formKey())) {
                throw BusinessException.badRequest("TDuck 报名数据缺少 formKey，无法确认来源表单");
            }
            if (!formKey.equals(details.formKey())) {
                throw BusinessException.badRequest("TDuck 报名数据不属于当前绑定表单，请检查 dataId 是否填错");
            }
            if (details.formData() == null || details.formData().isEmpty()) {
                throw BusinessException.badRequest("TDuck 同步接口返回空数据");
            }
            return details;
        } catch (BusinessException ex) {
            throw ex;
        } catch (TduckClientException ex) {
            throw BusinessException.badRequest("调用 TDuck 单条报名详情失败: " + ex.getMessage());
        }
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
        if (containsAny(combined, "name", "姓名", "参会人", "联系人")) {
            return Optional.of(AttendeeField.NAME);
        }
        if (containsAny(combined, "phone", "mobile", "tel", "手机号", "手机", "电话", "联系方式")) {
            return Optional.of(AttendeeField.PHONE);
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
        if (meeting.getStatus() != MeetingStatus.REGISTRATION_OPEN) {
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
            String remoteError = invalidFormReason(details.get());
            if (remoteError != null) {
                return TduckBindingResponse.of(binding, TduckBindingValidationStatus.INVALID, remoteError, currentRegistrationUrl(binding));
            }
            return TduckBindingResponse.valid(binding, currentRegistrationUrl(binding));
        } catch (TduckClientException ex) {
            return TduckBindingResponse.of(binding, TduckBindingValidationStatus.UNKNOWN, ex.getMessage(), currentRegistrationUrl(binding));
        }
    }

    private TduckBindingResponse withRebindingLockStatus(TduckBindingResponse response, Long meetingId) {
        return response.withRebindingLock(true, REBINDING_DISABLED_REASON);
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

    private String invalidFormReason(TduckFormDetails details) {
        if (details == null || !StringUtils.hasText(details.formKey())) {
            return "TDuck 表单不存在";
        }
        if (!details.published()) {
            return "TDuck 表单未发布，暂不可填写";
        }
        if (details.itemCount() <= 0) {
            return "TDuck 表单没有可填写字段";
        }
        return null;
    }
}
