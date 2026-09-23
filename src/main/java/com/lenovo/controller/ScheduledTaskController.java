package com.lenovo.controller;

import com.lenovo.job.ScheduledTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 手动执行任务
 */
@RestController
@RequestMapping("/task")
@RequiredArgsConstructor
@Slf4j
public class ScheduledTaskController {
    
    private final ScheduledTask scheduledTask;
    
    @GetMapping("/syncAndBackupUserData")
    public String syncAndBackupUserData() {
        log.info("【手动触发】开始执行同步用户数据任务");
        try {
            scheduledTask.syncAndBackupUserData();
            return "同步用户数据任务执行成功";
        } catch (Exception e) {
            log.error("【手动触发】同步用户数据任务执行失败", e);
            return "同步用户数据任务执行失败: " + e.getMessage();
        }
    }

    
    @GetMapping("/syncApplicationData")
    public String syncApplicationData() {
        log.info("【手动触发】开始执行同步应用数据任务");
        try {
            scheduledTask.syncApplicationData();
            return "同步应用数据任务执行成功";
        } catch (Exception e) {
            log.error("【手动触发】同步应用数据任务执行失败", e);
            return "同步应用数据任务执行失败: " + e.getMessage();
        }
    }
    
    @GetMapping("/syncUarData")
    public String syncUarData() {
        log.info("【手动触发】开始执行同步UAR全量数据任务");
        try {
            scheduledTask.syncUarData();
            return "同步UAR全量数据任务执行成功";
        } catch (Exception e) {
            log.error("【手动触发】同步UAR全量数据任务执行失败", e);
            return "同步UAR全量数据任务执行失败: " + e.getMessage();
        }
    }
    
    @GetMapping("/syncLocalApplicationReadyData")
    public String syncLocalApplicationReadyData() {
        log.info("【手动触发】开始执行更新本地应用数据准备状态任务");
        try {
            scheduledTask.syncLocalApplicationReadyData();
            return "更新本地应用数据准备状态任务执行成功";
        } catch (Exception e) {
            log.error("【手动触发】更新本地应用数据准备状态任务执行失败", e);
            return "更新本地应用数据准备状态任务执行失败: " + e.getMessage();
        }
    }

    @GetMapping("/handleNaSensitiveAccessAlerts")
    public String handleNaSensitiveAccessAlerts() {
        log.info("【手动触发】开始执行NA敏感访问告警处理任务");
        try {
            scheduledTask.handleNaSensitiveAccessAlerts();
            return "NA敏感访问告警处理任务执行成功";
        } catch (Exception e) {
            log.error("【手动触发】NA敏感访问告警处理任务执行失败", e);
            return "NA敏感访问告警处理任务执行失败: " + e.getMessage();
        }
    }
}
