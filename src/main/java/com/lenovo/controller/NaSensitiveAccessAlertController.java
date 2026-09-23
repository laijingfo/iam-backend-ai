package com.lenovo.controller;

import com.lenovo.config.LogOperation;
import com.lenovo.config.Logical;
import com.lenovo.config.RequiresPermission;
import com.lenovo.bean.NaSensitiveAccessAlertBean;
import com.lenovo.dto.NaSensitiveAccessAlertRequest;
import com.lenovo.dto.SuppressNaSensitiveAccessAlertRequest;
import com.lenovo.security.utils.RoleUtils;
import com.lenovo.service.NaSensitiveAccessAlertService;
import com.lenovo.util.ExcelUtil;
import com.lenovo.util.SortWhiteList;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/naSensitiveAccessAlert")
@RequiredArgsConstructor
public class NaSensitiveAccessAlertController {

    private static final Set<String> SORT_WHITE_LIST = SortWhiteList.of(NaSensitiveAccessAlertBean.class);

    private final NaSensitiveAccessAlertService alertService;
    private final RoleUtils roleUtils;

    @GetMapping("/list")
    @RequiresPermission(
            roles = {"View_Only_IT", "UAR_Admin_IT", "UAR_System_Admin_IT", "平台管理员", "Operation_owner_focal"},
            logical = Logical.OR
    )
    @LogOperation(value = "NA敏感权限告警-查询列表", module = "NA敏感权限告警", type = LogOperation.OperationType.QUERY)
    public ResponseEntity<?> getList(NaSensitiveAccessAlertRequest request) {
        SortWhiteList.check(SORT_WHITE_LIST, request.getSortField());
        request.setDataRange(roleUtils.getCurrentUserUarBusinessDataRange());
        return ResponseEntity.ok(alertService.getList(request));
    }

    @PutMapping("/{id}/suppress")
    @RequiresPermission(
            roles = {"UAR_Admin_IT", "UAR_System_Admin_IT", "平台管理员", "Operation_owner_focal"},
            logical = Logical.OR
    )
    @LogOperation(value = "NA敏感权限告警-抑制告警", module = "NA敏感权限告警", type = LogOperation.OperationType.UPDATE)
    public ResponseEntity<?> suppress(
            @PathVariable Long id,
            @Valid @RequestBody SuppressNaSensitiveAccessAlertRequest request) {
        List<String> dataRange = roleUtils.getCurrentUserUarBusinessDataRange();
        boolean suppressed = alertService.suppressAlert(
                id, request.getSuppressedReason(), request.getSuppressedRequestedBy(), dataRange);
        if (!suppressed) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "告警不存在、已处理或无权操作"
            ));
        }
        return ResponseEntity.ok(Map.of("success", true, "message", "抑制成功"));
    }

    @GetMapping("/exportExcel")
    @RequiresPermission(
            roles = {"View_Only_IT", "UAR_Admin_IT", "UAR_System_Admin_IT", "平台管理员", "Operation_owner_focal"},
            logical = Logical.OR
    )
    @LogOperation(value = "NA敏感权限告警-导出", module = "NA敏感权限告警", type = LogOperation.OperationType.EXPORT)
    public void exportExcel(NaSensitiveAccessAlertRequest request, HttpServletResponse response) throws IOException {
        SortWhiteList.check(SORT_WHITE_LIST, request.getSortField());
        request.setDataRange(roleUtils.getCurrentUserUarBusinessDataRange());

        List<NaSensitiveAccessAlertBean> alerts = alertService.getAlertsForExport(request);
        boolean chinese = !"EN".equalsIgnoreCase(StringUtils.defaultString(request.getLanguage(), "CN"));
        String[] fields = {
                "id", "cmdbId", "appName", "userItCode", "userCocType",
                "systemRole", "roleDescription", "alertTriggerTime", "resolvedTime",
                "alertStatus", "suppressedBy", "suppressedRequestedBy", "suppressedTime"
        };
        String[] headers = chinese
                ? new String[]{
                "告警ID", "应用编号", "应用名称", "用户IT Code", "用户CoC类型",
                "用户角色", "角色描述", "告警触发时间", "告警关闭时间",
                "告警状态", "告警抑制操作人", "告警抑制申请人", "告警抑制时间"
        }
                : new String[]{
                "Alert ID", "App ID", "App Name", "User IT Code", "User CoC Type",
                "User Role Name", "Role Desc", "Alert Triggered Time", "Alert Closed Time",
                "Alert Status", "Alert Suppressed By", "Alert Suppression Requested By", "Alert Suppressed Time"
        };
        String fileName = chinese ? "NA敏感权限告警" : "NA_Sensitive_Access_Alerts";
        ExcelUtil.exportToExcel(response, fileName, fileName, alerts, headers, fields, null, false);
    }
}
