package com.meetingos.tduck;

public record TduckFieldSyncItem(
        String tduckFieldKey,
        String tduckFieldLabel,
        String tduckFieldType,
        boolean tduckRequired,
        AttendeeField attendeeField,
        boolean requiredMapping,
        MappingStatus status
) {
}
