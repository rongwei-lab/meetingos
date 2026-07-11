package com.meetingos.importtask;

import com.meetingos.common.BusinessException;
import com.meetingos.common.TableFileSchema;

import java.util.List;
import java.util.Locale;

public enum ImportTaskType {
    ATTENDEES("attendees", attendeeSchema()),
    ROOMS("rooms", roomSchema()),
    ROOM_ASSIGNMENTS("room-assignments", roomAssignmentSchema()),
    DINNER("dinner", dinnerSchema());

    private final String value;
    private final TableFileSchema schema;

    ImportTaskType(String value, TableFileSchema schema) {
        this.value = value;
        this.schema = schema;
    }

    public String value() {
        return value;
    }

    public TableFileSchema schema() {
        return schema;
    }

    public static ImportTaskType from(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        for (ImportTaskType type : values()) {
            if (type.value.equals(normalized)) {
                return type;
            }
        }
        throw BusinessException.badRequest("暂不支持的导入类型: " + value);
    }

    private static TableFileSchema attendeeSchema() {
        var name = TableFileSchema.column("姓名", "姓名", "name", "参会人", "attendeeName");
        var phone = TableFileSchema.column("手机号", "手机号", "手机", "电话", "phone", "mobile", "tel");
        var identity = TableFileSchema.column("证件号", "证件号", "身份证号", "身份证", "identityNo", "idcard", "idno");
        var roomNo = TableFileSchema.column("房间号", "房间号", "房号", "roomNo", "room");
        return new TableFileSchema(
                List.of(
                        TableFileSchema.require("姓名", TableFileSchema.option(name)),
                        TableFileSchema.require("手机号", TableFileSchema.option(phone))
                ),
                List.of(phone, identity, roomNo)
        );
    }

    private static TableFileSchema roomSchema() {
        var roomNo = TableFileSchema.column("房间号", "房间号", "房号", "roomNo", "room");
        return new TableFileSchema(
                List.of(TableFileSchema.require("房间号", TableFileSchema.option(roomNo))),
                List.of(roomNo)
        );
    }

    private static TableFileSchema roomAssignmentSchema() {
        var roomNo = TableFileSchema.column("房间号", "房间号", "房号", "roomNo", "room");
        return assignmentSchema("房间号", roomNo);
    }

    private static TableFileSchema dinnerSchema() {
        var tableNo = TableFileSchema.column("桌号", "桌号", "餐桌号", "桌位", "tableNo", "table");
        return assignmentSchema("桌号", tableNo);
    }

    private static TableFileSchema assignmentSchema(String targetLabel, TableFileSchema.Column target) {
        var attendeeCode = TableFileSchema.column("参会码", "参会码", "attendeeCode", "code");
        var phone = TableFileSchema.column("手机号", "手机号", "手机", "电话", "phone", "mobile");
        var identity = TableFileSchema.column("证件号", "证件号", "身份证号", "身份证", "identityNo", "idcard", "idno");
        var name = TableFileSchema.column("姓名", "姓名", "name", "参会人");
        var organization = TableFileSchema.column("单位", "单位", "公司", "机构", "organization", "company");
        return new TableFileSchema(
                List.of(
                        TableFileSchema.require(targetLabel, TableFileSchema.option(target)),
                        TableFileSchema.require(
                                "参会码、手机号、证件号，或姓名+单位",
                                TableFileSchema.option(attendeeCode),
                                TableFileSchema.option(phone),
                                TableFileSchema.option(identity),
                                TableFileSchema.option(name, organization)
                        )
                ),
                List.of(target, attendeeCode, phone, identity)
        );
    }
}
