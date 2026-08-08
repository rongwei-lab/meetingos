package com.meetingos.seating;

import java.util.List;

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
        boolean structuralBlank,
        boolean aisle,
        List<Integer> aisleAfterSeatNos,
        boolean unregistered,
        Long attendeeId,
        String attendeeName,
        String attendeeTags
) {
}
