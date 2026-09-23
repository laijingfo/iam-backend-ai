package com.lenovo.controller;

import cn.hutool.core.util.ObjectUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lenovo.bean.AppOfflineBean;
import com.lenovo.bean.ApplicationCycleInfoBean;
import com.lenovo.bean.UarCycleMaintenanceBean;
import com.lenovo.config.GlobalBusinessStatusEnum;
import com.lenovo.config.LogOperation;
import com.lenovo.bean.UarCycleBean;
import com.lenovo.entity.AutoMailSendBpoTemp;
import com.lenovo.entity.AutoMailSendLineManagerTemp;
import com.lenovo.entity.SendEmailActionLog;
import com.lenovo.mapper.AutoMailSendLineManagerTempMapper;
import com.lenovo.security.utils.RoleUtils;
import com.lenovo.security.utils.SecurityUtils;
import com.lenovo.service.SendEmailActionLogService;
import com.lenovo.service.UarCycleMaintenanceService;
import com.lenovo.service.impl.IEmailSendService;
import com.lenovo.strategy.CheckStrategy;
import com.lenovo.strategy.SendEmailStrategyType;
import com.lenovo.strategy.factory.CheckStrategyFactory;
import com.lenovo.util.DateUtil;
import com.lenovo.util.I18nUtil;
import com.lenovo.util.NodeIdUtil;
import com.lenovo.util.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import com.lenovo.util.ExcelUtil;
import static org.springframework.http.ResponseEntity.ok;

@Slf4j
@RestController
@RequestMapping("/UarCycleMaintenance")
@RequiredArgsConstructor


public class UarCycleMaintenanceController {


    private final UarCycleMaintenanceService uarCycleMaintenanceService;
    private final RoleUtils roleUtils;
    private final RedisUtils redisUtils;
    private final IEmailSendService emailSendServiceAsync;
    private final NodeIdUtil nodeIdUtil;
    private final RedissonClient redissonClient;
    private final CheckStrategyFactory strategyFactory;
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");

    private final AutoMailSendLineManagerTempMapper autoMailSendLineManagerTempMapper;


    /**
     *  UAR周期设置-应用列表
     * @param uarCycleMaintenanceBean conditions
     * @return
     */
    @LogOperation(value = "UAR周期设置-列表查询", module = "应用系统UAR周期维护", type = LogOperation.OperationType.QUERY)
    @GetMapping("/UarCycleMaintenance/getAll")
    public ResponseEntity query(
            UarCycleMaintenanceBean uarCycleMaintenanceBean
    ) {
        uarCycleMaintenanceBean.setDataRange(roleUtils.getCurrentUserUarBusinessDataRange());

        return ok(uarCycleMaintenanceService.query(uarCycleMaintenanceBean));
    }

    /**
     * UAR周期设置-应用列表
     * @param uarCycleMaintenanceBean 条件
     */
    @LogOperation(value = "UAR周期设置-数据导出", module = "应用系统UAR周期维护", type = LogOperation.OperationType.QUERY)
    @GetMapping("/UarCycleMaintenance/export")
    public void export(
            UarCycleMaintenanceBean uarCycleMaintenanceBean, HttpServletResponse response
    ) throws IOException {
        List<String> dataRange = roleUtils.getCurrentUserUarBusinessDataRange();

        uarCycleMaintenanceBean.setDataRange(dataRange);
        List<ApplicationCycleInfoBean> list = uarCycleMaintenanceService.queryFullData(uarCycleMaintenanceBean);


        String[] headers = new String[0];
        String[] fields = new String[0];
        String fileName = "";
        String sheetName = "";

        fields = new String[]{
                "applicationId", "application", "appStatus", "onlineFlagStr", "onlineTime", "cycleDataFlag",
                "appUarPlatformStatus", "uarCycleStartMonth", "uarCycleEndMonth",
                "offlineTime", "operationOwner", "operationFocal", "uarProcessorStr", "uarProcessorEmail",
                "operatorName", "operatorDate", "modifiedBy", "modificationDate", "dataSource", "accessLink"
        };

        if ("CN".equals(uarCycleMaintenanceBean.getLanguage())) {
            headers = new String[]{
                    "应用编号", "应用名称", "应用状态", "是否上线", "应用上线时间", "数据准备状态",
                    "UAR 周期状态", "UAR周期起始日期", "UAR周期结束日期",
                    "应用下线时间", "运维负责人", "S&A团队", "UAR Processor", "UAR Processor 邮箱",
                    "操作人", "操作时间", "变更人", "变更时间", "数据来源", "权限申请链接"
            };
            if (uarCycleMaintenanceBean.getSource().equals("0")) {
                fileName = "UAR周期设置";
                sheetName = "UAR周期设置";
            } else {
                fileName = "UAR周期维护";
                sheetName = "UAR周期维护";
            }

        } else {
            headers = new String[]{
                    "App ID", "App Name", "App Status", "Online Flag", "App Online Date", "Data Ready Status",
                    "UAR Cycle Status", "UAR Cycle Start Date", "UAR Cycle End Date",
                    "App Offline Date", "App Ops Owner", "S&A Team", "UAR Processor", "UAR Processor Email",
                    "Operator", "Op Time", "Modifier", "Mod Time", "Data Source", "Access Request Link"
            };
            if (uarCycleMaintenanceBean.getSource().equals("0")) {
                fileName = "UAR_Cycle_Setting";
                sheetName =  "UAR_Cycle_Setting";
            } else {
                fileName = "UAR_Cycle_Maintenance";
                sheetName = "UAR_Cycle_Maintenance";
            }
        }

        ExcelUtil.exportToExcel(response, fileName, sheetName, list, headers, fields, null, false);
    }


