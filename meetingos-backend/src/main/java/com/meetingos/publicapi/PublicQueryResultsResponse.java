package com.meetingos.publicapi;

import java.util.List;

public record PublicQueryResultsResponse<T>(
        boolean multiple,
        int total,
        List<T> results
) {
}
