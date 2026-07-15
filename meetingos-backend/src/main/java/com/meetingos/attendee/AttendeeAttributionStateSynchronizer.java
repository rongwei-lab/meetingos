package com.meetingos.attendee;

/**
 * 参会人模块只声明报名有效性同步契约，具体的邀请函归因存储由 invitation 模块实现。
 */
@FunctionalInterface
public interface AttendeeAttributionStateSynchronizer {
    AttendeeAttributionStateSynchronizer NOOP = (meetingId, attendeeId, active) -> {
    };

    void synchronize(Long meetingId, Long attendeeId, boolean active);
}