    /**
     * 批量上线
     * @param uarCycleBean 参数
     * @return Map
     */
    @LogOperation(value = "批量更新上线时间", module = "应用系统UAR周期维护", type = LogOperation.OperationType.UPDATE)
    @PostMapping("/UarCycleMaintenance/batchUpdateStartMonth")
    public ResponseEntity<Map<String, Object>> batchUpdateAppOnlineTime(@RequestBody UarCycleBean uarCycleBean)
    {
        List<String> dataRange = roleUtils.getCurrentUserUarBusinessDataRange();
        uarCycleBean.getFilters().setDataRange(dataRange);
        try
        {
            Integer i = uarCycleMaintenanceService.batchUpdateAppOnlineTime(uarCycleBean);
            if (i == 0)
            {
                return ResponseEntity.ok(Map.of("message", "数据不存在或已上线", "success", false));
            }

            try
            {
                String sendFlag = "AppOnline";
                sendEmailByUarCycle(uarCycleBean,sendFlag);
            }
            catch (Exception e)
            {
                log.error("批量更新上线:发送邮件：操作上线成功,邮件发送失败:{}", e.getMessage(),e);
                return ResponseEntity.ok(Map.of("success", false, "message", I18nUtil.get("cycleMaintenance.error1") + e));
            }
            return ok(Map.of("success", true, "message", "操作上线成功"));
        }
        catch (Exception e)
        {
            return ResponseEntity.ok(
                    Map.of(
                            "success", false,
                            "message", "操作上线失败: " + e.getMessage()
                    )
            );
        }
    }

    /**
     * 批量下线
     * @param uarCycleBean 参数
     * @return Map
     */
    @LogOperation(value = "批量更新下线时间", module = "应用系统UAR周期维护", type = LogOperation.OperationType.UPDATE)
    @PostMapping("/UarCycleMaintenance/batchUpdateEndMonth")
    public ResponseEntity<Map<String, Object>> batchUpdateAppOfflineTime(@RequestBody UarCycleBean uarCycleBean) {
        try{
            Integer i = uarCycleMaintenanceService.batchUpdateAppOfflineTime(uarCycleBean);
            if (i == 0) {
                return ResponseEntity.ok().body(
                        Map.of("message", "数据不存在或已下线", "success", false)
                );
            }

            try
            {
                String sendFlag = "AppOffline";
                sendEmailByUarCycle(uarCycleBean,sendFlag);
            }
            catch (Exception e)
            {
                log.error("批量更新下线:发送邮件：操作下线成功,邮件发送失败:{}", e.getMessage(),e);
                return ok(Map.of("success", false, "message", I18nUtil.get("cycleMaintenance.error2") + e));
            }
            return ok(Map.of("success", true, "message", "操作下线成功"));
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return ResponseEntity.ok().body(
                    Map.of(
                            "success", false,
                            "message", "操作下线失败: " + e.getMessage()
                    )
            );
        }
    }


