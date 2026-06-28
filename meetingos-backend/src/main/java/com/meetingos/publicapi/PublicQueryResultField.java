package com.meetingos.publicapi;

public enum PublicQueryResultField {
    ATTENDEE_NAME("姓名"),
    ATTENDEE_PHONE("手机号"),
    ORGANIZATION("单位"),
    TITLE("职务"),
    EMAIL("邮箱"),
    MEETING_NAME("会议名称"),
    SEAT_AREA("座位区域"),
    SEAT_ROW("排号"),
    SEAT_NO("座号"),
    SEAT_CODE("座位号"),
    HOTEL_NAME("酒店"),
    ROOM_TYPE("房型"),
    ROOM_NO("房间号"),
    ROOM_REMARK("房间备注"),
    CHECKIN_TIPS("入住提示");

    private final String label;

    PublicQueryResultField(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
