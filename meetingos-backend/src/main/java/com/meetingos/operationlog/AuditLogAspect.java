package com.meetingos.operationlog;

import com.meetingos.common.SensitiveDataMasker;
import com.meetingos.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Aspect
@Component
public class AuditLogAspect {

    private final OperationLogService operationLogService;

    public AuditLogAspect(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        HttpServletRequest request = currentRequest();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("handler", joinPoint.getSignature().toShortString());
        details.put("actor", SecurityUtils.currentUsernameOrSystem());
        if (request != null) {
            details.put("httpMethod", request.getMethod());
            details.put("path", request.getRequestURI());
            details.put("parameters", requestParameterSummary(request));
        }
        details.put("args", summarizeArgs(joinPoint.getArgs()));
        try {
            Object result = joinPoint.proceed();
            details.put("result", summarizeResult(result));
            operationLogService.record(auditLog.action(), auditLog.targetType(), true, "OK", request, details);
            return result;
        } catch (Throwable ex) {
            details.put("exceptionType", ex.getClass().getName());
            operationLogService.record(auditLog.action(), auditLog.targetType(), false, ex.getMessage(), request, details);
            throw ex;
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    private List<Object> summarizeArgs(Object[] args) {
        return Arrays.stream(args)
                .map(this::summarizeArg)
                .toList();
    }

    private Object summarizeArg(Object arg) {
        if (arg == null) {
            return null;
        }
        if (arg instanceof MultipartFile file) {
            Map<String, Object> fileSummary = new LinkedHashMap<>();
            fileSummary.put("type", "MultipartFile");
            fileSummary.put("name", file.getOriginalFilename());
            fileSummary.put("size", file.getSize());
            return fileSummary;
        }
        if (arg instanceof HttpServletRequest) {
            return "HttpServletRequest";
        }
        if (isSimpleValue(arg)) {
            return arg;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("type", arg.getClass().getSimpleName());
        return summary;
    }

    private Map<String, Object> requestParameterSummary(HttpServletRequest request) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            Object value = values == null || values.length == 0
                    ? null
                    : values.length == 1 ? values[0] : Arrays.asList(values);
            parameters.put(key, SensitiveDataMasker.maskValue(key, value));
        });
        return parameters;
    }

    private Object summarizeResult(Object result) {
        if (result == null) {
            return null;
        }
        if (result instanceof ResponseEntity<?> response) {
            return Map.of(
                    "type", "ResponseEntity",
                    "status", response.getStatusCode().value()
            );
        }
        if (isSimpleValue(result)) {
            return result;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("type", result.getClass().getSimpleName());
        return summary;
    }

    private boolean isSimpleValue(Object value) {
        return value instanceof Number
                || value instanceof Boolean
                || value instanceof Enum<?>
                || value instanceof CharSequence;
    }
}
