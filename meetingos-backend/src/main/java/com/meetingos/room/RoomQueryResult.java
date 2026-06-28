package com.meetingos.room;

public record RoomQueryResult(
        Long attendeeId,
        String attendeeName,
        String attendeePhone,
        String organization,
        String title,
        String email,
        String meetingName,
        String roomNo,
        String roomRemark,
        String hotelName,
        String roomType,
        String checkinTips
) {
    public RoomQueryResult(Long attendeeId,
                           String attendeeName,
                           String meetingName,
                           String roomNo,
                           String roomRemark,
                           String hotelName,
                           String roomType,
                           String checkinTips) {
        this(attendeeId, attendeeName, null, null, null, null, meetingName, roomNo, roomRemark, hotelName, roomType, checkinTips);
    }
}
