package com.meetingos.exporttask;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExportTaskRepository extends JpaRepository<ExportTask, Long> {

    List<ExportTask> findByMeetingIdOrderByCreatedAtDesc(Long meetingId);

    Optional<ExportTask> findFirstByMeeting_IdAndExportTypeAndStatusOrderByCreatedAtDesc(Long meetingId,
                                                                                         String exportType,
                                                                                         ExportTaskStatus status);
}
