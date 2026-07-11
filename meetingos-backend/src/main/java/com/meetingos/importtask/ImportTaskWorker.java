package com.meetingos.importtask;

import com.meetingos.attendee.AttendeeService;
import com.meetingos.common.BusinessException;
import com.meetingos.common.ImportProgressListener;
import com.meetingos.common.ImportResult;
import com.meetingos.common.TableData;
import com.meetingos.common.TableFileReader;
import com.meetingos.dinner.DinnerService;
import com.meetingos.room.RoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
public class ImportTaskWorker {

    private static final Logger log = LoggerFactory.getLogger(ImportTaskWorker.class);
    private static final int PROGRESS_UPDATE_INTERVAL = 25;

    private final ImportTaskStateService stateService;
    private final ImportTaskFileStore fileStore;
    private final AttendeeService attendeeService;
    private final RoomService roomService;
    private final DinnerService dinnerService;

    public ImportTaskWorker(ImportTaskStateService stateService,
                            ImportTaskFileStore fileStore,
                            AttendeeService attendeeService,
                            RoomService roomService,
                            DinnerService dinnerService) {
        this.stateService = stateService;
        this.fileStore = fileStore;
        this.attendeeService = attendeeService;
        this.roomService = roomService;
        this.dinnerService = dinnerService;
    }

    @Async("fileTaskExecutor")
    public void processCreated(Long taskId) {
        ImportTaskWorkItem item = stateService.claimCreated(taskId);
        if (item == null) {
            return;
        }
        if (item.preflight()) {
            preflight(item);
        } else {
            runImport(item);
        }
    }

    @Async("fileTaskExecutor")
    public void processCommitted(Long taskId) {
        ImportTaskWorkItem item = stateService.claimCommitted(taskId);
        if (item != null) {
            runImport(item);
        }
    }

    private void preflight(ImportTaskWorkItem item) {
        try {
            TableData data = readInput(item);
            if (data.rows().isEmpty()) {
                throw BusinessException.badRequest("导入文件没有可导入的数据行");
            }
            stateService.previewReady(item.id(), data.rows().size());
        } catch (RuntimeException ex) {
            fail(item.id(), ex);
        }
    }

    private void runImport(ImportTaskWorkItem item) {
        try {
            TableData data = readInput(item);
            if (data.rows().isEmpty()) {
                throw BusinessException.badRequest("导入文件没有可导入的数据行");
            }
            int totalRows = data.rows().size();
            ImportProgressListener progress = (processed, imported, failed) -> {
                if (processed == totalRows || processed % PROGRESS_UPDATE_INTERVAL == 0) {
                    stateService.updateProgress(item.id(), totalRows, processed, imported, failed);
                }
            };
            ImportResult result = switch (item.type()) {
                case ATTENDEES -> attendeeService.importRows(item.meetingId(), data.rows(), progress);
                case ROOMS -> roomService.importRoomRows(item.meetingId(), data.rows(), progress);
                case ROOM_ASSIGNMENTS -> roomService.importAssignmentRows(item.meetingId(), data.rows(), progress);
                case DINNER -> dinnerService.importAssignmentRows(item.meetingId(), data.rows(), progress);
            };

            String failurePath = null;
            String message = result.message();
            if (!result.failedRows().isEmpty()) {
                try {
                    failurePath = fileStore.failurePath(item.id());
                    Path failureTarget = fileStore.createFailureTarget(failurePath);
                    ImportFailureWorkbook.write(failureTarget, data.headers(), data.rows(), result.failedRows());
                } catch (RuntimeException failureFileError) {
                    log.warn("Import failure workbook generation failed for task={}", item.id(), failureFileError);
                    failurePath = null;
                    message = result.message() + "；失败明细文件生成失败，请查看任务消息";
                }
            }
            stateService.completed(
                    item.id(),
                    result.totalRows(),
                    result.importedRows(),
                    result.failedRows().size(),
                    message,
                    failurePath
            );
        } catch (RuntimeException ex) {
            fail(item.id(), ex);
        }
    }

    private TableData readInput(ImportTaskWorkItem item) {
        Path input = fileStore.resolve(item.inputFilePath());
        if (!java.nio.file.Files.isRegularFile(input)) {
            throw BusinessException.notFound("导入源文件不存在或已清理");
        }
        return TableFileReader.read(input, item.type().schema());
    }

    private void fail(Long taskId, RuntimeException ex) {
        String message = ex instanceof BusinessException ? ex.getMessage() : "导入任务执行失败，请查看服务端日志";
        if (!(ex instanceof BusinessException)) {
            log.error("Import task failed, task={}", taskId, ex);
        }
        stateService.failed(taskId, message);
    }
}
