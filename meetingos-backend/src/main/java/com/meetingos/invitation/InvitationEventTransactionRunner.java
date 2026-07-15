package com.meetingos.invitation;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * 用独立事务提交公开访问事件，使唯一键竞争完整回滚后仍能查询胜出的幂等记录。
 */
@Component
public class InvitationEventTransactionRunner {
    private final TransactionTemplate requiresNewTransaction;

    public InvitationEventTransactionRunner(PlatformTransactionManager transactionManager) {
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public <T> T requiresNew(Supplier<T> action) {
        return requiresNewTransaction.execute(status -> action.get());
    }
}
