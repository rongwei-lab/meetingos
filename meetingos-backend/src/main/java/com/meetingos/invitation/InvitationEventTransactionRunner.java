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
    private final TransactionTemplate readOnlyTransaction;

    public InvitationEventTransactionRunner(PlatformTransactionManager transactionManager) {
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readOnlyTransaction.setReadOnly(true);
    }

    public <T> T requiresNew(Supplier<T> action) {
        return requiresNewTransaction.execute(status -> action.get());
    }

    public <T> T readOnly(Supplier<T> action) {
        return readOnlyTransaction.execute(status -> action.get());
    }
}
