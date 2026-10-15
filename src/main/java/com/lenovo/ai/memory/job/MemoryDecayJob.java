package com.lenovo.ai.memory.job;

import com.lenovo.ai.infra.lock.RedisLockHelper;
import com.lenovo.ai.memory.store.MemoryMaintenanceStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 长期记忆衰减任务。
 *
 * <p><b>锁续租</b>：任务遍历全部活跃用户，耗时可能超过锁的初始 TTL。
 * 原实现用固定 30 分钟 TTL 且不续租，一旦超时，另一个实例会拿到锁并发执行，
 * 造成重复衰减（importance 被乘两次）。这里引入看门狗定期续租。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryDecayJob {

    private static final double DECAY_FACTOR = 0.95;
    private static final double DELETE_THRESHOLD = 0.1;

    private static final String LOCK_KEY = "ai:job:memory-decay";
    private static final long LOCK_TTL_MS = 5 * 60 * 1000L;   // 5 分钟
    /** 续租间隔：取 TTL 的 1/3，保证网络抖动也有重试机会 */
    private static final long LOCK_RENEW_INTERVAL_MS = LOCK_TTL_MS / 3;

    private final MemoryMaintenanceStore memoryMaintenanceStore;
    private final RedisLockHelper redisLockHelper;

    @Scheduled(cron = "0 0 3 * * ?")
    public void decay() {
        String token = UUID.randomUUID().toString();
        if (!redisLockHelper.tryLock(LOCK_KEY, token, LOCK_TTL_MS)) {
            log.info(">>> 记忆衰减任务已被其他实例执行，跳过");
            return;
        }

        ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "memory-decay-lock-renew");
            t.setDaemon(true);
            return t;
        });

        try {
            // ★ 启动看门狗：任务跑多久，锁就续多久
            watchdog.scheduleWithFixedDelay(
                    () -> {
                        boolean ok = redisLockHelper.renew(LOCK_KEY, token, LOCK_TTL_MS);
                        if (!ok) {
                            log.warn(">>> 记忆衰减任务锁续租失败，可能已被其他实例接管");
                        }
                    },
                    LOCK_RENEW_INTERVAL_MS, LOCK_RENEW_INTERVAL_MS, TimeUnit.MILLISECONDS);

            doDecay();
        } finally {
            watchdog.shutdownNow();
            redisLockHelper.unlock(LOCK_KEY, token);
        }
    }

    private void doDecay() {
        log.info(">>> 长期记忆衰减任务开始执行");
        long startTime = System.currentTimeMillis();
        int totalDecayed = 0;
        int totalDeleted = 0;

        try {
            Set<String> activeUsers = memoryMaintenanceStore.getAllActiveUsers();
            if (activeUsers.isEmpty()) {
                log.warn(">>> 当前没有活跃用户，跳过衰减任务");
                return;
            }

            log.info(">>> 发现 {} 个活跃用户，开始逐个衰减", activeUsers.size());

            for (String userId : activeUsers) {
                totalDecayed += safeRun(userId, "衰减",
                        uid -> memoryMaintenanceStore.decayAll(uid, DECAY_FACTOR));
                totalDeleted += safeRun(userId, "清理",
                        uid -> memoryMaintenanceStore.deleteBelow(uid, DELETE_THRESHOLD));
            }

            log.info(">>> 长期记忆衰减任务完成：处理 {} 个用户，衰减 {} 条，清理 {} 条，耗时 {}ms",
                    activeUsers.size(), totalDecayed, totalDeleted,
                    System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            log.error(">>> 长期记忆衰减任务执行异常", e);
        }
    }

    @FunctionalInterface
    private interface UserOp {
        int run(String userId);
    }

    private int safeRun(String userId, String opName, UserOp op) {
        try {
            return op.run(userId);
        } catch (Exception e) {
            log.error(">>> 对用户 {} 执行 {} 失败", userId, opName, e);
            return 0;
        }
    }
}
