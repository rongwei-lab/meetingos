package com.meetingos.operationlog;

import com.meetingos.common.PageResponse;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OperationLogFilteringIntegrationTest {

    @Autowired
    private OperationLogRepository operationLogRepository;
    @Autowired
    private OperationLogService operationLogService;
    @Autowired
    private OperationLogRetentionService operationLogRetentionService;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void clearLogs() {
        operationLogRepository.deleteAll();
        operationLogRepository.flush();
    }

    @Test
    void hidesDraftSavesByDefaultAndIncludesThemOnlyWhenRequested() {
        save("INVITATION_DRAFT_SAVE", "designer", "MEETING_INVITATION", "draft", true, "自动保存", now());
        save("INVITATION_DRAFT_SAVE", "designer", "MEETING_INVITATION", "draft", false, "草稿版本冲突", now());
        save("INVITATION_PUBLISH", "designer", "MEETING_INVITATION", "published", true, "OK", now());

        assertThat(actions(OperationLogFilter.fromRequest(null, null, null, null, null, null, null)))
                .containsExactly("INVITATION_PUBLISH");
        assertThat(actions(OperationLogFilter.fromRequest(null, null, null, null, null, null, "true")))
                .containsExactlyInAnyOrder(
                        "INVITATION_DRAFT_SAVE", "INVITATION_DRAFT_SAVE", "INVITATION_PUBLISH"
                );
    }

    @Test
    void appliesCategoryWithImportExportPrecedenceAndSupportsChineseCategoryKeyword() {
        save("ATTENDEE_IMPORT", "operator", "ATTENDEE", "imported", true, "OK", now());
        save("ATTENDEE_UPDATE", "operator", "ATTENDEE", "attendee", true, "OK", now());
        save("TDUCK_BIND_FORM", "operator", "TDUCK_FORM", "form", true, "OK", now());

        assertThat(actions(filter(OperationLogCategory.IMPORT_EXPORT, null, null, null, null)))
                .containsExactly("ATTENDEE_IMPORT");
        assertThat(actions(filter(OperationLogCategory.ATTENDEE_CHECKIN, null, null, null, null)))
                .containsExactly("ATTENDEE_UPDATE");
        assertThat(actions(filter(null, "报名", null, null, null)))
                .containsExactly("TDUCK_BIND_FORM");
    }

    @Test
    void searchesVisibleFieldsAndChineseAliasesWithLiteralLikeEscaping() {
        save("INVITATION_PUBLISH", "Alpha%_\\value", "MEETING_INVITATION", "target-123", true,
                "请人工复核", now());
        save("SYSTEM_SETTINGS_UPDATE", "system", "SYSTEM_SETTINGS", "settings", false, "执行失败", now());

        assertThat(actions(filter(null, "Alpha%_\\", null, null, null))).containsExactly("INVITATION_PUBLISH");
        assertThat(actions(filter(null, "发布邀请函", null, null, null))).containsExactly("INVITATION_PUBLISH");
        assertThat(actions(filter(null, "MEETING_INVITATION", null, null, null))).containsExactly("INVITATION_PUBLISH");
        assertThat(actions(filter(null, "target-123", null, null, null))).containsExactly("INVITATION_PUBLISH");
        assertThat(actions(filter(null, "复核", null, null, null))).containsExactly("INVITATION_PUBLISH");
        assertThat(actions(filter(null, "成功", null, null, null))).containsExactly("INVITATION_PUBLISH");
        assertThat(actions(filter(null, "失败", null, null, null))).containsExactly("SYSTEM_SETTINGS_UPDATE");
    }

    @Test
    void appliesDateStatusAndSnapshotFiltersWithoutMixingNewRows() {
        save("INVITATION_PUBLISH", "operator", "MEETING_INVITATION", "start", true, "OK",
                LocalDateTime.of(2026, 7, 1, 0, 0));
        save("INVITATION_ARCHIVE", "operator", "MEETING_INVITATION", "end", false, "failed",
                LocalDateTime.of(2026, 7, 1, 23, 59, 59));
        save("INVITATION_UNPUBLISH", "operator", "MEETING_INVITATION", "next", true, "OK",
                LocalDateTime.of(2026, 7, 2, 0, 0));

        OperationLogFilter dateAndFailure = new OperationLogFilter(
                null, OperationLogCategory.INVITATION, null, false,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1), false
        );
        assertThat(actions(dateAndFailure)).containsExactly("INVITATION_ARCHIVE");

        OperationLogFilter allInvitations = new OperationLogFilter(
                null, OperationLogCategory.INVITATION, null, null, null, null, false
        );
        OperationLogService.SnapshotPage firstPage = operationLogService.listFiltered(allInvitations, 0, 20, null);
        long snapshotId = firstPage.snapshotId();
        save("INVITATION_COPY", "operator", "MEETING_INVITATION", "late", true, "OK", now());

        List<String> stableActions = operationLogService.listFiltered(allInvitations, 0, 20, snapshotId)
                .page().content().stream().map(OperationLog::getAction).toList();
        assertThat(stableActions).doesNotContain("INVITATION_COPY");
        assertThat(snapshotId).isLessThan(operationLogRepository.findMaxId());
    }

    @Test
    void onlySuperAdminCanReadTheGlobalLogEndpoint() throws Exception {
        save("INVITATION_PUBLISH", "super-admin", "MEETING_INVITATION", "published", true, "OK", now());

        mockMvc.perform(get("/api/admin/operation-logs").with(user("meeting-admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/operation-logs").with(user("super-admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].category").value("INVITATION"));
    }

    @Test
    void listResponseDoesNotExposeAuditClientIpOrDetails() throws Exception {
        OperationLog log = save("INVITATION_PUBLISH", "super-admin", "MEETING_INVITATION", "published", true,
                "OK", now());
        log.setClientIp("203.0.113.42");
        log.setDetails(Map.of("rawToken", "must-not-reach-browser"));
        operationLogRepository.saveAndFlush(log);

        mockMvc.perform(get("/api/admin/operation-logs").with(user("super-admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(log.getId()))
                .andExpect(jsonPath("$.data.content[0].actor").value("super-admin"))
                .andExpect(jsonPath("$.data.content[0].action").value("INVITATION_PUBLISH"))
                .andExpect(jsonPath("$.data.content[0].category").value("INVITATION"))
                .andExpect(jsonPath("$.data.content[0].clientIp").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].details").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].updatedAt").doesNotExist());
    }

    @Test
    void invalidFilterParametersReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/admin/operation-logs")
                        .param("category", "UNKNOWN")
                        .with(user("super-admin").roles("SUPER_ADMIN")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/operation-logs")
                        .param("fromDate", "2026-07-02")
                        .param("toDate", "2026-07-01")
                        .with(user("super-admin").roles("SUPER_ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retentionDeletesOnlyLogsStrictlyOlderThanTheConfiguredBoundary() {
        LocalDateTime referenceNow = LocalDateTime.of(2026, 7, 16, 12, 0);
        OperationLog expired = save("SYSTEM_SETTINGS_UPDATE", "system", "SYSTEM_SETTINGS", "expired", true, "OK",
                referenceNow.minusDays(90).minusSeconds(1));
        OperationLog boundary = save("SYSTEM_SETTINGS_UPDATE", "system", "SYSTEM_SETTINGS", "boundary", true, "OK",
                referenceNow.minusDays(90));
        OperationLog recent = save("SYSTEM_SETTINGS_UPDATE", "system", "SYSTEM_SETTINGS", "recent", true, "OK",
                referenceNow.minusDays(89));

        assertThat(operationLogRetentionService.cleanupExpiredLogs(referenceNow)).isEqualTo(1);
        assertThat(operationLogRepository.findById(expired.getId())).isEmpty();
        assertThat(operationLogRepository.findById(boundary.getId())).isPresent();
        assertThat(operationLogRepository.findById(recent.getId())).isPresent();
    }

    @Test
    void retentionDoesNothingWhenThereAreNoLogs() {
        assertThat(operationLogRetentionService.cleanupExpiredLogs(LocalDateTime.of(2026, 7, 16, 12, 0))).isZero();
    }

    private OperationLogFilter filter(OperationLogCategory category, String keyword, Boolean success,
                                      LocalDate fromDate, LocalDate toDate) {
        return new OperationLogFilter(null, category, keyword, success, fromDate, toDate, false);
    }

    private List<String> actions(OperationLogFilter filter) {
        PageResponse<OperationLog> page = operationLogService.listFiltered(filter, 0, 50, null).page();
        return page.content().stream().map(OperationLog::getAction).toList();
    }

    private OperationLog save(String action, String actor, String targetType, String targetId,
                              boolean success, String message, LocalDateTime createdAt) {
        OperationLog log = new OperationLog();
        log.setAction(action);
        log.setActor(actor);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setSuccess(success);
        log.setMessage(message);
        log.setCreatedAt(createdAt);
        log.setUpdatedAt(createdAt);
        OperationLog saved = operationLogRepository.saveAndFlush(log);
        /*
         * @CreatedDate 会在持久化时写入当前时间。日期边界测试需要明确历史时点，
         * 因此在同一事务内回写后清理一级缓存，确保后续 Specification 读取数据库真实值。
         */
        entityManager.createQuery("update OperationLog log set log.createdAt = :createdAt, log.updatedAt = :createdAt "
                        + "where log.id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", saved.getId())
                .executeUpdate();
        entityManager.clear();
        return operationLogRepository.findById(saved.getId()).orElseThrow();
    }

    private LocalDateTime now() {
        return LocalDateTime.of(2026, 7, 16, 12, 0);
    }
}
