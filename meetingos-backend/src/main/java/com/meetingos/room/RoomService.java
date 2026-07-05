package com.meetingos.room;

import com.meetingos.attendee.Attendee;
import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.attendee.ReviewStatus;
import com.meetingos.common.BusinessException;
import com.meetingos.common.CsvFiles;
import com.meetingos.common.ExportTable;
import com.meetingos.common.ImportFailure;
import com.meetingos.common.ImportResult;
import com.meetingos.common.SensitiveDataMasker;
import com.meetingos.common.TableFileReader;
import com.meetingos.common.TableRow;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import com.meetingos.operationlog.OperationLogService;
import com.meetingos.settings.SystemSettingsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RoomService {

    private final MeetingRepository meetingRepository;
    private final RoomRepository roomRepository;
    private final AttendeeRepository attendeeRepository;
    private final OperationLogService operationLogService;
    private final SystemSettingsService systemSettingsService;

    public RoomService(MeetingRepository meetingRepository,
                       RoomRepository roomRepository,
                       AttendeeRepository attendeeRepository,
                       OperationLogService operationLogService,
                       SystemSettingsService systemSettingsService) {
        this.meetingRepository = meetingRepository;
        this.roomRepository = roomRepository;
        this.attendeeRepository = attendeeRepository;
        this.operationLogService = operationLogService;
        this.systemSettingsService = systemSettingsService;
    }

    @Transactional
    public Room create(RoomRequest request) {
        Meeting meeting = meetingRepository.findById(request.meetingId())
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        roomRepository.findByMeetingIdAndRoomNo(request.meetingId(), request.roomNo()).ifPresent(existing -> {
            throw BusinessException.conflict("房间号已存在");
        });
        Room room = new Room();
        room.setMeeting(meeting);
        apply(room, request);
        return roomRepository.save(room);
    }

    @Transactional(readOnly = true)
    public List<Room> list(Long meetingId) {
        return roomRepository.findByMeetingId(meetingId);
    }

    @Transactional
    public Attendee assign(AssignRoomRequest request) {
        Attendee attendee = attendeeRepository.findById(request.attendeeId())
                .orElseThrow(() -> BusinessException.notFound("参会人不存在"));
        Room room = roomRepository.findByMeetingIdAndRoomNo(attendee.getMeeting().getId(), requiredRoomNo(request.roomNo()))
                .orElseThrow(() -> BusinessException.notFound("房间号不存在: " + request.roomNo()));
        assignAttendeeToRoom(attendee, room, request.roomRemark(), "MANUAL");
        return attendee;
    }

    private void assignAttendeeToRoom(Attendee attendee, Room room, String roomRemark, String source) {
        String oldRoomNo = attendee.getRoomNo();
        if (room.getStatus() == RoomStatus.DISABLED) {
            throw BusinessException.badRequest("停用房间不能分配入住人员");
        }
        /*
         * 容量按“当前会议 + 房间号”实时统计。参会人从同一房间重复保存时不占用额外名额；
         * 从其他房间迁入时，只有目标房间未满才允许写入，避免多人误挤到单人房。
         */
        long occupied = attendeeRepository.countByMeetingIdAndRoomNo(attendee.getMeeting().getId(), room.getRoomNo());
        boolean alreadyInThisRoom = room.getRoomNo().equals(oldRoomNo);
        if (!alreadyInThisRoom && occupied >= normalizedCapacity(room.getCapacity())) {
            throw BusinessException.badRequest("该房间已达到可入住人数上限");
        }
        room.setStatus(RoomStatus.ASSIGNED);
        attendee.setRoomNo(room.getRoomNo());
        attendee.setRoomRemark(roomRemark);
        attendee.setAccommodationRequired(true);
        if (oldRoomNo != null && !oldRoomNo.isBlank() && !oldRoomNo.equals(room.getRoomNo())) {
            roomRepository.findByMeetingIdAndRoomNo(attendee.getMeeting().getId(), oldRoomNo)
                    .ifPresent(this::refreshRoomStatus);
        }
        recordRoomChange(attendee, oldRoomNo, room.getRoomNo(), source, roomRemark);
    }

    @Transactional
    public Attendee clearAssignment(Long attendeeId) {
        Attendee attendee = attendeeRepository.findById(attendeeId)
                .orElseThrow(() -> BusinessException.notFound("参会人不存在"));
        String oldRoomNo = attendee.getRoomNo();
        attendee.setRoomNo(null);
        attendee.setRoomRemark(null);
        attendee.setAccommodationRequired(false);
        if (oldRoomNo != null && !oldRoomNo.isBlank()) {
            roomRepository.findByMeetingIdAndRoomNo(attendee.getMeeting().getId(), oldRoomNo)
                    .ifPresent(this::refreshRoomStatus);
        }
        recordRoomChange(attendee, oldRoomNo, null, "MANUAL_CLEAR", null);
        return attendee;
    }

    @Transactional
    public ImportResult importRooms(Long meetingId, MultipartFile file) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        List<TableRow> rows = TableFileReader.read(file);
        List<ImportFailure> failures = new ArrayList<>();
        int imported = 0;

        for (TableRow row : rows) {
            try {
                Room room = fromRoomRow(meeting, row);
                roomRepository.save(room);
                imported++;
            } catch (BusinessException ex) {
                failures.add(new ImportFailure(row.rowNumber(), ex.getMessage()));
            } catch (RuntimeException ex) {
                failures.add(new ImportFailure(row.rowNumber(), "房间数据格式不正确，请检查该行字段"));
            }
        }
        return ImportResult.of(rows.size(), imported, failures, "房间号");
    }

    @Transactional
    public ImportResult importAssignments(Long meetingId, MultipartFile file) {
        meetingRepository.findById(meetingId)
                .orElseThrow(() -> BusinessException.notFound("会议不存在"));
        List<TableRow> rows = TableFileReader.read(file);
        List<ImportFailure> failures = new ArrayList<>();
        int imported = 0;

        for (TableRow row : rows) {
            try {
                Attendee attendee = resolveAttendee(meetingId, row);
                String roomNo = required(row, "房间号", "房号", "roomNo", "room");
                Room room = roomRepository.findByMeetingIdAndRoomNo(meetingId, roomNo)
                        .orElseThrow(() -> BusinessException.notFound("房间号不存在: " + roomNo));
                assignAttendeeToRoom(attendee, room, row.get("房间备注", "住宿备注", "remark", "roomRemark"), "IMPORT");
                imported++;
            } catch (BusinessException ex) {
                failures.add(new ImportFailure(row.rowNumber(), ex.getMessage()));
            } catch (RuntimeException ex) {
                failures.add(new ImportFailure(row.rowNumber(), "分配数据格式不正确，请检查该行字段"));
            }
        }
        return ImportResult.of(rows.size(), imported, failures, "房间分配");
    }

    @Transactional(readOnly = true)
    public RoomQueryResult query(Long meetingId, String phone) {
        Attendee attendee = attendeeRepository.findByMeetingIdAndPhone(meetingId, phone)
                .orElseThrow(() -> BusinessException.notFound("未找到参会人"));
        if (attendee.getReviewStatus() != ReviewStatus.APPROVED) {
            throw BusinessException.badRequest("报名尚未审核通过，暂不能查询房间");
        }
        if (attendee.getRoomNo() == null || attendee.getRoomNo().isBlank()) {
            throw BusinessException.notFound("尚未安排房间");
        }
        Room room = roomRepository.findByMeetingIdAndRoomNo(meetingId, attendee.getRoomNo()).orElse(null);
        return new RoomQueryResult(
                attendee.getId(),
                attendee.getName(),
                attendee.getPhone(),
                attendee.getOrganization(),
                attendee.getTitle(),
                attendee.getEmail(),
                attendee.getMeeting().getName(),
                attendee.getRoomNo(),
                attendee.getRoomRemark(),
                room == null ? null : room.getHotelName(),
                room == null ? null : room.getRoomType(),
                room == null ? null : room.getRemark()
        );
    }

    @Transactional(readOnly = true)
    public String exportRoomsCsv(Long meetingId) {
        ExportTable table = exportRoomsTable(meetingId);
        return CsvFiles.toCsv(table.headers(), table.rows());
    }

    @Transactional(readOnly = true)
    public ExportTable exportRoomsTable(Long meetingId) {
        List<Attendee> assignments = attendeeRepository.findByMeetingIdAndRoomNoIsNotNullOrderByIdAsc(meetingId);
        var roomNos = assignments.stream()
                .map(Attendee::getRoomNo)
                .filter(roomNo -> roomNo != null && !roomNo.isBlank())
                .collect(Collectors.toSet());
        Map<String, Room> roomByNo = roomNos.isEmpty()
                ? Map.of()
                : roomRepository.findByMeetingIdAndRoomNoIn(meetingId, roomNos).stream()
                .collect(Collectors.toMap(Room::getRoomNo, Function.identity(), (existing, replacement) -> existing));
        var rows = assignments.stream()
                .map(attendee -> {
                    Room room = roomByNo.get(attendee.getRoomNo());
                    return List.of(
                            attendee.getName(),
                            exportPhone(attendee.getPhone()),
                            nullable(attendee.getOrganization()),
                            nullable(attendee.getTitle()),
                            attendee.getAttendeeType(),
                            attendee.getRoomNo(),
                            room == null ? "" : nullable(room.getHotelName()),
                            room == null ? "" : nullable(room.getRoomType()),
                            room == null ? "" : String.valueOf(normalizedCapacity(room.getCapacity())),
                            room == null ? "" : nullable(room.getRemark()),
                            nullable(attendee.getRoomRemark())
                    );
                })
                .toList();
        return new ExportTable(
                "住宿安排",
                List.of("姓名", "手机号", "单位", "职务", "参会类型", "房间号", "酒店", "房型", "可入住人数", "入住提示", "房间备注"),
                rows
        );
    }

    @Transactional(readOnly = true)
    public String exportRoomInventoryCsv(Long meetingId) {
        ExportTable table = exportRoomInventoryTable(meetingId);
        return CsvFiles.toCsv(table.headers(), table.rows());
    }

    @Transactional(readOnly = true)
    public ExportTable exportRoomInventoryTable(Long meetingId) {
        var rows = roomRepository.findByMeetingIdOrderByRoomNoAsc(meetingId).stream()
                .map(room -> List.of(
                        room.getRoomNo(),
                        nullable(room.getHotelName()),
                        nullable(room.getRoomType()),
                        normalizedCapacity(room.getCapacity()),
                        room.getStatus(),
                        nullable(room.getRemark())
                ))
                .toList();
        return new ExportTable("房间库存", List.of("房间号", "酒店", "房型", "可入住人数", "状态", "备注"), rows);
    }

    private void apply(Room room, RoomRequest request) {
        room.setRoomNo(request.roomNo());
        room.setRoomType(request.roomType());
        room.setHotelName(request.hotelName());
        room.setRemark(request.remark());
        room.setCapacity(normalizedCapacity(request.capacity()));
        room.setStatus(request.status() == null ? RoomStatus.AVAILABLE : request.status());
    }

    private Room fromRoomRow(Meeting meeting, TableRow row) {
        String roomNo = required(row, "房间号", "房号", "roomNo", "room");
        roomRepository.findByMeetingIdAndRoomNo(meeting.getId(), roomNo).ifPresent(existing -> {
            throw BusinessException.conflict("房间号已存在: " + roomNo);
        });
        Room room = new Room();
        room.setMeeting(meeting);
        room.setRoomNo(roomNo);
        room.setRoomType(row.get("房型", "房间类型", "roomType", "type"));
        room.setHotelName(row.get("酒店", "酒店名称", "hotel", "hotelName"));
        room.setRemark(row.get("备注", "入住提示", "checkinTips", "remark"));
        room.setCapacity(parseCapacity(row.get("可入住人数", "入住人数", "容量", "床位数", "capacity", "maxOccupancy")));
        room.setStatus(RoomStatus.AVAILABLE);
        return room;
    }

    private String requiredRoomNo(String roomNo) {
        if (roomNo == null || roomNo.isBlank()) {
            throw BusinessException.badRequest("房间号不能为空");
        }
        return roomNo.trim();
    }

    private int normalizedCapacity(Integer capacity) {
        return capacity == null || capacity < 1 ? 1 : capacity;
    }

    private int parseCapacity(String value) {
        if (value == null || value.isBlank()) return 1;
        try {
            return Math.max(1, Integer.parseInt(value.trim()));
        } catch (NumberFormatException ex) {
            throw BusinessException.badRequest("可入住人数必须是正整数");
        }
    }

    private void refreshRoomStatus(Room room) {
        if (room.getStatus() == RoomStatus.DISABLED) return;
        long occupied = attendeeRepository.countByMeetingIdAndRoomNo(room.getMeeting().getId(), room.getRoomNo());
        room.setStatus(occupied > 0 ? RoomStatus.ASSIGNED : RoomStatus.AVAILABLE);
    }

    private Attendee resolveAttendee(Long meetingId, TableRow row) {
        String phone = row.get("手机号", "手机", "电话", "phone", "mobile");
        if (phone != null && !phone.isBlank()) {
            return attendeeRepository.findByMeetingIdAndPhone(meetingId, phone.trim())
                    .orElseThrow(() -> BusinessException.notFound("未找到手机号对应参会人: " + phone));
        }
        String identityNo = row.get("证件号", "身份证号", "身份证", "identityNo", "idcard", "idno");
        if (identityNo != null && !identityNo.isBlank()) {
            return attendeeRepository.findByMeetingIdAndIdentityNo(meetingId, identityNo.trim())
                    .orElseThrow(() -> BusinessException.notFound("未找到证件号对应参会人: " + identityNo));
        }
        String name = row.get("姓名", "name", "参会人");
        String organization = row.get("单位", "公司", "机构", "organization", "company");
        if (name != null && !name.isBlank() && organization != null && !organization.isBlank()) {
            List<Attendee> matched = attendeeRepository.findByMeetingIdAndNameAndOrganization(meetingId, name.trim(), organization.trim());
            if (matched.size() == 1) {
                return matched.get(0);
            }
            if (matched.isEmpty()) {
                throw BusinessException.notFound("未找到姓名+单位对应参会人: " + name + "/" + organization);
            }
            throw BusinessException.conflict("姓名+单位匹配到多名参会人，请改用手机号或证件号");
        }
        throw BusinessException.badRequest("请提供手机号、证件号，或姓名+单位用于匹配参会人");
    }

    private String required(TableRow row, String... aliases) {
        String value = row.get(aliases);
        if (value == null || value.isBlank()) {
            throw BusinessException.badRequest(String.join("/", aliases) + "不能为空");
        }
        return value.trim();
    }

    private Object nullable(Object value) {
        return value == null ? "" : value;
    }

    private String exportPhone(String phone) {
        if (phone == null) {
            return "";
        }
        return systemSettingsService.maskSensitiveEnabled() ? SensitiveDataMasker.maskPhone(phone) : phone;
    }

    private void recordRoomChange(Attendee attendee, String oldRoomNo, String newRoomNo, String source, String remark) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("meetingId", attendee.getMeeting().getId());
        details.put("attendeeId", attendee.getId());
        details.put("attendeeName", attendee.getName());
        details.put("oldRoomNo", oldRoomNo);
        details.put("newRoomNo", newRoomNo);
        details.put("source", source);
        details.put("remark", remark);
        operationLogService.record(
                "ROOM_ASSIGNMENT_CHANGE",
                "ROOM",
                attendee.getId() == null ? null : attendee.getId().toString(),
                true,
                "房间分配变更",
                null,
                details
        );
    }
}
