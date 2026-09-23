package com.lenovo.controller;

import com.lenovo.bean.UarAlertBean;
import com.lenovo.config.LogOperation;
import com.lenovo.constant.UarAlertType;
import com.lenovo.dto.MarkBpoInvalidRequest;
import com.lenovo.dto.UarAlertRequest;
import com.lenovo.mapper.AlertDashboardMapper;
import com.lenovo.security.utils.RoleUtils;
import com.lenovo.security.utils.SecurityUtils;
import com.lenovo.service.AlertDashboardService;
import com.lenovo.service.UarMailTemplateService;
import com.lenovo.strategy.CheckStrategy;
import com.lenovo.strategy.SendEmailStrategyType;
import com.lenovo.strategy.factory.CheckStrategyFactory;
import com.lenovo.util.I18nUtil;
import com.lenovo.util.SortWhiteList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.lenovo.util.ExcelUtil;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * @author: fz.liu
 * @Description: 告警看板
 */
@Slf4j
@RestController
@RequestMapping("/alertDashboard")
@RequiredArgsConstructor
public class AlertDashboardController {
    private final AlertDashboardService alertDashboardService;
    private final RoleUtils roleUtils;
    private final CheckStrategyFactory strategyFactory;
    private final UarMailTemplateService uarMailTemplateService;
    private final AlertDashboardMapper alertDashboardMapper;
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");

    /**
     * 插入UAR告警数据
     * @return
     */
    @LogOperation(value = "告警看板-检测待审核数据是否存在异常，并记录", module = "告警看板", type = LogOperation.OperationType.ADD)
    @RequestMapping("/insert")
    public ResponseEntity insert() {
        return ResponseEntity.ok(alertDashboardService.insertUarAlertData());
    }

    @LogOperation(value = "告警看板-人工标记BPO失效", module = "告警看板", type = LogOperation.OperationType.ADD)
    @PostMapping("/markBpoInvalid")
    public ResponseEntity markBpoInvalid(@RequestBody MarkBpoInvalidRequest dto) {
        String cmdbId = dto.getCmdbId();
        String bpo = dto.getBpo();
        List<String> systemRoles = dto.getSystemRoles();

        if (StringUtils.isBlank(cmdbId) || StringUtils.isBlank(bpo) || CollectionUtils.isEmpty(systemRoles)) {
            return ResponseEntity.ok(
                    Map.of("success", false, "message", "参数不能为空")
            );
        }

        int inserted = alertDashboardService.insertManualBpoInvalid(dto);
        if (inserted > 0) {
            return ResponseEntity.ok(
                    Map.of("success", true, "message", "标记成功")
            );
        }
        return ResponseEntity.ok(
                Map.of("success", false, "message", "未找到匹配数据或已存在相同告警")
        );
    }

    /**
     * 排序白名单
     */
    private static final Set<String> SORT_WHITE_ALERT_LIST
            = SortWhiteList.of(UarAlertBean.class);

    /**
     * 获取告警看板列表数据
     * @return
     */
    @LogOperation(value = "告警看板-获取告警看板列表数据", module = "告警看板", type = LogOperation.OperationType.QUERY)
    @RequestMapping("/list")
    public ResponseEntity getList(UarAlertRequest uarAlertRequest) {
        // 排序字段校验
        SortWhiteList.check(SORT_WHITE_ALERT_LIST, uarAlertRequest.getSortField());
        uarAlertRequest.setDataRange(roleUtils.getCurrentUserUarBusinessDataRange());
        return ResponseEntity.ok( alertDashboardService.getList(uarAlertRequest) );
    }

