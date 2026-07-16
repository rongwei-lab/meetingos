package com.meetingos.operationlog;

import com.meetingos.common.BusinessException;
import com.meetingos.invitation.InvitationAdminController;
import com.meetingos.invitation.InvitationApiModels.SaveDraftRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditLogAspectTest {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void draftSaveSuccessCreatesOperationLog() throws Throwable {
        OperationLogService operationLogService = mock(OperationLogService.class);
        AuditLogAspect aspect = new AuditLogAspect(operationLogService);
        MockHttpServletRequest request = request();
        ProceedingJoinPoint joinPoint = joinPointReturning("saved");

        Object result = aspect.around(joinPoint, invitationDraftAuditLog());

        assertThat(result).isEqualTo("saved");
        verify(operationLogService).record(
                eq("INVITATION_DRAFT_SAVE"),
                eq("MEETING_INVITATION"),
                eq(true),
                eq("OK"),
                same(request),
                org.mockito.ArgumentMatchers.any(Map.class)
        );
    }

    @Test
    void repeatedDraftFailuresEachCreateOperationLog() throws Throwable {
        OperationLogService operationLogService = mock(OperationLogService.class);
        AuditLogAspect aspect = new AuditLogAspect(operationLogService);
        MockHttpServletRequest request = request();
        ProceedingJoinPoint joinPoint = joinPointThrowing(BusinessException.conflict("草稿版本冲突"));

        assertThatThrownBy(() -> aspect.around(joinPoint, invitationDraftAuditLog()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> aspect.around(joinPoint, invitationDraftAuditLog()))
                .isInstanceOf(BusinessException.class);

        verify(operationLogService, times(2)).record(
                eq("INVITATION_DRAFT_SAVE"),
                eq("MEETING_INVITATION"),
                eq(false),
                anyString(),
                same(request),
                org.mockito.ArgumentMatchers.any(Map.class)
        );
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/admin/meetings/1/invitations/2/draft");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return request;
    }

    private ProceedingJoinPoint joinPointReturning(Object result) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.toShortString()).thenReturn("InvitationAdminController.saveDraft(..)");
        when(joinPoint.getArgs()).thenReturn(new Object[]{1L, 2L});
        when(joinPoint.proceed()).thenReturn(result);
        return joinPoint;
    }

    private ProceedingJoinPoint joinPointThrowing(Throwable exception) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.toShortString()).thenReturn("InvitationAdminController.saveDraft(..)");
        when(joinPoint.getArgs()).thenReturn(new Object[]{1L, 2L});
        doThrow(exception).when(joinPoint).proceed();
        return joinPoint;
    }

    private AuditLog invitationDraftAuditLog() {
        try {
            Method method = InvitationAdminController.class.getMethod(
                    "saveDraft", Long.class, Long.class, SaveDraftRequest.class
            );
            return method.getAnnotation(AuditLog.class);
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(exception);
        }
    }

}
