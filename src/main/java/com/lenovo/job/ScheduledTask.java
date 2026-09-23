package com.lenovo.job;

import com.lenovo.constant.UarAlertType;
import com.lenovo.dto.UarAlertRequest;
import com.lenovo.mapper.AlertDashboardMapper;
import com.lenovo.mapper.AutoMailSendLineManagerTempMapper;
import com.lenovo.service.*;
import com.lenovo.service.impl.IEmailSendService;
import com.lenovo.strategy.CheckStrategy;
import com.lenovo.strategy.SendEmailStrategyType;
import com.lenovo.strategy.factory.CheckStrategyFactory;
import com.lenovo.util.NodeIdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;


/**
 * 定时任务执行规则汇总：
 * <br>
 * 定时任务公共执行器。这里只保留加锁和业务执行能力，具体调度入口由
 * prod、prod-na各自的环境调度类提供。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledTask
{
    private final NodeIdUtil nodeIdUtil;
    private final IEmailSendService emailSendServiceAsync;
    private final AutoMailSendLineManagerTempMapper autoMailSendLineManagerTempMapper;
    private final RedissonClient redissonClient;
    private final LenovoService lenovoService;
    private final ItsApplicationAccessDataService splunkDataSourceService;
    private final NotifyService notifyService;
    private final UarCycleSettingService uarCycleSettingService;
    private final ItsApplicationService itsApplicationService;
    private final AlertDashboardService alertDashboardService;
    private final SyncItsApplicationService syncItsApplicationService;
    private final NaSensitiveAccessAlertService naSensitiveAccessAlertService;

    // 定义分布式锁key
    private static final String PREFIX_LOCK_KEY = "lock:job:";
    private static final String SYNC_UAR_DATA                       = PREFIX_LOCK_KEY + "SyncUarData";
    private static final String SYNC_AND_BACKUP_USER_DATA           = PREFIX_LOCK_KEY + "SyncAndBackupUserData";
    private static final String SYNC_APPLICATION_DATA               = PREFIX_LOCK_KEY + "SyncApplicationData";
    private static final String SYNC_LOCAL_APPLICATION_READY_DATA   = PREFIX_LOCK_KEY + "SyncLocalApplicationReadyData";
    private static final String HANDLE_ALERT_FOR_BPO                = PREFIX_LOCK_KEY + "HandleAlertForBPO";
    private static final String HANDLE_ALERT_FOR_USER_AND_LM        = PREFIX_LOCK_KEY + "HandleAlertForUserAndLM";
    private static final String NA_SENSITIVE_ACCESS_ALERT_LOCK      = PREFIX_LOCK_KEY + "NaSensitiveAccessAlert";




    /**
     *   同步上游[应用]数据到本系统
     *   任务需要1-3分钟，5分钟内完成
     */
    public void syncApplicationData() {
        executeTaskWithLock(
                SYNC_APPLICATION_DATA,
                syncItsApplicationService::syncSplunkApplicationData
        );
    }


    /**
     *   1. 每日 - UAR系统每日定时拿[全量数据]
     *   2. UAR周期内 - 处理BPO告警
     *   任务需要10分钟左右，15分钟内完成
     */
    public void syncUarData() {
        syncUarData(() -> { });
    }

    /**
     * 同步权限数据，并且只在实际获得锁、同步成功的节点执行成功回调。
     */
    public void syncUarData(Runnable afterSyncSuccessful) {
        executeTaskWithLock(
                SYNC_UAR_DATA,
                () -> {
                    // 1. 同步全量数据
                    splunkDataSourceService.syncFullData(0);
                    afterSyncSuccessful.run();

                    // UAR周期内 - 处理BPO告警
                    executeTaskWithinUarCycleWithLock(
                            HANDLE_ALERT_FOR_BPO,
                            alertDashboardService::handleBpoInvalidAlert
                    );
                }
        );
    }


    /**
     *   更新本地应用[数据准备状态]
     *   任务1分钟内完成
     */
    public void syncLocalApplicationReadyData() {
        executeTaskWithLock(
                SYNC_LOCAL_APPLICATION_READY_DATA,
                itsApplicationService::syncLocalApplicationReadyData
        );
    }


    private final CheckStrategyFactory strategyFactory;
    private final AlertDashboardMapper alertDashboardMapper;
    /**
     *   1. 每日 - 同步最新的[用户]数据，并且对旧数据备份
     *   2. UAR周期内 - 检测[ItCode]失效
     *   3. UAR周期内 - 处理[ITCode，LM]告警
     *   4. UAR周期内 - 发送BPO失效邮件给 S&A、AppTeam
     */
    public void syncAndBackupUserData() {
        syncAndBackupUserData(() -> { });
    }

    /**
     * 同步用户数据，并且只在实际获得锁、同步成功的节点执行成功回调和周期告警。
     */
    public void syncAndBackupUserData(Runnable afterSyncSuccessful) {
        executeTaskWithLock(
                SYNC_AND_BACKUP_USER_DATA,
                ()-> {
                    // 1. 同步最新的[用户]数据，并且对旧数据备份
                    log.info("Step 1: Sync UPP");
                    lenovoService.syncUserProfileData();
                    afterSyncSuccessful.run();

                    executeTaskWithinUarCycleWithLock(
                            HANDLE_ALERT_FOR_USER_AND_LM,
                            () -> {
                                // 2. 检测[ItCode]失效
                                log.info("Step 2: Detection ItCode");
                                alertDashboardService.insertUarAlertData();

                                // 3. 处理[ITCode，LM]告警
                                log.info("Step 3: Handle Alert");
                                alertDashboardService.handleUserItCodeAlert();
                                alertDashboardService.handleLineManagerItCodeAlert();

                                // 4. 发BPO失效的邮件给 S&A、AppTeam
                                log.info("Step 4: Send Task Mail");
                                this.sendExpiredBpoToOperator();
                            }
                    );
                }
        );
    }

    /**
     * CV了一份,回头改掉用
     * @see com.lenovo.controller.AlertDashboardController#handleExpiredBpoItCode()
     */
    private void sendExpiredBpoToOperator() {
        try
        {
            UarAlertRequest uarAlertRequest = new UarAlertRequest();
            uarAlertRequest.setAlertType(UarAlertType.BPO_IT_CODE_INVALID.getCodeValue());
            uarAlertRequest.setUpdateBy("JOB");
            uarAlertRequest.setItCodeOfUser("JOB");
            uarAlertRequest.setUpdateTime(LocalDateTime.now());
            CheckStrategy<UarAlertRequest, ResponseEntity<?>> strategy = strategyFactory.getStrategy(SendEmailStrategyType.EXPIRED_BPO_CYCLE_TYPE);
            strategy.handle(null, uarAlertRequest);
            alertDashboardMapper.updateByAlertType(uarAlertRequest);
        }
        catch (Exception e)
        {
            log.error("【Task - sendExpiredBpoToOperator】", e);
        }
    }

    /**
     * 处理PROD-NA敏感访问告警
     */
    public void handleNaSensitiveAccessAlerts() {
        executeTaskWithLock(
                NA_SENSITIVE_ACCESS_ALERT_LOCK,
                () -> {
                    log.info("Detect NA-sensitive access alerts");
                    int created = naSensitiveAccessAlertService.createNewAlerts();
                    int resolved = naSensitiveAccessAlertService.resolveMissingAlerts();
                    int submitted = 0;//naSensitiveAccessAlertService.sendPendingAlertEmails();
                    log.info("NA-sensitive access alert task completed: created={}, resolved={}, mailTasksSubmitted={}",
                            created, resolved, submitted);
                }
        );
    }


    /**
     * 公共方法，用于执行分布式锁任务
     *
     * @param lockName 分布式锁名称
     * @param task 要执行的任务
     */
    public void executeTaskWithLock(String lockName, Runnable task) {

        RLock lock = redissonClient.getLock(lockName);
        boolean locked = false;

        try {
            locked = lock.tryLock(0, -1, TimeUnit.SECONDS);
            if (!locked) {
                // 【任务跳过】锁被占用，跳过执行。
                log.info("[Task Skip] The lock is occupied, skip execution. Lock name: {}", lockName);
                return;
            }

            // 【任务开始】获取锁成功。
            log.info("[Task Start] Lock acquisition successful. Lock name: {}", lockName);
            task.run();
        } catch (InterruptedException e) {
            // 【任务中断】获取锁被中断。
            log.warn("[Task Interrupted] The acquisition lock has been interrupted. Lock name: {}", lockName);
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            // 【任务异常】服务执行异常。
            log.error("[Task Exception] Service execution abnormality. Lock name: {}", lockName, t);
            throw new RuntimeException("定时任务执行失败: " + lockName, t);  // ← 加上
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                try {
                    lock.unlock();
                    // 【任务完成】锁已释放。
                    log.info("[Task Complete] Lock has been released. Lock name:{}", lockName);
                } catch (Exception e) {
                    // 【任务异常】锁释放异常。
                    log.error("[Task Abnormal] Lock release failed. Lock name: {}", lockName, e);
                }
            }
        }
    }


    /**
     * 公共方法，仅在UAR周期内执行分布式锁任务。
     *
     * @param lockName 分布式锁名称
     * @param task 要执行的任务
     */
    public void executeTaskWithinUarCycleWithLock(String lockName, Runnable task) {
        executeTaskWithLock(lockName, () -> {
            if (!uarCycleSettingService.isCurrentDateWithinCycleRange()) {
                log.info("[Task Skip] Current date is outside the UAR cycle range. Lock name: {}", lockName);
                return;
            }

            task.run();
        });
    }




}
