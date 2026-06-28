package com.meetingos.seating;

public record SeatQueryResult(
        Long attendeeId,
        String attendeeName,
        String attendeePhone,
        String organization,
        String title,
        String email,
        String meetingName,
        String areaName,
        int rowNo,
        int seatNo,
        String seatCode,
        PublicSeatMap seatMap
) {
    public SeatQueryResult(Long attendeeId,
                           String attendeeName,
                           String meetingName,
                           String areaName,
                           int rowNo,
                           int seatNo,
                           String seatCode) {
        this(attendeeId, attendeeName, null, null, null, null, meetingName, areaName, rowNo, seatNo, seatCode, null);
    }

    public SeatQueryResult(Long attendeeId,
                           String attendeeName,
                           String attendeePhone,
                           String organization,
                           String title,
                           String email,
                           String meetingName,
                           String areaName,
                           int rowNo,
                           int seatNo,
                           String seatCode) {
        this(attendeeId, attendeeName, attendeePhone, organization, title, email, meetingName, areaName, rowNo, seatNo, seatCode, null);
    }

    public record PublicSeatMap(
            String areaName,
            int rowCount,
            int seatsPerRow,
            int currentRowNo,
            int currentSeatNo
    ) {
    }
}
