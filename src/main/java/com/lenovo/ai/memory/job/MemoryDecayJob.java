package com.lenovo.ai.memory.job;

import com.lenovo.ai.memory.store.MemoryMaintenanceStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 长期记忆衰减任务
 *
 * @author laifo
 * @date 2026-09-30
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryDecayJob {

    private final MemoryMaintenanceStore memoryMaintenanceStore;

    @Scheduled(cron = "0 0 3 * * ?")
    public void decay() {
        log.info(">>> 长期记忆衰减任务开始执行");

        long startTime = System.currentTimeMillis();
        int activeUserCount = 0;
        int totalMemoryDecayed = 0;
        int totalMemoryDeleted = 0;

        try {
            Set<String> activeUsers = memoryMaintenanceStore.getAllActiveUsers();
            activeUserCount = activeUsers.size();

            if (activeUsers.isEmpty()) {
                log.warn(">>> 当前没有活跃用户，跳过衰减任务");
                return;
            }

            log.info(">>> 发现 {} 个活跃用户，开始逐个衰减", activeUserCount);

            for (String userId : activeUsers) {
                try {
                    memoryMaintenanceStore.decayAll(userId, 0.95);
                    totalMemoryDecayed++;
                } catch (Exception e) {
                    log.error(">>> 对用户 {} 执行衰减失败", userId, e);
                }

                try {
                    memoryMaintenanceStore.deleteBelow(userId, 0.1);
                    totalMemoryDeleted++;
                } catch (Exception e) {
                    log.error(">>> 对用户 {} 清理低重要性记忆失败", userId, e);
                }
            }

            long elapsed = System.currentTimeMillis() - startTime;
            log.info(">>> 长期记忆衰减任务完成：处理 {} 个用户，衰减 {} 次记忆，清理 {} 条，耗时 {}ms",
                    activeUserCount, totalMemoryDecayed, totalMemoryDeleted, elapsed);

        } catch (Exception e) {
            log.error(">>> 长期记忆衰减任务执行异常", e);
        }
    }
}