package com.meetingos.statistics.dashboard;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.meetingos.common.BaseEntity;
import com.meetingos.common.JsonMapConverter;
import com.meetingos.meeting.Meeting;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 会议默认统计看板配置。
 *
 * <p>组件结构会随前端能力演进，使用 JSON 保存而非拆成固定列；执行统计时仍会转成受控 DTO
 * 并按字段目录校验，配置 JSON 不会直接参与查询拼接。</p>
 */
@Entity
@Table(name = "meeting_dashboard_configs", uniqueConstraints = {
        @UniqueConstraint(name = "uk_dashboard_config_meeting", columnNames = "meeting_id")
})
public class MeetingDashboardConfig extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    @JsonIgnore
    private Meeting meeting;

    @Convert(converter = JsonMapConverter.class)
    @Column(nullable = false, columnDefinition = "json")
    private Map<String, Object> configuration = new LinkedHashMap<>();

    public Meeting getMeeting() {
        return meeting;
    }

    public void setMeeting(Meeting meeting) {
        this.meeting = meeting;
    }

    public Map<String, Object> getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Map<String, Object> configuration) {
        this.configuration = configuration == null ? new LinkedHashMap<>() : new LinkedHashMap<>(configuration);
    }
}
