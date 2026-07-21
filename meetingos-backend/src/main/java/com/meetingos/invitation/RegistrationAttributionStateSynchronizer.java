package com.meetingos.invitation;

import com.meetingos.attendee.AttendeeAttributionStateSynchronizer;
import com.meetingos.tduck.TduckSubmissionStatus;
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
        attributionRepository.updateActiveByMeetingIdAndAttendeeId(meetingId, attendeeId, false);
        if (!active) {
            return;
        }

        /*
         * 先统一失效再按 submission 状态激活，确保一个参会人的历史 dataId 不会因审核通过
         * 被一并重新计入转化。ACTIVE 的唯一约束同时保证这里最多恢复一条当前来源。
         */
        attributionRepository.activateCurrentTduckSubmissionAttributions(
                meetingId,
                attendeeId,
                TduckSubmissionStatus.ACTIVE
        );
    }
}
