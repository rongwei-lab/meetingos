package com.meetingos.publicapi;

public enum PublicQueryField {
    NAME("姓名"),
    PHONE("手机号"),
    PHONE_LAST4("手机号后四位"),
    ATTENDEE_CODE("参会码"),
    ORGANIZATION("单位"),
    TITLE("职务"),
    EMAIL("邮箱");

    private final String label;

    PublicQueryField(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
