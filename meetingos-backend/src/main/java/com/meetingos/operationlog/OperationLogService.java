package com.meetingos.operationlog;

import com.meetingos.common.PageRequests;
import com.meetingos.common.PageResponse;
import com.meetingos.common.BusinessException;
import com.meetingos.common.SensitiveDataMasker;
import com.meetingos.security.ClientIpResolver;
import com.meetingos.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
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
        recordInternal(null, action, targetType, null, success, message, request, details);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String targetType, String targetId, boolean success, String message,
                       HttpServletRequest request, Map<String, Object> details) {
        recordInternal(null, action, targetType, targetId, success, message, request, details);
    }

    /**
     * 记录必须与调用方业务数据同生共灭的日志，例如批量导入中的房间、晚宴分配变更。
     * MANDATORY 用于阻止调用方遗漏事务边界，避免日志先于业务数据独立提交。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordWithinTransaction(String action, String targetType, String targetId, boolean success,
                                        String message, HttpServletRequest request, Map<String, Object> details) {
        recordInternal(null, action, targetType, targetId, success, message, request, details);
    }

    /**
     * 认证成功发生在 SecurityContext 建立之前，默认操作人会退化为 system。
     * 仅允许这类已经完成身份校验的内部调用显式指定操作人，不能用于接收客户端传入的任意用户名。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordForActor(String actor, String action, String targetType, String targetId, boolean success,
                               String message, HttpServletRequest request, Map<String, Object> details) {
        recordInternal(actor, action, targetType, targetId, success, message, request, details);
    }

    private void recordInternal(String actor, String action, String targetType, String targetId, boolean success,
                                String message, HttpServletRequest request, Map<String, Object> details) {
        OperationLog log = new OperationLog();
        log.setActor(StringUtils.hasText(actor) ? actor.trim() : SecurityUtils.currentUsernameOrSystem());
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setClientIp(request == null ? null : clientIpResolver.resolve(request));
        log.setSuccess(success);
        log.setMessage(truncate(message, MAX_MESSAGE_LENGTH));
        log.setDetails(details == null ? new LinkedHashMap<>() : SensitiveDataMasker.maskMap(details));
        operationLogRepository.save(log);
    }

    /**
     * 兼容已有内部调用。新接口应使用带 snapshotId 的重载，保证翻页期间不会混入新日志。
     */
    @Transactional(readOnly = true)
    public PageResponse<OperationLog> list(String action, int page, int size) {
        return listFiltered(OperationLogFilter.legacyAction(action), page, size, null).page();
    }

    @Transactional(readOnly = true)
    public SnapshotPage list(String action, int page, int size, Long requestedSnapshotId) {
        return listFiltered(OperationLogFilter.legacyAction(action), page, size, requestedSnapshotId);
    }

    @Transactional(readOnly = true)
    public SnapshotPage listFiltered(OperationLogFilter filter, int page, int size, Long requestedSnapshotId) {
        if (filter == null) {
            filter = OperationLogFilter.legacyAction(null);
        }
        long snapshotId = resolveSnapshotId(requestedSnapshotId);
        /*
         * 操作日志持续写入时，固定排序只能保证单次查询顺序，不足以保证跨页数据稳定。
         * 首次请求冻结最大 ID，后续页均限定在该上界内；createdAt 相同再按 ID 倒序。
         */
        var pageRequest = PageRequests.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt", "id")
        );
        return new SnapshotPage(
                PageResponse.from(operationLogRepository.findAll(specification(filter, snapshotId), pageRequest)),
                snapshotId
        );
    }

    private Specification<OperationLog> specification(OperationLogFilter filter, long snapshotId) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.lessThanOrEqualTo(root.<Long>get("id"), snapshotId));

            if (filter.action() != null) {
                predicates.add(containsIgnoreCase(root.<String>get("action"), filter.action(), criteriaBuilder));
            }
            if (filter.category() != null) {
                predicates.add(categoryPredicate(root, criteriaBuilder, filter.category()));
            }
            if (filter.success() != null) {
                predicates.add(criteriaBuilder.equal(root.<Boolean>get("success"), filter.success()));
            }
            if (filter.fromDate() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.<LocalDateTime>get("createdAt"),
                        filter.fromDate().atStartOfDay()
                ));
            }
            if (filter.toDate() != null) {
                predicates.add(criteriaBuilder.lessThan(
                        root.<LocalDateTime>get("createdAt"),
                        filter.toDate().plusDays(1).atStartOfDay()
                ));
            }
            if (!filter.includeDraftSaves()) {
                predicates.add(criteriaBuilder.notEqual(root.<String>get("action"), "INVITATION_DRAFT_SAVE"));
            }
            if (filter.keyword() != null) {
                predicates.add(keywordPredicate(root, criteriaBuilder, filter.keyword()));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * 仅对列表中可见字段做检索。details 和 clientIp 可能包含更多上下文，不能因为搜索而扩大暴露面。
     */
    private Predicate keywordPredicate(Root<OperationLog> root, CriteriaBuilder criteriaBuilder, String keyword) {
        List<Predicate> matches = new ArrayList<>();
        matches.add(containsIgnoreCase(root.<String>get("actor"), keyword, criteriaBuilder));
        matches.add(containsIgnoreCase(root.<String>get("action"), keyword, criteriaBuilder));
        matches.add(containsIgnoreCase(root.<String>get("targetType"), keyword, criteriaBuilder));
        matches.add(containsIgnoreCase(root.<String>get("targetId"), keyword, criteriaBuilder));
        matches.add(containsIgnoreCase(root.<String>get("message"), keyword, criteriaBuilder));

        addInIfPresent(matches, criteriaBuilder.upper(root.<String>get("action")),
                OperationLogCatalog.matchingActions(keyword));
        addInIfPresent(matches, criteriaBuilder.upper(root.<String>get("targetType")),
                OperationLogCatalog.matchingTargetTypes(keyword));
        if (OperationLogCatalog.matchesSuccess(keyword)) {
            matches.add(criteriaBuilder.isTrue(root.<Boolean>get("success")));
        }
        if (OperationLogCatalog.matchesFailure(keyword)) {
            matches.add(criteriaBuilder.isFalse(root.<Boolean>get("success")));
        }
        for (OperationLogCategory category : OperationLogCatalog.matchingCategories(keyword)) {
            matches.add(categoryPredicate(root, criteriaBuilder, category));
        }
        return criteriaBuilder.or(matches.toArray(Predicate[]::new));
    }

    private void addInIfPresent(List<Predicate> predicates, Expression<String> expression, Collection<String> values) {
        if (!values.isEmpty()) {
            predicates.add(expression.in(values));
        }
    }

    /**
     * 与 OperationLogCategory#fromAction 保持同一优先级：导入导出先拦截，再判断各业务模块。
     */
    private Predicate categoryPredicate(Root<OperationLog> root, CriteriaBuilder criteriaBuilder,
                                        OperationLogCategory category) {
        Expression<String> action = criteriaBuilder.upper(root.<String>get("action"));
        Predicate importExport = criteriaBuilder.or(
                contains(action, "IMPORT", criteriaBuilder),
                contains(action, "EXPORT", criteriaBuilder)
        );
        Predicate accountPermission = startsWithAny(action, criteriaBuilder,
                "AUTH_", "USER_", "MEETING_PERMISSION_");
        Predicate meetingConfiguration = criteriaBuilder.or(
                criteriaBuilder.and(
                        startsWith(action, "MEETING_", criteriaBuilder),
                        criteriaBuilder.not(startsWith(action, "MEETING_PERMISSION_", criteriaBuilder))
                ),
                startsWith(action, "NOTIFICATION_", criteriaBuilder)
        );
        Predicate attendeeCheckin = criteriaBuilder.or(
                startsWith(action, "ATTENDEE_", criteriaBuilder),
                contains(action, "CHECKIN", criteriaBuilder)
        );
        Predicate venueSeating = startsWithAny(action, criteriaBuilder, "ROOM_", "SEATING_", "DINNER_");
        Predicate registrationTduck = startsWith(action, "TDUCK_", criteriaBuilder);
        Predicate invitation = startsWith(action, "INVITATION_", criteriaBuilder);

        return switch (category) {
            case IMPORT_EXPORT -> importExport;
            case ACCOUNT_PERMISSION -> criteriaBuilder.and(criteriaBuilder.not(importExport), accountPermission);
            case MEETING_CONFIGURATION -> criteriaBuilder.and(criteriaBuilder.not(importExport), meetingConfiguration);
            case ATTENDEE_CHECKIN -> criteriaBuilder.and(criteriaBuilder.not(importExport), attendeeCheckin);
            case VENUE_SEATING -> criteriaBuilder.and(criteriaBuilder.not(importExport), venueSeating);
            case REGISTRATION_TDUCK -> criteriaBuilder.and(criteriaBuilder.not(importExport), registrationTduck);
            case INVITATION -> criteriaBuilder.and(criteriaBuilder.not(importExport), invitation);
            case SYSTEM -> criteriaBuilder.not(criteriaBuilder.or(
                    importExport,
                    accountPermission,
                    meetingConfiguration,
                    attendeeCheckin,
                    venueSeating,
                    registrationTduck,
                    invitation
            ));
        };
    }

    private Predicate startsWithAny(Expression<String> expression, CriteriaBuilder criteriaBuilder, String... prefixes) {
        Predicate[] predicates = new Predicate[prefixes.length];
        for (int index = 0; index < prefixes.length; index++) {
            predicates[index] = startsWith(expression, prefixes[index], criteriaBuilder);
        }
        return criteriaBuilder.or(predicates);
    }

    private Predicate startsWith(Expression<String> expression, String prefix, CriteriaBuilder criteriaBuilder) {
        return criteriaBuilder.like(expression, escapeLike(prefix) + "%", '\\');
    }

    private Predicate contains(Expression<String> expression, String value, CriteriaBuilder criteriaBuilder) {
        return criteriaBuilder.like(expression, "%" + escapeLike(value) + "%", '\\');
    }

    private Predicate containsIgnoreCase(Expression<String> expression, String value, CriteriaBuilder criteriaBuilder) {
        return contains(criteriaBuilder.lower(expression), value.toLowerCase(java.util.Locale.ROOT), criteriaBuilder);
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private long resolveSnapshotId(Long requestedSnapshotId) {
        if (requestedSnapshotId == null) {
            return operationLogRepository.findMaxId();
        }
        if (requestedSnapshotId < 0) {
            throw BusinessException.badRequest("操作日志快照 ID 不能小于 0");
        }
        return requestedSnapshotId;
    }

    public record SnapshotPage(PageResponse<OperationLog> page, long snapshotId) {
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }
}