    @LogOperation(value = "告警看板-导出告警看板数据", module = "告警看板", type = LogOperation.OperationType.EXPORT)
    @GetMapping("/exportExcel")
    public void exportExcel(UarAlertRequest uarAlertRequest, HttpServletResponse response) throws IOException {
        // 排序字段校验
        SortWhiteList.check(SORT_WHITE_ALERT_LIST, uarAlertRequest.getSortField());

        String module = uarAlertRequest.getModule();
        String language = StringUtils.isEmpty(uarAlertRequest.getLanguage()) ? "CN" : uarAlertRequest.getLanguage();
        uarAlertRequest.setLanguage( language );
        uarAlertRequest.setDataRange(roleUtils.getCurrentUserUarBusinessDataRange());

        List<UarAlertBean> list = alertDashboardService.getAlertsForExport(uarAlertRequest);

        String[] headers = new String[0];
        String[] fields = new String[0];
        String fileName = "";
        String sheetName = "";

        if ("UAR".equals(module)) {
            fields = new String[]{"cmdbId", "appName", "operationOwner", "operationFocal", "operationOwnerDomain", "operationOwnerTower", "alertTypeStr", "itCodeOfUser", "systemRole", "bpo"};

            if ("CN".equals(language)) {
                headers = new String[]{"应用编号", "应用名称", "应用运维负责人", "S&A 负责人", "运维负责人Domain", "运维负责人Tower", "告警类型", "用户ITcode", "用户角色", "BPO ITcode"};
                fileName = "BPO失效报表";
                sheetName = "BPO失效报表";
            } else {
                headers = new String[]{"App ID", "App Name", "App Ops Owner", "S&A Owner", "App Ops Owner Domain", "App Ops Owner Tower", "Reason for the issue", "User IT Code", "User Role Name", "BPO It Code"};
                fileName = "BPO_Invalid_Report";
                sheetName =  "BPO_Invalid_Report";

            }
        } else if ("Setting".equals(module)) {
            fields = new String[]{"cmdbId", "appName", "alertTypeStr", "itCodeOfUser", "systemRole", "lineManager", "successionManager", "bpo", "managerOfBpo", "alertHandleStatusStr", "updateBy", "updateTime"};

            if ("CN".equals(language)) {
                headers = new String[]{"应用编号", "应用名称", "告警类型", "用户ITcode", "用户角色", "直属经理", "继任直属经理 ITCode", "BPO ITcode", "原BPO的直属经理 ITCode", "告警处理状态", "异常标记时间", "异常处理时间"};
                fileName = "UAR告警数据";
                sheetName = "UAR告警数据";
            } else {
                headers = new String[]{"App ID", "App Name", "Reason for the issue", "User IT Code", "User Role Name", "Line Manager It Code", "Successor LM's IT Code", "BPO It Code", "ITCode of the original BPO's line manager", "Handle Status", "Operator", "Operation Date"};
                fileName = "UAR_Alert_Data";
                sheetName = "UAR_Alert_Data";

            }
        }

        ExcelUtil.exportToExcel(response, fileName, sheetName, list, headers, fields, null, false);
    }

    /**
     * 处理告警数据
     * @return
     */
    @LogOperation(value = "告警看板-处理告警数据", module = "告警看板", type = LogOperation.OperationType.UPDATE)
    @PostMapping("/handle")
    public ResponseEntity handle() {

        int handle = alertDashboardService.handle();
        if (handle > 0) {
            return ResponseEntity.ok(
                    Map.of( "success", true, "message", "操作成功" )
            );
        }
        return ResponseEntity.ok(
                Map.of( "success", true, "message", "操作失败" )
        );
    }


    /**
     * @Description 给BPO失效的itCode发送邮件(发给focal和owner)
     * @author wangfenglong
     * @date 2026/3/4 16:28
    **/
    @LogOperation(module = "告警看板-告警仪表盘", type = LogOperation.OperationType.UPDATE, value = "处理BPO_ITCode失效")
    @PostMapping("/handleExpiredBpoItCode")
    public ResponseEntity<?> handleExpiredBpoItCode()
    {
        try
        {
            String itCode = SecurityUtils.getCurrentUserId();
            UarAlertRequest uarAlertRequest = new UarAlertRequest();
            uarAlertRequest.setAlertType(UarAlertType.BPO_IT_CODE_INVALID.getCodeValue());
            uarAlertRequest.setUpdateBy(itCode);
            uarAlertRequest.setItCodeOfUser(itCode);
            uarAlertRequest.setUpdateTime(LocalDateTime.now());
            CheckStrategy<UarAlertRequest,ResponseEntity<?>> strategy = strategyFactory.getStrategy(SendEmailStrategyType.EXPIRED_BPO_CYCLE_TYPE);
            strategy.handle(null, uarAlertRequest);
            alertDashboardMapper.updateByAlertType(uarAlertRequest);
            return ResponseEntity.ok(Map.of( "success", true, "message", I18nUtil.get("common.success")));
        }
        catch (Exception e)
        {
            return ResponseEntity.ok(Map.of( "success", false, "message", I18nUtil.get("common.fail")+" : "+e.getMessage()));
        }
    }

