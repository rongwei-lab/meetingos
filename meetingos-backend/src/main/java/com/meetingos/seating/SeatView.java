package com.meetingos.seating;

public record SeatView(
        Long seatId,
        String areaName,
        int areaSortOrder,
        int rowNo,
        int seatNo,
        String seatCode,
        SeatType seatType,
        SeatStatus status,
        boolean locked,
        Long attendeeId,
        String attendeeName,
        String attendeeTags
) {
}
