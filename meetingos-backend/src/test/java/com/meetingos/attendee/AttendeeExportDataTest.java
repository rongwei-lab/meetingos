package com.meetingos.attendee;

import com.meetingos.meeting.Meeting;
import com.meetingos.seating.Seat;
import com.meetingos.seating.SeatAssignment;
import com.meetingos.seating.SeatType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AttendeeExportDataTest {

    @Test
    void exportsTduckFieldsFollowedByRequestedSystemColumns() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 11, 20, 0);
        Meeting meeting = new Meeting();
        meeting.setId(9L);

        Attendee attendee = attendee(meeting, now);
        attendee.setId(18L);
        attendee.setRoomNo("1801");
        attendee.setTags("嘉宾,重点接待");
        attendee.setRemark("参会人备注");
        attendee.setRawDataSnapshot(new LinkedHashMap<>(Map.of(
                "name", "张三",
                "contactPhone", "13900001234",
                "custom", "内部值",
                "customlabel", "展示值"
        )));

        Seat seat = new Seat();
        seat.setAreaName("A区");
        seat.setRowNo(3);
        seat.setSeatNo(8);
        seat.setSeatType(SeatType.VIP);
        SeatAssignment seatAssignment = new SeatAssignment();
        seatAssignment.setAttendee(attendee);
        seatAssignment.setSeat(seat);

        var dynamicColumns = List.of(
                new AttendeeExportData.DynamicColumn("姓名", "name", "namelabel"),
                new AttendeeExportData.DynamicColumn("联系电话", "contactPhone", "contactPhonelabel"),
                new AttendeeExportData.DynamicColumn("自定义字段", "custom", "customlabel")
        );
        var related = new AttendeeExportData.RelatedData(seatAssignment, true, true);
        List<String> headers = AttendeeExportData.headers(dynamicColumns);
        List<Object> row = AttendeeExportData.row(attendee, related, dynamicColumns, false);
        Map<String, Object> values = valuesByHeader(headers, row);

        assertThat(row).hasSameSizeAs(headers);
        assertThat(headers).containsExactly(
                "姓名", "联系电话", "自定义字段",
                "标签", "审核", "座位", "房间", "座位查询", "住宿查询", "签到", "参会人备注"
        );
        assertThat(values)
                .containsEntry("姓名", "张三")
                .containsEntry("联系电话", "13900001234")
                .containsEntry("自定义字段", "展示值");
        assertThat(values)
                .containsEntry("标签", "嘉宾,重点接待")
                .containsEntry("审核", ReviewStatus.APPROVED)
                .containsEntry("座位", "A区 3排8座")
                .containsEntry("房间", "1801")
                .containsEntry("座位查询", "已查询")
                .containsEntry("住宿查询", "已查询")
                .containsEntry("签到", CheckinStatus.NOT_CHECKED_IN)
                .containsEntry("参会人备注", "参会人备注");
        assertThat(headers).doesNotContain("会议编码", "晚宴桌号", "房间ID", "审核人", "签到时间");
    }

    @Test
    void stillMasksSensitiveFixedAndDynamicValuesWhenConfigured() {
        Meeting meeting = new Meeting();
        meeting.setId(1L);
        Attendee attendee = attendee(meeting, LocalDateTime.of(2026, 7, 11, 20, 0));
        attendee.setRawDataSnapshot(Map.of(
                "contactPhone", "13900001234",
                "identity", "110101199001010011",
                "mail", "zhangsan@example.com"
        ));
        var columns = List.of(
                new AttendeeExportData.DynamicColumn("联系电话", "contactPhone", "contactPhonelabel"),
                new AttendeeExportData.DynamicColumn("证件号", "identity", "identitylabel"),
                new AttendeeExportData.DynamicColumn("邮箱", "mail", "maillabel")
        );
        var related = new AttendeeExportData.RelatedData(null, false, false);
        Map<String, Object> values = valuesByHeader(
                AttendeeExportData.headers(columns),
                AttendeeExportData.row(attendee, related, columns, true)
        );

        assertThat(String.valueOf(values.get("联系电话"))).contains("****");
        assertThat(String.valueOf(values.get("证件号"))).contains("****");
        assertThat(String.valueOf(values.get("邮箱"))).contains("***");
    }

    private Attendee attendee(Meeting meeting, LocalDateTime now) {
        Attendee attendee = new Attendee();
        attendee.setMeeting(meeting);
        attendee.setName("张三");
        attendee.setPhone("13800000001");
        attendee.setOrganization("测试单位");
        attendee.setTitle("经理");
        attendee.setIdentityNo("110101199001010011");
        attendee.setEmail("zhangsan@example.com");
        attendee.setAttendeeType(AttendeeType.VIP);
        attendee.setAccommodationRequired(true);
        attendee.setReviewStatus(ReviewStatus.APPROVED);
        attendee.setReviewedAt(now);
        attendee.setRegisteredAt(now);
        attendee.setCheckinStatus(CheckinStatus.NOT_CHECKED_IN);
        attendee.setAttendeeCode("ATTENDEE-1");
        attendee.setCreatedAt(now);
        attendee.setUpdatedAt(now.plusMinutes(1));
        return attendee;
    }

    private Map<String, Object> valuesByHeader(List<String> headers, List<Object> row) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < headers.size(); index++) {
            values.put(headers.get(index), row.get(index));
        }
        return values;
    }
}
