package com.meetingos.operationlog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OperationLogCategoryTest {

    @Test
    void classifiesKnownActionsAndLetsImportExportTakePrecedence() {
        assertThat(OperationLogCategory.fromAction("ATTENDEE_IMPORT"))
                .isEqualTo(OperationLogCategory.IMPORT_EXPORT);
        assertThat(OperationLogCategory.fromAction("ROOM_EXPORT"))
                .isEqualTo(OperationLogCategory.IMPORT_EXPORT);
        assertThat(OperationLogCategory.fromAction("USER_CREATE"))
                .isEqualTo(OperationLogCategory.ACCOUNT_PERMISSION);
        assertThat(OperationLogCategory.fromAction("MEETING_UPDATE"))
                .isEqualTo(OperationLogCategory.MEETING_CONFIGURATION);
        assertThat(OperationLogCategory.fromAction("CHECKIN"))
                .isEqualTo(OperationLogCategory.ATTENDEE_CHECKIN);
        assertThat(OperationLogCategory.fromAction("SEATING_AUTO_ASSIGN"))
                .isEqualTo(OperationLogCategory.VENUE_SEATING);
        assertThat(OperationLogCategory.fromAction("TDUCK_BIND_FORM"))
                .isEqualTo(OperationLogCategory.REGISTRATION_TDUCK);
        assertThat(OperationLogCategory.fromAction("INVITATION_PUBLISH"))
                .isEqualTo(OperationLogCategory.INVITATION);
    }

    @Test
    void exposesDerivedCategoryWithoutPersistingIt() {
        OperationLog log = new OperationLog();
        log.setAction("UNKNOWN_HISTORICAL_ACTION");

        assertThat(log.getCategory()).isEqualTo(OperationLogCategory.SYSTEM);
    }
}