    /**
     * 获取BPO失效信息总览
    **/
    @LogOperation(value = "告警看板-BPO失效信息总览", module = "告警看板", type = LogOperation.OperationType.QUERY)
    @GetMapping("/getAlertBpoSummary")
    public ResponseEntity getAlertBpoSummary(UarAlertRequest uarAlertRequest)
    {
        try {
            SortWhiteList.check(SORT_WHITE_ALERT_LIST, uarAlertRequest.getSortField());
            uarAlertRequest.setAlertType(UarAlertType.codeValues(
                    UarAlertType.BPO_IT_CODE_INVALID,
                    UarAlertType.MANUAL_BPO_INVALID));
            uarAlertRequest.setAlertHandleStatus("Pending,Send");
            uarAlertRequest.setDataRange(roleUtils.getCurrentUserUarBusinessDataRange());
            return ResponseEntity.ok(
                    Map.of( "success", true, "message", I18nUtil.get("common.success"), "data", alertDashboardService.getAlertBpoSummary(uarAlertRequest))
            );
        } catch (Exception e)
        {
            return ResponseEntity.ok(Map.of( "success", false, "message", I18nUtil.get("common.fail")+" : "+e.getMessage()));
        }
    }

    /**
     * 获取BPO失效信息总览
     **/
    @LogOperation(value = "告警看板-导出BPO失效信息总览", module = "告警看板", type = LogOperation.OperationType.EXPORT)
    @GetMapping("/exportAlertBpoSummary")
    public void exportAlertBpoSummary(UarAlertRequest uarAlertRequest, HttpServletResponse response)
    throws IOException
    {
        SortWhiteList.check(SORT_WHITE_ALERT_LIST, uarAlertRequest.getSortField());
        uarAlertRequest.setAlertType(UarAlertType.codeValues(
                UarAlertType.BPO_IT_CODE_INVALID,
                UarAlertType.MANUAL_BPO_INVALID));
        uarAlertRequest.setAlertHandleStatus("Pending,Send");
        uarAlertRequest.setDataRange(roleUtils.getCurrentUserUarBusinessDataRange());

        List<UarAlertBean> list = alertDashboardService.getAllAlertBpoSummary(uarAlertRequest);

        String[] headers = new String[0];
        String[] fields = new String[0];
        String fileName = "";
        String sheetName = "";

        fields = new String[]{"cmdbId", "appName", "operationOwner", "operationFocal", "operationOwnerDomain", "operationOwnerTower", "alertTypeStr", "systemRole", "bpo"};

        if ("CN".equals(uarAlertRequest.getLanguage())) {
            headers = new String[]{"应用编号", "应用名称", "应用运维负责人", "S&A 负责人", "运维负责人Domain", "运维负责人Tower", "告警类型", "用户角色", "BPO ITcode"};
            fileName = "BPO失效信息总览";
            sheetName = "BPO失效信息总览";
        } else {
            headers = new String[]{"App ID", "App Name", "App Ops Owner", "S&A Owner", "App Ops Owner Domain", "App Ops Owner Tower", "Reason for the issue", "User Role Name", "BPO It Code"};
            fileName = "OverviewOfBPOFailureInformation";
            sheetName =  "OverviewOfBPOFailureInformation";

        }


        ExcelUtil.exportToExcel(response, fileName, sheetName, list, headers, fields, null, false);

    }
    /**
     * 获取BPO失效ItCode列表
    **/
    @LogOperation(value = "告警看板-BPO失效ItCode列表", module = "告警看板", type = LogOperation.OperationType.QUERY)
    @GetMapping("/getAlertInvalidBpoList")
    public ResponseEntity getAlertInvalidBpoList()
    {

        try {
            UarAlertRequest uarAlertRequest = new UarAlertRequest();
            uarAlertRequest.setAlertType(UarAlertType.codeValues(
                    UarAlertType.BPO_IT_CODE_INVALID,
                    UarAlertType.MANUAL_BPO_INVALID));
            uarAlertRequest.setAlertHandleStatus("Pending,Send");
            uarAlertRequest.setDataRange(roleUtils.getCurrentUserUarBusinessDataRange());
            return ResponseEntity.ok(
                    Map.of( "success", true, "message", I18nUtil.get("common.success"), "data", alertDashboardService.getAlertInvalidBpoList(uarAlertRequest))
            );
        } catch (Exception e)
        {
            return ResponseEntity.ok(Map.of( "success", false, "message", I18nUtil.get("common.fail")+" : "+e.getMessage()));
        }
    }


}
