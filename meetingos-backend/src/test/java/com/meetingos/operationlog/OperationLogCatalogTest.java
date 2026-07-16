package com.meetingos.operationlog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OperationLogCatalogTest {

    @Test
    void findsCurrentAndHistoricalChineseAliases() {
        assertThat(OperationLogCatalog.matchingActions("签到")).contains("CHECKIN");
        assertThat(OperationLogCatalog.matchingActions("更新标签"))
                .contains("ATTENDEE_TAGS_UPDATE", "ATTENDEE_UPDATE_TAGS");
        assertThat(OperationLogCatalog.matchingActions("更新房间")).contains("ROOM_UPDATE");
        assertThat(OperationLogCatalog.matchingActions("更换 TDuck")).contains("TDUCK_REBIND_FORM");
        assertThat(OperationLogCatalog.matchingActions("同步 TDuck 字段")).contains("TDUCK_SYNC_FIELDS");
        assertThat(OperationLogCatalog.matchingTargetTypes("渠道链接")).contains("INVITATION_SHARE_LINK");
        assertThat(OperationLogCatalog.matchingCategories("报名"))
                .contains(OperationLogCategory.REGISTRATION_TDUCK);
        assertThat(OperationLogCatalog.matchesSuccess("成功")).isTrue();
        assertThat(OperationLogCatalog.matchesFailure("失败")).isTrue();
    }
}
