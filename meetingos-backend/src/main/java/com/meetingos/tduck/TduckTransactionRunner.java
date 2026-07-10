package com.meetingos.tduck;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * 为 Webhook 提供独立事务边界，使调用方能在提交失败、事务已完整回滚之后处理唯一键竞争。
 * REQUIRES_NEW 是这里的必要条件：若加入调用方事务，真正提交会晚于本方法返回，幂等冲突将无法捕获。
 */
@Component
public class TduckTransactionRunner {

    private final TransactionTemplate requiresNewTransaction;

    public TduckTransactionRunner(PlatformTransactionManager transactionManager) {
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public <T> T requiresNew(Supplier<T> action) {
        return requiresNewTransaction.execute(status -> action.get());
    }
}
