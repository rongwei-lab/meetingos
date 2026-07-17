package com.meetingos.common;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * 为同步旧接口和异步任务提供一致的逐行短事务边界。
 */
@Component
public class ImportRowTransactionRunner {

    private final TransactionTemplate transactionTemplate;

    public ImportRowTransactionRunner(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    private ImportRowTransactionRunner() {
        this.transactionTemplate = null;
    }

    /** 仅供不启动 Spring 事务的纯单元测试构造服务。 */
    public static ImportRowTransactionRunner direct() {
        return new ImportRowTransactionRunner();
    }

    public <T> T execute(Supplier<T> action) {
        if (transactionTemplate == null) {
            return action.get();
        }
        return transactionTemplate.execute(status -> action.get());
    }

    /**
     * 批次失败后需要依赖事务回滚再逐行重试；纯单元测试的 direct 模式不具备该能力。
     */
    public boolean supportsBatchTransactions() {
        return transactionTemplate != null;
    }
}
