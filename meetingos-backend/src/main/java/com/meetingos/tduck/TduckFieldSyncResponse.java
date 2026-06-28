package com.meetingos.tduck;

import java.util.List;

public record TduckFieldSyncResponse(
        String formKey,
        String formName,
        int fieldCount,
        int mappedCount,
        MappingStatus mappingStatus,
        List<TduckFieldSyncItem> fields
) {
}
