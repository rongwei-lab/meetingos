package com.meetingos.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

/**
 * 导入、导出文件任务共用的有界执行器。
 *
 * <p>文件解析和生成会同时消耗 CPU、内存和磁盘 IO，因此并发数保持保守。队列满时只记录
 * 拒绝并丢弃本次内存调度，数据库中的 PENDING 任务由周期恢复器重新派发，绝不占用 HTTP
 * 请求线程同步执行大文件任务。</p>
 */
@Configuration
@EnableAsync
@EnableScheduling
public class FileTaskConfig {

    private static final Logger log = LoggerFactory.getLogger(FileTaskConfig.class);

    @Bean(name = "fileTaskThreadPool")
    public ThreadPoolTaskExecutor fileTaskThreadPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("meetingos-file-task-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler((task, threadPool) -> log.warn(
                "File task executor is saturated; discarded in-memory dispatch for database recovery "
                        + "(active={}, queued={}, completed={})",
                threadPool.getActiveCount(),
                threadPool.getQueue().size(),
                threadPool.getCompletedTaskCount()
        ));
        return executor;
    }

    @Bean(name = "fileTaskExecutor")
    public AsyncTaskExecutor fileTaskExecutor(
            @Qualifier("fileTaskThreadPool") ThreadPoolTaskExecutor executor) {
        // 保留提交管理员的审计身份，并在任务结束后清理线程上下文，避免线程复用造成身份串用。
        return new DelegatingSecurityContextAsyncTaskExecutor(executor);
    }
}
