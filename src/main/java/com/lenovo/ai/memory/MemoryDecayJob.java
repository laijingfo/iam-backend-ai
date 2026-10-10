package com.lenovo.ai.memory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-09-30 10:04
 * @project iam-backend-ai
 * @description
 * 长期记忆衰减任务 - 按固定周期对用户的记忆重要性进行衰减计算
 * <p>
 * 功能说明：
 * 1. 每日凌晨 3 点执行一次记忆衰减任务
 * 2. 遍历所有活跃用户，对其记忆重要性进行衰减（乘以 0.95）
 * 3. 同时清理过期记忆和低于阈值的低重要性记忆
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryDecayJob {

    private final MemoryStore memoryStore;

    /**
     * 每日凌晨 3 点执行一次记忆衰减任务
     * 衰减因子：0.95（每次衰减 5%）
     * 清理阈值：重要性低于 0.1 的记忆将被删除
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void decay() {
        log.info(">>> 长期记忆衰减任务开始执行");
        
        long startTime = System.currentTimeMillis();
        int activeUserCount = 0;
        int totalMemoryDecayed = 0;
        int totalMemoryDeleted = 0;
        
        try {
            // 步骤 1: 获取所有活跃用户
            java.util.Set<String> activeUsers = memoryStore.getAllActiveUsers();
            activeUserCount = activeUsers.size();
            
            if (activeUsers.isEmpty()) {
                log.warn(">>> 当前没有活跃用户，跳过衰减任务");
                return;
            }
            
            log.info(">>> 发现 {} 个活跃用户，开始逐个衰减", activeUserCount);
            
            // 步骤 2: 对每个活跃用户执行衰减
            for (String userId : activeUsers) {
                try {
                    // 衰减该用户的所有记忆（重要性乘以 0.95）
                    memoryStore.decayAll(userId, 0.95);
                    totalMemoryDecayed++;
                } catch (Exception e) {
                    log.error(">>> 对用户 {} 执行衰减失败", userId, e);
                }
                
                try {
                    // 清理重要性低于阈值的记忆
                    memoryStore.deleteBelow(userId, 0.1);
                    totalMemoryDeleted++;
                } catch (Exception e) {
                    log.error(">>> 对用户 {} 清理低重要性记忆失败", userId, e);
                }
            }
            
            long elapsed = System.currentTimeMillis() - startTime;
            log.info(">>> 长期记忆衰减任务完成：处理 {} 个用户，衰减 {} 次记忆，清理 {} 条低重要性记忆，耗时 {}ms",
                    activeUserCount, totalMemoryDecayed, totalMemoryDeleted, elapsed);
                    
        } catch (UnsupportedOperationException e) {
            // 批量查询活跃用户功能暂未实现，记录警告但继续运行
            log.warn(">>> 批量查询活跃用户功能未实现，暂不执行自动衰减：{}", e.getMessage());
        } catch (Exception e) {
            log.error(">>> 长期记忆衰减任务执行异常", e);
        }
    }
}
