package com.meetingos.operationlog;

import com.meetingos.common.PageRequests;
import com.meetingos.common.PageResponse;
import com.meetingos.common.SensitiveDataMasker;
import com.meetingos.security.ClientIpResolver;
import com.meetingos.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class OperationLogService {

    private static final int MAX_MESSAGE_LENGTH = 500;

    private final OperationLogRepository operationLogRepository;
    private final ClientIpResolver clientIpResolver;

    public OperationLogService(OperationLogRepository operationLogRepository,
                               ClientIpResolver clientIpResolver) {
        this.operationLogRepository = operationLogRepository;
        this.clientIpResolver = clientIpResolver;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String targetType, boolean success, String message,
                       HttpServletRequest request, Map<String, Object> details) {
        record(action, targetType, null, success, message, request, details);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String targetType, String targetId, boolean success, String message,
                       HttpServletRequest request, Map<String, Object> details) {
        OperationLog log = new OperationLog();
        log.setActor(SecurityUtils.currentUsernameOrSystem());
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setClientIp(request == null ? null : clientIpResolver.resolve(request));
        log.setSuccess(success);
        log.setMessage(truncate(message, MAX_MESSAGE_LENGTH));
        log.setDetails(details == null ? new LinkedHashMap<>() : SensitiveDataMasker.maskMap(details));
        operationLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public PageResponse<OperationLog> list(String action, int page, int size) {
        var pageRequest = PageRequests.of(page, size);
        if (action == null || action.isBlank()) {
            return PageResponse.from(operationLogRepository.findAll(pageRequest));
        }
        return PageResponse.from(operationLogRepository.findByActionContainingIgnoreCase(action, pageRequest));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }
}
