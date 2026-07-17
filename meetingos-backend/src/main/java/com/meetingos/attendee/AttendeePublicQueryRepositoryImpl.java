package com.meetingos.attendee;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Repository
public class AttendeePublicQueryRepositoryImpl implements AttendeePublicQueryRepository {

    private final EntityManager entityManager;

    public AttendeePublicQueryRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendeeMatchCandidate> findPublicQueryCandidates(AttendeePublicQueryCriteria criteria) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<AttendeeMatchCandidate> query = builder.createQuery(AttendeeMatchCandidate.class);
        Root<Attendee> attendee = query.from(Attendee.class);
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(builder.equal(attendee.get("meeting").get("id"), criteria.meetingId()));
        if (criteria.reviewStatus() != null) {
            predicates.add(builder.equal(attendee.get("reviewStatus"), criteria.reviewStatus()));
        }

        addTextPredicate(builder, predicates, attendee.get("attendeeCode"), criteria.attendeeCode(), criteria.exact());
        addPhonePredicate(builder, predicates, attendee.get("phone"), criteria.phone(), criteria.exact(), false);
        addPhonePredicate(builder, predicates, attendee.get("phone"), criteria.phoneLast4(), criteria.exact(), true);
        addTextPredicate(builder, predicates, attendee.get("email"), criteria.email(), criteria.exact());
        addTextPredicate(builder, predicates, attendee.get("name"), criteria.name(), criteria.exact());
        addTextPredicate(builder, predicates, attendee.get("organization"), criteria.organization(), criteria.exact());
        addTextPredicate(builder, predicates, attendee.get("title"), criteria.title(), criteria.exact());

        query.select(builder.construct(
                        AttendeeMatchCandidate.class,
                        attendee.get("id"),
                        attendee.get("reviewStatus")
                ))
                .where(predicates.toArray(Predicate[]::new))
                .orderBy(builder.asc(attendee.get("id")));
        return entityManager.createQuery(query)
                .setFirstResult(Math.max(0, criteria.offset()))
                .setMaxResults(Math.max(1, criteria.limit()))
                .getResultList();
    }

    private void addTextPredicate(CriteriaBuilder builder,
                                  List<Predicate> predicates,
                                  Expression<String> field,
                                  String value,
                                  boolean exact) {
        if (value == null) {
            return;
        }
        if (exact) {
            /*
             * MySQL 业务表使用 utf8mb4_0900_ai_ci，直接等值比较已经忽略大小写，且能使用
             * (meeting_id, phone/code/...) 索引。数据写入时已做 trim/手机号归一化。
             */
            predicates.add(builder.equal(field, value));
            return;
        }
        Expression<String> normalized = builder.lower(builder.trim(field));
        predicates.add(builder.like(normalized, containsPattern(value), '\\'));
    }

    private void addPhonePredicate(CriteriaBuilder builder,
                                   List<Predicate> predicates,
                                   Expression<String> field,
                                   String value,
                                   boolean exact,
                                   boolean suffixWhenExact) {
        if (value == null) {
            return;
        }
        if (exact && !suffixWhenExact) {
            // 手机号入库前已统一移除空格和连字符，直接等值比较才能使用会议+手机号唯一索引。
            predicates.add(builder.equal(field, value));
            return;
        }
        // 模糊/后四位模式保持原语义：手机号匹配忽略空格和连字符。
        Expression<String> normalized = builder.function(
                "replace",
                String.class,
                builder.function("replace", String.class, builder.lower(builder.trim(field)), builder.literal(" "), builder.literal("")),
                builder.literal("-"),
                builder.literal("")
        );
        String pattern = suffixWhenExact && exact ? "%" + escapeLike(value) : containsPattern(value);
        predicates.add(builder.like(normalized, pattern, '\\'));
    }

    private String containsPattern(String value) {
        return "%" + escapeLike(value) + "%";
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
