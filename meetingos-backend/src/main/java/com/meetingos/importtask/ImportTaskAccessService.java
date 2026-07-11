package com.meetingos.importtask;

import com.meetingos.permission.MeetingAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("importTaskAccess")
public class ImportTaskAccessService {

    private final ImportTaskRepository repository;
    private final MeetingAccessService meetingAccessService;

    public ImportTaskAccessService(ImportTaskRepository repository, MeetingAccessService meetingAccessService) {
        this.repository = repository;
        this.meetingAccessService = meetingAccessService;
    }

    @Transactional(readOnly = true)
    public boolean canCreate(Long meetingId, String type) {
        try {
            return allowed(meetingId, ImportTaskType.from(type));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    @Transactional(readOnly = true)
    public boolean canList(Long meetingId) {
        return meetingAccessService.canManage(meetingId) || meetingAccessService.canManageRooms(meetingId);
    }

    @Transactional(readOnly = true)
    public boolean canAccess(Long taskId, Long meetingId) {
        return repository.findByIdAndMeetingId(taskId, meetingId)
                .map(task -> allowed(meetingId, ImportTaskType.from(task.getImportType())))
                .orElse(false);
    }

    private boolean allowed(Long meetingId, ImportTaskType type) {
        return switch (type) {
            case ROOMS, ROOM_ASSIGNMENTS -> meetingAccessService.canManageRooms(meetingId);
            case ATTENDEES, DINNER -> meetingAccessService.canManage(meetingId);
        };
    }
}
