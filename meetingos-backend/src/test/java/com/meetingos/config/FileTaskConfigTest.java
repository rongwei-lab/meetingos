package com.meetingos.config;

import com.meetingos.security.CurrentUser;
import com.meetingos.security.Role;
import com.meetingos.security.SecurityUtils;
import com.meetingos.tduck.TduckPullSyncTaskWorker;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileTaskConfigTest {

    @Test
    void saturatedPoolDiscardsDispatchInsteadOfRunningOnSubmittingThread() throws Exception {
        FileTaskConfig config = new FileTaskConfig();
        var threadPool = config.fileTaskThreadPool();
        threadPool.initialize();
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        Runnable blocker = () -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        };

        try {
            threadPool.execute(blocker);
            threadPool.execute(blocker);
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            for (int index = 0; index < 32; index++) {
                threadPool.execute(blocker);
            }
            assertThat(threadPool.getThreadPoolExecutor().getQueue()).hasSize(32);

            AtomicReference<Thread> executedBy = new AtomicReference<>();
            threadPool.execute(() -> executedBy.set(Thread.currentThread()));

            assertThat(executedBy.get())
                    .as("饱和时不能回退到提交请求线程执行")
                    .isNull();
        } finally {
            release.countDown();
            threadPool.shutdown();
        }
    }

    @Test
    void propagatesSubmittingUserAndClearsContextBetweenTasks() throws Exception {
        FileTaskConfig config = new FileTaskConfig();
        var threadPool = config.fileTaskThreadPool();
        threadPool.initialize();
        var executor = config.fileTaskExecutor(threadPool);

        try {
            CurrentUser user = new CurrentUser(7L, "meeting-admin", "会议管理员", Set.of(Role.MEETING_ADMIN));
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(user, "n/a", List.of())
            );

            assertThat(executor.submit(SecurityUtils::currentUsernameOrSystem).get(5, TimeUnit.SECONDS))
                    .isEqualTo("meeting-admin");

            SecurityContextHolder.clearContext();
            assertThat(executor.submit(SecurityUtils::currentUsernameOrSystem).get(5, TimeUnit.SECONDS))
                    .isEqualTo("system");
        } finally {
            SecurityContextHolder.clearContext();
            threadPool.shutdown();
        }
    }

    @Test
    void tduckPullSyncUsesDedicatedBoundedExecutor() throws NoSuchMethodException {
        FileTaskConfig config = new FileTaskConfig();
        ThreadPoolTaskExecutor fileTasks = config.fileTaskThreadPool();
        ThreadPoolTaskExecutor tduckPull = config.tduckPullSyncThreadPool();

        assertThat(tduckPull).isNotSameAs(fileTasks);
        assertThat(tduckPull.getCorePoolSize()).isEqualTo(2);
        assertThat(tduckPull.getMaxPoolSize()).isEqualTo(2);
        assertThat(tduckPull.getQueueCapacity()).isEqualTo(32);
        assertThat(tduckPull.getThreadNamePrefix()).isEqualTo("meetingos-tduck-pull-");

        Async async = TduckPullSyncTaskWorker.class
                .getMethod("process", Long.class)
                .getAnnotation(Async.class);
        assertThat(async).isNotNull();
        assertThat(async.value()).isEqualTo("tduckPullSyncExecutor");
    }

    @Test
    void saturatedTduckPullSyncPoolRejectsDispatchSoReservationCanBeReleased() throws Exception {
        FileTaskConfig config = new FileTaskConfig();
        ThreadPoolTaskExecutor threadPool = config.tduckPullSyncThreadPool();
        threadPool.initialize();
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        Runnable blocker = () -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        };

        try {
            threadPool.execute(blocker);
            threadPool.execute(blocker);
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            for (int index = 0; index < 32; index++) {
                threadPool.execute(blocker);
            }

            assertThatThrownBy(() -> threadPool.execute(() -> { }))
                    .as("拒绝必须同步反馈给调度服务，不能静默保留 taskId 占位")
                    .isInstanceOf(RuntimeException.class);
        } finally {
            release.countDown();
            threadPool.shutdown();
        }
    }
}
