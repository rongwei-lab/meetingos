package com.meetingos.attendee;

import com.meetingos.common.SensitiveDataMasker;
import com.meetingos.seating.Seat;
import com.meetingos.seating.SeatAssignment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 统一参会人同步、异步导出的列顺序和取值规则，避免两个下载入口产生不同字段。
 */
public final class AttendeeExportData {

    private static final List<String> SYSTEM_HEADERS = List.of(
            "标签", "审核", "座位", "房间", "座位查询", "住宿查询", "签到", "参会人备注"
    );

    private AttendeeExportData() {
    }

    public static List<String> headers(List<DynamicColumn> dynamicColumns) {
        List<String> headers = new ArrayList<>(dynamicColumns.size() + SYSTEM_HEADERS.size());
        headers.addAll(dynamicColumns.stream().map(DynamicColumn::header).toList());
        headers.addAll(SYSTEM_HEADERS);
        return headers;
    }

    public static List<Object> row(Attendee attendee,
                                   RelatedData related,
                                   List<DynamicColumn> dynamicColumns,
                                   boolean masked) {
        SeatAssignment seatAssignment = related.seatAssignment();
        Seat seat = seatAssignment == null ? null : seatAssignment.getSeat();
        Map<String, Object> snapshot = attendee.getRawDataSnapshot() == null ? Map.of() : attendee.getRawDataSnapshot();

        List<Object> row = new ArrayList<>(dynamicColumns.size() + SYSTEM_HEADERS.size());
        for (DynamicColumn column : dynamicColumns) {
            Object value = snapshot.getOrDefault(column.labelKey(), snapshot.get(column.valueKey()));
            row.add(nullable(masked
                    ? SensitiveDataMasker.maskValue(column.valueKey(), column.header(), value)
                    : value));
        }
        row.addAll(List.of(
                nullable(attendee.getTags()),
                attendee.getReviewStatus(),
                nullable(seatLabel(seat)),
                nullable(attendee.getRoomNo()),
                queriedText(related.seatQueried()),
                queriedText(related.roomQueried()),
                attendee.getCheckinStatus(),
                nullable(attendee.getRemark())
        ));
        return row;
    }

    private static String seatLabel(Seat seat) {
        return seat == null ? null : seat.getAreaName() + " " + seat.getRowNo() + "排" + seat.getSeatNo() + "座";
    }

    private static String queriedText(boolean queried) {
        return queried ? "已查询" : "未查询";
    }

    private static Object nullable(Object value) {
        return value == null ? "" : value;
    }

    public record RelatedData(
            SeatAssignment seatAssignment,
            boolean seatQueried,
            boolean roomQueried
    ) {
    }

    public record DynamicColumn(String header, String valueKey, String labelKey) {
    }
}
