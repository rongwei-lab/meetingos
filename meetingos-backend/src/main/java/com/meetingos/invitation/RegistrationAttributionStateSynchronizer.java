package com.meetingos.invitation;

import com.meetingos.attendee.AttendeeAttributionStateSynchronizer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RegistrationAttributionStateSynchronizer implements AttendeeAttributionStateSynchronizer {
    private final RegistrationAttributionRepository attributionRepository;

    public RegistrationAttributionStateSynchronizer(RegistrationAttributionRepository attributionRepository) {
        this.attributionRepository = attributionRepository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void synchronize(Long meetingId, Long attendeeId, boolean active) {
        if (meetingId == null || attendeeId == null) {
            return;
        }
        /*
         * 审核状态和归因 active 必须加入调用方的同一个事务；任何一侧失败都整体回滚，
         * 避免统计把已拒绝或已取消的报名继续算作当前有效报名。
         */
        attributionRepository.updateActiveByMeetingIdAndAttendeeId(meetingId, attendeeId, active);
    }
}
