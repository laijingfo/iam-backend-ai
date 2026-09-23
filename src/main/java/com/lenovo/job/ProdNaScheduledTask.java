package com.lenovo.job;

import com.lenovo.service.NaSensitiveAccessAlertService;
import com.lenovo.service.NaSensitiveAccessSyncStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 和 prod 错峰执行，慢 10 分钟
 *
 * prod-na 环境定时任务总览：
 * <br>
 * 1. 不受UAR周期限制，每日执行：
 *    - 12:10 同步上游应用数据；
 *    - 12:15 同步UAR全量权限数据，并记录当日同步成功状态；
 *    - 12:40 更新本地应用数据准备状态；
 *    - 15:30 同步最新用户数据并备份旧数据；
 *    - 15:30 处理NA-Sensitive权限告警。
 * <br>
 * 2. 仅在UAR周期内执行：
 *    - 12:15 权限数据同步成功后处理BPO告警；
 *    - 15:30 检测用户ITCode失效、处理用户及直属经理告警、发送BPO失效邮件。
 * <br>
 * UAR周期范围取自uar_cycle_setting，起止日期均包含在执行范围内；
 * 没有有效周期时，仅跳过UAR周期告警，不影响每日同步和NA-Sensitive权限告警。
 */
@Slf4j
@Service
@Profile("prod-na")
@RequiredArgsConstructor
public class ProdNaScheduledTask {

    private final ScheduledTask scheduledTask;
    private final NaSensitiveAccessSyncStatus syncStatus;

    @Scheduled(cron = "0 10 12 * * ?", zone = "Asia/Shanghai")
    public void syncApplicationData() {
        scheduledTask.syncApplicationData();
    }

    @Scheduled(cron = "0 15 12 * * ?", zone = "Asia/Shanghai")
    public void syncUarData() {
        scheduledTask.syncUarData(syncStatus::markSuccessful);
    }

    @Scheduled(cron = "0 40 12 * * ?", zone = "Asia/Shanghai")
    public void syncLocalApplicationReadyData() {
        scheduledTask.syncLocalApplicationReadyData();
    }

    @Scheduled(cron = "0 30 15 * * ?", zone = "Asia/Shanghai")
    public void syncUserDataAndHandleAlerts() {
        scheduledTask.syncAndBackupUserData(() -> {
            // 如果今天已经成功同步过权限数据 或者 今天是 2026-09-15日可以正常调用
            if (syncStatus.isSuccessfulToday() || "2026-09-15".equals(LocalDate.now().toString())) {
                scheduledTask.handleNaSensitiveAccessAlerts();
            } else {
                log.error("Skip NA-sensitive access alert task: today's permission data sync was not successful");
            }
        });
    }


}
