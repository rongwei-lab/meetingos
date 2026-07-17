package com.meetingos.tduck;

import com.meetingos.common.BaseEntity;
import com.meetingos.meeting.Meeting;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(name = "tduck_pull_sync_tasks", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_tduck_pull_task_idempotency",
                columnNames = {"meeting_id", "idempotency_key"}
        )
})
public class TduckPullSyncTask extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @Column(name = "form_key", nullable = false, length = 120)
    private String formKey;

    @Column(name = "requested_limit", nullable = false)
    private int requestedLimit;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TduckPullSyncTaskStatus status = TduckPullSyncTaskStatus.PENDING;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "processed_rows", nullable = false)
    private int processedRows;

    @Column(name = "success_rows", nullable = false)
    private int successRows;

    @Column(name = "duplicate_rows", nullable = false)
    private int duplicateRows;

    @Column(name = "failed_rows", nullable = false)
    private int failedRows;

    @Column(name = "progress", nullable = false)
    private int progress;

    @Lob
    @Column(name = "failure_details", columnDefinition = "text")
    private String failureDetails;

    @Column(name = "message", length = 500)
    private String message;

    @Column(name = "requested_by", length = 64)
    private String requestedBy;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "lease_until")
    private LocalDateTime leaseUntil;

    @Column(name = "run_token", length = 64)
    private String runToken;

    public Meeting getMeeting() { return meeting; }
    public void setMeeting(Meeting meeting) { this.meeting = meeting; }
    public String getFormKey() { return formKey; }
    public void setFormKey(String formKey) { this.formKey = formKey; }
    public int getRequestedLimit() { return requestedLimit; }
    public void setRequestedLimit(int requestedLimit) { this.requestedLimit = requestedLimit; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public TduckPullSyncTaskStatus getStatus() { return status; }
    public void setStatus(TduckPullSyncTaskStatus status) { this.status = status; }
    public int getTotalRows() { return totalRows; }
    public void setTotalRows(int totalRows) { this.totalRows = totalRows; }
    public int getProcessedRows() { return processedRows; }
    public void setProcessedRows(int processedRows) { this.processedRows = processedRows; }
    public int getSuccessRows() { return successRows; }
    public void setSuccessRows(int successRows) { this.successRows = successRows; }
    public int getDuplicateRows() { return duplicateRows; }
    public void setDuplicateRows(int duplicateRows) { this.duplicateRows = duplicateRows; }
    public int getFailedRows() { return failedRows; }
    public void setFailedRows(int failedRows) { this.failedRows = failedRows; }
    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }
    public String getFailureDetails() { return failureDetails; }
    public void setFailureDetails(String failureDetails) { this.failureDetails = failureDetails; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(LocalDateTime leaseUntil) { this.leaseUntil = leaseUntil; }
    public String getRunToken() { return runToken; }
    public void setRunToken(String runToken) { this.runToken = runToken; }
}
