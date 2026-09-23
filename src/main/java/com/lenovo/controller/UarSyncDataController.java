package com.lenovo.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.bean.ApplicationAccessDataBean;
import com.lenovo.config.LogOperation;
import com.lenovo.security.exception.BadRequestException;
import com.lenovo.service.ItsApplicationAccessDataService;
import com.lenovo.service.UarSyncDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.lenovo.bean.UarSyncDataRequest;
import com.lenovo.security.utils.RoleUtils;
import com.lenovo.util.ExcelUtil;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/uarSyncData")
@RequiredArgsConstructor
@Slf4j
public class UarSyncDataController {
    private final ItsApplicationAccessDataService itsApplicationAccessDataService;
    private final UarSyncDataService uarSyncDataService;
    private final RoleUtils roleUtils;

    /**
     * 定时任务
     * 全量Uar数据同步
     */
    @LogOperation(value = "同步更新实时表全量数据", module = "Sync Snapshot", type = LogOperation.OperationType.SYNC)
    @GetMapping("/start")
    public void syncFullData(@RequestParam(required = false, defaultValue = "0") int start) {
        itsApplicationAccessDataService.syncFullData(start);
    }


    /**
     * 获取实时表数据
     */
    @LogOperation(value = "获取实时表数据", module = "Sync Snapshot", type = LogOperation.OperationType.QUERY)
    @GetMapping("/query")
    public ResponseEntity getData(UarSyncDataRequest request) {
        try {
            request.setDataRange(roleUtils.getCurrentUserUarBusinessDataRange());
            Page<ApplicationAccessDataBean> result = uarSyncDataService.getSyncData(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("查询实时表数据失败", e);
            return ResponseEntity.ok(
                    Map.of("message", "列表查询失败", "success", false)
            );
        }
    }


    @LogOperation(value = "导出实时表数据", module = "Sync Snapshot", type = LogOperation.OperationType.EXPORT)
    @GetMapping("/export")
    public void exportData(UarSyncDataRequest request, HttpServletResponse response) throws IOException {
        request.setDataRange(roleUtils.getCurrentUserUarBusinessDataRange());
        List<ApplicationAccessDataBean> list = uarSyncDataService.getSyncDataForExport(request);

        String[] headers = new String[]{};
        String fileName  ;
        String sheetName ;

        if (request.getLanguage().equals("CN")) {
            headers = new String[]{"应用编号", "应用名称", "用户 ITcode", "用户名", "用户角色编码", "用户角色", "角色描述", "角色类型", "角色分类", "直属经理", "直属经理邮箱", "直属经理职级", "BPO", "BPO邮箱", "BPO职级"};
            fileName = "应用接入数据";
            sheetName = "数据";
        } else {
            headers = new String[]{"App Id", "App Name", "User IT Code", "User Full Name", "User Role ID", "User Role Name", "Role Description", "Role Type", "Role Classification", "Line Manager", "Line Manager Email", "Line Manager Band", "BPO", "BPO Email", "BPO Band"};
            fileName = "App_Integration_Data";
            sheetName = "Data";
        }
        String[] fields = {"cmdbId", "appName", "itCodeOfUser", "userRealName", "systemRoleId", "systemRole", "roleDescription", "systemRoleType", "accessLabel", "lineManager", "lineManagerEmail", "lineManagerLevelCode", "bpo", "bpoEmail", "bpoLevelCode"};

        ExcelUtil.exportToExcel(response, fileName, sheetName, list, headers, fields,  null, false);
    }


    @LogOperation(value = "应用下去重的角色对应的bpo导出", module = "Sync Snapshot", type = LogOperation.OperationType.EXPORT)
    @GetMapping("/exportDistinctRoleAndBpo")
    public void exportDistinctRoleAndBpo(UarSyncDataRequest request, HttpServletResponse response) throws IOException {
        if (request.getCmdbId() == null || request.getCmdbId().isEmpty()) {
            throw new BadRequestException("应用编号不能为空 / The App Id cannot be empty");
        }
        request.setDataRange(roleUtils.getCurrentUserUarBusinessDataRange());
        List<ApplicationAccessDataBean> list = uarSyncDataService.getDistinctRoleAndBpo(request);

        String[] headers ;
        String fileName  ;
        String sheetName ;

        if (request.getLanguage().equals("CN")) {
            headers = new String[]{"应用编号", "应用名称", "用户角色编码", "用户角色", "角色描述", "角色类型", "角色分类", "BPO", "BPO邮箱"};
            fileName = request.getCmdbId() + "角色报表";
            sheetName = "角色和BPO数据";
        } else {
            headers = new String[]{"App Id", "App Name", "User Role ID", "User Role Name", "Role Description", "Role Type", "Role Classification", "BPO", "BPO Email"};
            fileName = request.getCmdbId() + "_Role_Report";
            sheetName = "Role_and_BPO_Data";
        }
        String[] fields = {"cmdbId", "appName", "systemRoleId", "systemRole", "roleDescription", "systemRoleType", "accessLabel", "bpo", "bpoEmail"};

        ExcelUtil.exportToExcel(response, fileName, sheetName, list, headers, fields,  null, false);

    }


}
