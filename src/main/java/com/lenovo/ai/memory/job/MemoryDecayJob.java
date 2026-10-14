package com.lenovo.ai.memory.job;

import com.lenovo.ai.memory.store.MemoryMaintenanceStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryDecayJob {

    private static final double DECAY_FACTOR = 0.95;
    private static final double DELETE_THRESHOLD = 0.1;

    private final MemoryMaintenanceStore memoryMaintenanceStore;

    @Scheduled(cron = "0 0 3 * * ?")
    public void decay() {
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