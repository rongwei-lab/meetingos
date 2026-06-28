package com.meetingos.attendee;

import com.meetingos.common.BusinessException;
import com.meetingos.common.CsvFiles;
import com.meetingos.common.ExportTable;
import com.meetingos.common.ImportFailure;
import com.meetingos.common.ImportResult;
import com.meetingos.common.PageRequests;
import com.meetingos.common.PageResponse;
import com.meetingos.common.SensitiveDataMasker;
import com.meetingos.common.TableFileReader;
import com.meetingos.common.TableRow;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import com.meetingos.permission.MeetingAccessService;
import com.meetingos.security.SecurityUtils;
import com.meetingos.settings.SystemSettingsService;
import com.meetingos.tduck.TduckSubmissionFields;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AttendeeService {

    private final AttendeeRepository attendeeRepository;
    private final MeetingRepository meetingRepository;
    private final MeetingAccessService meetingAccessService;
    private final SystemSettingsService systemSettingsService;

    public AttendeeService(AttendeeRepository attendeeRepository,
                           MeetingRepository meetingRepository,
                           MeetingAccessService meetingAccessService,
                           SystemSettingsService systemSettingsService) {
        this.attendeeRepository = attendeeRepository;
        this.meetingRepository = meetingRepository;
        this.meetingAccessService = meetingAccessService;
        this.systemSettingsService = systemSettingsService;
    }

    @Transactional
    public Attendee create(AttendeeRequest request) {
        Meeting meeting = meetingRepository.findById(request.meetingId())
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        meetingAccessService.requireManage(meeting.getId());
        String normalizedPhone = normalizePhone(request.phone());
        attendeeRepository.findByMeetingAndPhone(meeting, normalizedPhone)
                .ifPresent(existing -> {
                    throw BusinessException.conflict("同一会议内手机号已存在报名记录");
                });
        Attendee attendee = new Attendee();
        attendee.setMeeting(meeting);
        applyRequest(attendee, request);
        attendee.setRegisteredAt(LocalDateTime.now());
        attendee.setAttendeeCode(generateAttendeeCode(meeting));
        return attendeeRepository.save(attendee);
    }

    @Transactional(readOnly = true)
    public PageResponse<Attendee> search(Long meetingId, String keyword, ReviewStatus reviewStatus,
                                         CheckinStatus checkinStatus, AttendeeType attendeeType,
                                         Boolean hasSeat, String tags, int page, int size) {
        return PageResponse.from(attendeeRepository.search(
                meetingId,
                blankToNull(keyword),
                reviewStatus,
                checkinStatus,
                attendeeType,
                hasSeat,
                blankToNull(tags),
                PageRequests.of(page, size)
        ));
    }

    @Transactional(readOnly = true)
    public Attendee detail(Long attendeeId) {
        return attendeeRepository.findById(attendeeId)
                .orElseThrow(() -> BusinessException.notFound("参会人不存在"));
    }

    @Transactional
    public Attendee update(Long attendeeId, AttendeeRequest request) {
        Attendee attendee = attendeeRepository.findById(attendeeId)
                .orElseThrow(() -> BusinessException.notFound("参会人不存在"));
        meetingAccessService.requireManage(attendee.getMeeting().getId());
        Meeting meeting = meetingRepository.findById(request.meetingId())
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        if (!attendee.getMeeting().getId().equals(meeting.getId())) {
            throw BusinessException.badRequest("不能通过更新接口变更参会人所属会议");
        }
        String normalizedPhone = normalizePhone(request.phone());
        attendeeRepository.findByMeetingAndPhone(meeting, normalizedPhone)
                .filter(existing -> !existing.getId().equals(attendee.getId()))
                .ifPresent(existing -> {
                    throw BusinessException.conflict("同一会议内手机号已存在报名记录");
                });
        applyRequest(attendee, request);
        return attendee;
    }

    @Transactional
    public Attendee review(Long attendeeId, ReviewRequest request) {
        Attendee attendee = attendeeRepository.findById(attendeeId)
                .orElseThrow(() -> BusinessException.notFound("参会人不存在"));
        meetingAccessService.requireManage(attendee.getMeeting().getId());
        applyReview(attendee, request.reviewStatus(), request.reason(), request.remark());
        return attendee;
    }

    @Transactional
    public int batchReview(ReviewRequest request) {
        if (request.attendeeIds() == null || request.attendeeIds().isEmpty()) {
            throw BusinessException.badRequest("批量审核必须提供 attendeeIds");
        }
        List<Attendee> attendees = attendeeRepository.findAllById(request.attendeeIds());
        attendees.forEach(attendee -> meetingAccessService.requireManage(attendee.getMeeting().getId()));
        return (int) attendees.stream()
                .filter(attendee -> applyReview(attendee, request.reviewStatus(), request.reason(), request.remark()))
                .count();
    }

    @Transactional
    public Attendee updateTags(Long attendeeId, String tags) {
        Attendee attendee = attendeeRepository.findById(attendeeId)
                .orElseThrow(() -> BusinessException.notFound("参会人不存在"));
        meetingAccessService.requireManage(attendee.getMeeting().getId());
        attendee.setTags(tags);
        return attendee;
    }

    @Transactional
    public Attendee cancel(Long attendeeId, String reason) {
        Attendee attendee = attendeeRepository.findById(attendeeId)
                .orElseThrow(() -> BusinessException.notFound("参会人不存在"));
        meetingAccessService.requireManage(attendee.getMeeting().getId());
        attendee.setReviewStatus(ReviewStatus.CANCELLED);
        attendee.setReviewReason(reason == null || reason.isBlank() ? "管理员取消参会" : reason);
        attendee.setReviewedAt(LocalDateTime.now());
        attendee.setReviewedBy(SecurityUtils.currentUsernameOrSystem());
        return attendee;
    }

    @Transactional
    public Attendee assignRoom(Long attendeeId, String roomNo, String roomRemark) {
        Attendee attendee = attendeeRepository.findById(attendeeId)
                .orElseThrow(() -> BusinessException.notFound("参会人不存在"));
        attendee.setRoomNo(roomNo);
        attendee.setRoomRemark(roomRemark);
        return attendee;
    }

    @Transactional(readOnly = true)
    public Attendee querySeatOrRoom(Long meetingId, String phone) {
        return attendeeRepository.findByMeetingIdAndPhone(meetingId, phone)
                .orElseThrow(() -> BusinessException.notFound("未找到参会人"));
    }

    @Transactional
    public ImportResult importFile(Long meetingId, MultipartFile file) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        meetingAccessService.requireManage(meeting.getId());
        List<TableRow> rows = TableFileReader.read(file);
        List<ImportFailure> failures = new ArrayList<>();
        int imported = 0;

        for (TableRow row : rows) {
            try {
                Attendee attendee = fromRow(meeting, row);
                attendeeRepository.save(attendee);
                imported++;
            } catch (BusinessException ex) {
                failures.add(new ImportFailure(row.rowNumber(), ex.getMessage()));
            } catch (RuntimeException ex) {
                failures.add(new ImportFailure(row.rowNumber(), "数据格式不正确，请检查该行字段"));
            }
        }
        return ImportResult.of(rows.size(), imported, failures, "参会人");
    }

    @Transactional(readOnly = true)
    public String exportCsv(Long meetingId, String type) {
        ExportTable table = exportTable(meetingId, type);
        return CsvFiles.toCsv(table.headers(), table.rows());
    }

    @Transactional(readOnly = true)
    public ExportTable exportTable(Long meetingId, String type) {
        // 导出类型保持和通用导出接口一致，便于前端在专用按钮和“数据导出”页之间复用。
        String normalizedType = normalizeExportType(type);
        List<Attendee> attendees = switch (normalizedType) {
            case "approved" -> attendeeRepository.findByMeetingIdAndReviewStatusOrderByIdAsc(meetingId, ReviewStatus.APPROVED);
            case "no-checkin" -> attendeeRepository.findByMeetingIdAndCheckinStatusOrderByIdAsc(meetingId, CheckinStatus.NOT_CHECKED_IN);
            default -> attendeeRepository.findByMeetingIdOrderByIdAsc(meetingId);
        };
        List<String> baseHeaders = List.of(
                "ID", "姓名", "手机号", "单位", "职务", "证件号", "邮箱", "参会类型",
                "是否住宿", "房间号", "房间备注", "审核状态", "审核原因", "审核备注",
                "审核人", "审核时间", "报名时间", "签到状态", "签到时间", "签到入口",
                "签到操作人", "参会码", "标签", "备注"
        );
        List<FormValueColumn> formColumns = formValueColumns(attendees);
        List<String> headers = new ArrayList<>(baseHeaders);
        headers.addAll(formColumns.stream().map(FormValueColumn::header).toList());
        boolean masked = systemSettingsService.maskSensitiveEnabled();
        var rows = attendees.stream()
                .map(attendee -> exportRow(attendee, formColumns, masked))
                .toList();
        return new ExportTable(attendeeSheetName(normalizedType), headers, rows);
    }

    private void applyRequest(Attendee attendee, AttendeeRequest request) {
        attendee.setName(request.name());
        attendee.setPhone(normalizePhone(request.phone()));
        attendee.setOrganization(request.organization());
        attendee.setTitle(request.title());
        attendee.setIdentityNo(request.identityNo());
        attendee.setEmail(request.email());
        attendee.setAttendeeType(request.attendeeType() == null ? AttendeeType.NORMAL : request.attendeeType());
        attendee.setAccommodationRequired(Boolean.TRUE.equals(request.accommodationRequired()));
        attendee.setRoomNo(request.roomNo());
        attendee.setRoomRemark(request.roomRemark());
        attendee.setRemark(request.remark());
        attendee.setTags(request.tags());
    }

    private boolean applyReview(Attendee attendee, ReviewStatus status, String reason, String remark) {
        if (status == ReviewStatus.PENDING || status == ReviewStatus.CANCELLED) {
            throw BusinessException.badRequest("普通审核只支持通过或拒绝");
        }
        if (attendee.getReviewStatus() == status) {
            // 重复点击同一个审核结果时按幂等处理，不再刷新审核人/审核时间，避免制造多条无意义操作。
            return false;
        }
        if (!canApplyReview(attendee.getReviewStatus(), status)) {
            /*
             * TDuck 报名默认通过，因此普通审核入口允许“已通过 -> 已拒绝”。
             * 但已拒绝/已取消属于终态，不能再通过同一入口随意改回，避免误操作恢复无效报名。
             */
            throw BusinessException.badRequest("当前审核状态不允许执行该操作");
        }
        attendee.setReviewStatus(status);
        attendee.setReviewReason(reason);
        attendee.setReviewRemark(remark);
        attendee.setReviewedAt(LocalDateTime.now());
        attendee.setReviewedBy(SecurityUtils.currentUsernameOrSystem());
        return true;
    }

    private boolean canApplyReview(ReviewStatus currentStatus, ReviewStatus targetStatus) {
        if (currentStatus == ReviewStatus.PENDING) {
            return targetStatus == ReviewStatus.APPROVED || targetStatus == ReviewStatus.REJECTED;
        }
        return currentStatus == ReviewStatus.APPROVED && targetStatus == ReviewStatus.REJECTED;
    }

    private String generateAttendeeCode(Meeting meeting) {
        return meeting.getCode() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private Attendee fromRow(Meeting meeting, TableRow row) {
        String name = required(row, "姓名", "name", "参会人", "attendeename");
        String phone = normalizePhone(required(row, "手机号", "手机", "电话", "phone", "mobile", "tel"));
        attendeeRepository.findByMeetingAndPhone(meeting, phone)
                .ifPresent(existing -> {
                    throw BusinessException.conflict("同一会议内手机号已存在报名记录");
                });

        Attendee attendee = new Attendee();
        attendee.setMeeting(meeting);
        attendee.setName(name);
        attendee.setPhone(phone);
        attendee.setOrganization(row.get("单位", "公司", "机构", "organization", "company"));
        attendee.setTitle(row.get("职务", "职位", "title", "position"));
        attendee.setIdentityNo(row.get("证件号", "身份证号", "身份证", "identityNo", "idcard", "idno"));
        attendee.setEmail(row.get("邮箱", "电子邮箱", "email"));
        attendee.setAttendeeType(parseAttendeeType(row.get("参会类型", "类型", "attendeeType", "type")));
        attendee.setAccommodationRequired(parseBoolean(row.get("是否住宿", "住宿", "accommodationRequired", "needRoom", "roomRequired")));
        attendee.setRoomNo(row.get("房间号", "房号", "roomNo", "room"));
        attendee.setRoomRemark(row.get("房间备注", "住宿备注", "roomRemark"));
        attendee.setRemark(row.get("备注", "remark", "note"));
        attendee.setTags(row.get("标签", "tags"));
        attendee.setReviewStatus(parseReviewStatus(row.get("审核状态", "reviewStatus", "auditStatus")));
        attendee.setRegisteredAt(LocalDateTime.now());
        attendee.setAttendeeCode(generateAttendeeCode(meeting));
        Map<String, Object> snapshot = new LinkedHashMap<>(row.rawValues());
        snapshot.put("source", "admin-import");
        attendee.setRawDataSnapshot(snapshot);
        return attendee;
    }

    private String required(TableRow row, String... aliases) {
        String value = row.get(aliases);
        if (value == null || value.isBlank()) {
            throw BusinessException.badRequest(String.join("/", aliases) + "不能为空");
        }
        return value.trim();
    }

    private AttendeeType parseAttendeeType(String value) {
        if (value == null || value.isBlank()) {
            return AttendeeType.NORMAL;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "VIP", "贵宾" -> AttendeeType.VIP;
            case "GUEST", "嘉宾" -> AttendeeType.GUEST;
            case "MEDIA", "媒体" -> AttendeeType.MEDIA;
            case "STAFF", "工作人员", "员工" -> AttendeeType.STAFF;
            case "NORMAL", "普通", "普通参会人" -> AttendeeType.NORMAL;
            default -> {
                try {
                    yield AttendeeType.valueOf(normalized);
                } catch (IllegalArgumentException ex) {
                    throw BusinessException.badRequest("参会类型不支持: " + value);
                }
            }
        };
    }

    private ReviewStatus parseReviewStatus(String value) {
        if (value == null || value.isBlank()) {
            return ReviewStatus.PENDING;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "APPROVED", "PASS", "通过", "已通过" -> ReviewStatus.APPROVED;
            case "REJECTED", "拒绝", "已拒绝" -> ReviewStatus.REJECTED;
            case "CANCELLED", "CANCELED", "取消", "已取消" -> ReviewStatus.CANCELLED;
            case "PENDING", "待审核", "待审" -> ReviewStatus.PENDING;
            default -> {
                try {
                    yield ReviewStatus.valueOf(normalized);
                } catch (IllegalArgumentException ex) {
                    throw BusinessException.badRequest("审核状态不支持: " + value);
                }
            }
        };
    }

    private boolean parseBoolean(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("true")
                || normalized.equals("1")
                || normalized.equals("yes")
                || normalized.equals("y")
                || normalized.equals("是")
                || normalized.equals("需要")
                || normalized.equals("住宿");
    }

    private String normalizeExportType(String type) {
        return type == null ? "attendees" : type.trim().toLowerCase(Locale.ROOT);
    }

    private String attendeeSheetName(String normalizedType) {
        return switch (normalizedType) {
            case "approved" -> "审核通过名单";
            case "no-checkin" -> "未签到名单";
            default -> "参会名单";
        };
    }

    private List<Object> exportRow(Attendee attendee, List<FormValueColumn> formColumns, boolean masked) {
        List<Object> row = new ArrayList<>(List.of(
                attendee.getId(),
                attendee.getName(),
                masked ? SensitiveDataMasker.maskPhone(attendee.getPhone()) : attendee.getPhone(),
                nullable(attendee.getOrganization()),
                nullable(attendee.getTitle()),
                nullable(masked ? SensitiveDataMasker.maskIdentityNo(attendee.getIdentityNo()) : attendee.getIdentityNo()),
                nullable(masked ? SensitiveDataMasker.maskEmail(attendee.getEmail()) : attendee.getEmail()),
                attendee.getAttendeeType(),
                attendee.isAccommodationRequired() ? "是" : "否",
                nullable(attendee.getRoomNo()),
                nullable(attendee.getRoomRemark()),
                attendee.getReviewStatus(),
                nullable(attendee.getReviewReason()),
                nullable(attendee.getReviewRemark()),
                nullable(attendee.getReviewedBy()),
                nullable(attendee.getReviewedAt()),
                nullable(attendee.getRegisteredAt()),
                attendee.getCheckinStatus(),
                nullable(attendee.getCheckedInAt()),
                nullable(attendee.getCheckinEntrance()),
                nullable(attendee.getCheckinOperator()),
                nullable(attendee.getAttendeeCode()),
                nullable(attendee.getTags()),
                nullable(attendee.getRemark())
        ));
        Map<String, Object> snapshot = attendee.getRawDataSnapshot() == null ? Map.of() : attendee.getRawDataSnapshot();
        Map<String, Object> exportSnapshot = masked ? SensitiveDataMasker.maskMap(snapshot) : snapshot;
        for (FormValueColumn column : formColumns) {
            // TDuck 选择类组件会带 key + keylabel 两份数据；导出优先使用填写人看到的展示标签。
            row.add(nullable(exportSnapshot.getOrDefault(column.labelKey(), exportSnapshot.get(column.valueKey()))));
        }
        return row;
    }

    private List<FormValueColumn> formValueColumns(List<Attendee> attendees) {
        Map<String, FormValueColumn> columns = new LinkedHashMap<>();
        for (Attendee attendee : attendees) {
            Map<String, Object> snapshot = attendee.getRawDataSnapshot();
            if (snapshot == null || snapshot.isEmpty()) {
                continue;
            }
            for (Map.Entry<String, Object> entry : snapshot.entrySet()) {
                String key = entry.getKey();
                if (!hasExportableFormValueKey(key)) {
                    continue;
                }
                /*
                 * 动态报名字段属于参会人主数据的一部分，但字段结构来自 TDuck 表单，不能建固定列。
                 * CSV 按首次出现顺序展开，表头保留原始 key，避免不同表单同名字段发生歧义。
                 */
                columns.putIfAbsent(key, new FormValueColumn(key, key, key + "label"));
            }
        }
        return new ArrayList<>(columns.values());
    }

    private boolean hasExportableFormValueKey(String key) {
        return key != null
                && !key.isBlank()
                && !TduckSubmissionFields.isMetaField(key)
                && !"source".equals(key)
                && !key.endsWith("label");
    }

    private Object nullable(Object value) {
        return value == null ? "" : value;
    }

    private record FormValueColumn(String header, String valueKey, String labelKey) {
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            throw BusinessException.badRequest("手机号不能为空");
        }
        String normalized = phone.replaceAll("[\\s-]", "");
        if (!normalized.matches("^\\+?\\d{6,20}$")) {
            throw BusinessException.badRequest("手机号格式不正确");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
