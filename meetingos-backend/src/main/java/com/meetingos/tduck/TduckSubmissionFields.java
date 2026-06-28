package com.meetingos.tduck;

import java.util.Set;

public final class TduckSubmissionFields {

    /*
     * TDuck 提交数据会把业务字段和提交元信息放在同一层。
     * MeetingOS 只把填写人输入的业务字段并入参会人主数据；formKey、浏览器、IP 等技术字段
     * 用于同步定位和审计，不应展示成报名字段或导出成参会人字段。
     */
    private static final Set<String> META_FIELDS = Set.of(
            "id",
            "formKey",
            "formId",
            "formType",
            "serialNumber",
            "submitUa",
            "submitOs",
            "submitBrowser",
            "submitRequestIp",
            "submitAddress",
            "completeTime",
            "wxOpenId",
            "wxUserInfo",
            "extValue",
            "params",
            "createTime",
            "createBy",
            "updateTime",
            "updateBy",
            "eventId",
            "eventType",
            "eventTime",
            "type",
            "dataId",
            "data",
            "raw",
            "originalData"
    );

    private TduckSubmissionFields() {
    }

    public static boolean isMetaField(String key) {
        return key != null && META_FIELDS.contains(key);
    }
}
