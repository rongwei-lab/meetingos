package com.meetingos.attendee;

import java.util.List;

public interface AttendeePublicQueryRepository {
    List<AttendeeMatchCandidate> findPublicQueryCandidates(AttendeePublicQueryCriteria criteria);
}