    /**
     * 批量初始化UAR周期
     * @param uarCycleBean 参数
     * @return Map
     */
    @LogOperation(value = "开启UAR周期", module = "应用系统UAR周期维护", type = LogOperation.OperationType.ADD)
    @PostMapping("/batchInitUarCycle")
    public ResponseEntity batchInitUarCycle(@RequestBody UarCycleBean uarCycleBean) {
        uarCycleBean.getFilters().setDataRange(roleUtils.getCurrentUserUarBusinessDataRange());
        try{
            String s = uarCycleMaintenanceService.batchInitUarCycle(uarCycleBean);
            if ("success".equals(s)) {
                return ok(Map.of("success", true));
            } else {
                return ResponseEntity.ok(
                        Map.of(
                                "success", false,
                                "message", "开启UAR周期失败: " + s
                        )
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
            // 唯一索引冲突
            if (e.getMessage().contains("unique"))
            {
                String errorMsg = e.getMessage();
                int i = errorMsg.indexOf(")=(");
                String substringCmdbId = errorMsg.substring(i + 3, i + 10); // cmdbId一般是7位
                return ResponseEntity.ok(
                        Map.of(
                                "success", false,
                                "message", "应用["+substringCmdbId+"]已在此轮周期中，请勿重复开启。"
                        )
                );
            }
            return ResponseEntity.ok(
                    Map.of(
                            "success", false,
                            "message", "开启UAR周期失败: " + e.getMessage()
                    )
            );
        }
    }

    /**
     * 更新最终审核结果，结束UAR周期
     * 接口地址：POST /useAccessReview/batchCompleteUarCycle
     */
    @LogOperation(value = "更新最终审核结果，结束UAR周期", module = "应用系统UAR周期维护", type = LogOperation.OperationType.UPDATE)
    @PostMapping("/batchCompleteUarCycle")
    public ResponseEntity<?> batchCompleteUarCycle(@RequestBody UarCycleBean uarCycleBean) {
        uarCycleBean.getFilters().setDataRange(roleUtils.getCurrentUserUarBusinessDataRange());

        try{
            String s = uarCycleMaintenanceService.batchCompleteCycle(uarCycleBean);
            if ("success".equals(s)) {
                return ok(Map.of("success", true, "message", "系统已成功进入最终审核状态"));
            } else {
                return ok(Map.of("success", false, "message", s));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok(
                    Map.of(
                            "success", false,
                            "message", "更新最终审核结果失败"
                    )
            );
        }

    }



    /**
     * @Description TODO 给选中应用的负责人发送开启本轮UAR周期的通知邮件
     * @author wangfenglong
     * @date 2026/1/19 16:46
     **/
    @PostMapping("/sendStartCycleEmail")
    @LogOperation(value = "发送开启本轮UAR周期的通知邮件", module = "应用系统UAR周期维护", type = LogOperation.OperationType.OTHER)
    public ResponseEntity<Map<String, Object>> sendStartCycleEmail(@RequestBody UarCycleBean uarCycleBean)
    {
        uarCycleBean.getFilters().setDataRange(roleUtils.getCurrentUserUarBusinessDataRange());
        try
        {
            uarCycleBean =  uarCycleMaintenanceService.batchSendEmailsAsyncByInitUarCycleAndOfflineTime(uarCycleBean);
            if (ObjectUtil.isEmpty(uarCycleBean))
            {
                return ResponseEntity.ok(Map.of("success", false, "message", I18nUtil.get("cycleMaintenance.error3")));
            }
            String sendFlag = "InitUarCycle";
            sendEmailByUarCycle(uarCycleBean,sendFlag);
            return ok(Map.of("success", true,"message",I18nUtil.get("common.success")));
        }
        catch (Exception e)
        {
            log.error("发送开启本轮UAR周期的通知邮件：发送邮件失败:{}", e.getMessage(),e);
            return ResponseEntity.ok(Map.of("success", false, "message", I18nUtil.get("common.fail")+ " : " + e));
        }
    }

    /**
     * @Description TODO 给选中应用的负责人发送关闭本轮UAR周期的通知邮件
     * @author wangfenglong
     * @date 2026/1/20 15:05
    **/
    @PostMapping("/sendStopCycleEmail")
    @LogOperation(value = "发送关闭本轮UAR周期的通知邮件", module = "应用系统UAR周期维护", type = LogOperation.OperationType.OTHER)
    public ResponseEntity<Map<String, Object>> sendStopCycleEmail(@RequestBody UarCycleBean uarCycleBean)
    {
        uarCycleBean.getFilters().setDataRange(roleUtils.getCurrentUserUarBusinessDataRange());
        try
        {
            uarCycleBean =  uarCycleMaintenanceService.batchSendEmailsAsyncByInitUarCycleAndOfflineTime(uarCycleBean);
            if (ObjectUtil.isEmpty(uarCycleBean))
            {
                return ResponseEntity.ok(Map.of("success", false, "message", I18nUtil.get("cycleMaintenance.error3")));
            }
            String sendFlag = "CompleteUarCycle";
            sendEmailByUarCycle(uarCycleBean,sendFlag);
            return ok(Map.of("success", true,"message",I18nUtil.get("common.success")));
        }
        catch (Exception e)
        {
            log.error("发送关闭本轮UAR周期的通知邮件：发送邮件失败:{}", e.getMessage(),e);
            return ResponseEntity.ok(Map.of("success", false, "message", I18nUtil.get("common.fail")+ " : " + e));
        }
    }

    /**
     * @Description TODO UAR周期设置发送邮件
     * @author wangfenglong
     * @date 2026/1/20 15:13
    **/
    private void sendEmailByUarCycle(UarCycleBean uarCycleBean,String sendFlag)
    {


        uarCycleBean.setCycleDataFlag(sendFlag);
        CheckStrategy<UarCycleBean,ResponseEntity<?>> strategy = strategyFactory.getStrategy(SendEmailStrategyType.UAR_CYCLE_MAINTENANCE_TYPE);
        strategy.handle(null, uarCycleBean);
    }

    /**
     * @Description ！！！！！无实际执行 并且是旧代码 使用前待验证！！！！！ 给所有的lm和bpo发送邮件,并且过滤掉高管邮箱
     * @author wangfenglong
     * @date 2026/2/4 17:00
    **/
    @LogOperation(value = "给所有lm和bpo发送邮件", module = "应用系统UAR周期维护", type = LogOperation.OperationType.OTHER)
    @PostMapping("/sendEmailToAllLmAndBpo")
    public ResponseEntity<?> sendEmailToAllLmAndBpo()
    {
        String batchNo = emailSendServiceAsync.generateBatchNoByScheduledTask();
        String itCode = SecurityUtils.getCurrentUserId();
        String nodeId = nodeIdUtil.getNodeId();
        log.info("【给所有lm和bpo发送邮件】批次号：{}，节点{}开始执行，批次大小：{}", batchNo, nodeId, GlobalBusinessStatusEnum.BATCH_SIZE.code);
        try
        {
            //将需要发送的lm和bpo数据分别写到对应的表中
            emailSendServiceAsync.getLinaManagerSendData();
            log.info("【给所有lm和bpo发送邮件】批次号：{}，节点{}成功写入缓存表", batchNo, nodeId);
        }
        catch (Exception e)
        {
            log.error("【给所有lm和bpo发送邮件】批次号：{}，节点{}写入缓存表异常:{}", batchNo, nodeId, e.getMessage(),e);
            return ResponseEntity.ok(Map.of("success", false, "message", "给所有lm和bpo发送邮件失败: " + e));
        }

        List<AutoMailSendLineManagerTemp> linaManagerSendDataList = autoMailSendLineManagerTempMapper.getLMFromTable();
        List<AutoMailSendBpoTemp> bpoSendDataList = autoMailSendLineManagerTempMapper.getBpoFromTable();
        emailSendServiceAsync.sendAllEmail(linaManagerSendDataList,bpoSendDataList,batchNo,itCode,nodeId);
        return ok(Map.of("success", true, "message", "邮件发送任务完成"));
    }



    @GetMapping("/UarCycleMaintenance/uar_cycle_maintenance_distinct_list")
    public ResponseEntity uar_cycle_maintenance_distinct_list() {
        return ok(uarCycleMaintenanceService.uar_cycle_maintenance_distinct_list());

    }

    @LogOperation(value = "查询周期应用下拉列表", module = "应用系统UAR周期维护", type = LogOperation.OperationType.QUERY)
    @GetMapping("/UarCycleMaintenance/uar_cycle_maintenance_distinct_application")
    public ResponseEntity uar_cycle_maintenance_distinct_application() {
        return ok(Map.of("data", uarCycleMaintenanceService.uar_cycle_maintenance_distinct_application()));

    }
}
