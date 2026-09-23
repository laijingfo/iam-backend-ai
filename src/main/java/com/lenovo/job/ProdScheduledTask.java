package com.lenovo.job;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * prod环境定时任务总览：
 * <br>
 * 1. 不受UAR周期限制，每日执行：
 *    - 12:00 同步上游应用数据；
 *    - 12:05 同步UAR全量权限数据；
 *    - 12:30 更新本地应用数据准备状态；
 *    - 15:20 同步最新用户数据并备份旧数据。
 * <br>
 * 2. 仅在UAR周期内执行：
 *    - 12:05 权限数据同步成功后处理BPO告警；
 *    - 15:20 检测用户ITCode失效、处理用户及直属经理告警、发送BPO失效邮件。
 * <br>
 * UAR周期范围取自uar_cycle_setting，起止日期均包含在执行范围内；
 * 没有有效周期时，仅跳过UAR周期告警，不影响每日同步任务。
 */
@Service
@Profile("prod")
@RequiredArgsConstructor
public class ProdScheduledTask {

    private final ScheduledTask scheduledTask;

    @Scheduled(cron = "0 0 12 * * ?", zone = "Asia/Shanghai")
    public void syncApplicationData() {
        scheduledTask.syncApplicationData();
    }

    @Scheduled(cron = "0 05 12 * * ?", zone = "Asia/Shanghai")
    public void syncUarData() {
        scheduledTask.syncUarData();
    }

    @Scheduled(cron = "0 30 12 * * ?", zone = "Asia/Shanghai")
    public void syncLocalApplicationReadyData() {
        scheduledTask.syncLocalApplicationReadyData();
    }

    @Scheduled(cron = "0 20 15 * * ?", zone = "Asia/Shanghai")
    public void syncAndBackupUserData() {
        scheduledTask.syncAndBackupUserData();
    }
}
