package com.lenovo.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.bean.DashboardChartReviewBean;
import com.lenovo.bean.DashboardSearchBean;
import com.lenovo.bean.DashboardUARBean;
import com.lenovo.bean.PermissionDistributionBean;
import com.lenovo.bean.PermissionDistributionSearchBean;
import com.lenovo.bean.UseAccessReviewBean;
import com.lenovo.config.LogOperation;
import com.lenovo.security.exception.BadRequestException;
import com.lenovo.security.utils.RoleUtils;
import com.lenovo.service.DashboardService;
import com.lenovo.service.UarCycleSettingService;
import com.lenovo.util.SortWhiteList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.lenovo.util.ExcelUtil;

/**
 * @author: fz.liu
 * @Description: 看板
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final RoleUtils roleUtils;
    private final DashboardService dashboardService;
    private final UarCycleSettingService uarCycleSettingService;

    /**
     * 统计：获取 LM 等级统计
     *  不同职级统计（lm人数，review条数）
     * @return
     */
    @LogOperation(value = "看板-UAR审核结果汇总 - mgr 等级统计", type = LogOperation.OperationType.QUERY)
    @GetMapping("/UarFullData/lmLevelStatistics")
    public ResponseEntity queryLmLevelStatistics (
            String currentDataCycle
    ) {
        if (currentDataCycle == null || currentDataCycle.isEmpty()) {
            return ResponseEntity.ok(
                    Map.of("message", "请先选择要查询的周期", "success", true)
            );
        }
        List<String> dataRange = roleUtils.getCurrentUserUarBusinessDataRange();
        Integer isCurrentCycle = uarCycleSettingService.queryIsCurrent(currentDataCycle);

        try {
            Map<String, Map<String, String>> result = dashboardService.queryLmLevelStatistics(isCurrentCycle, currentDataCycle, dataRange);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok(
                    Map.of("message", "顶部统计查询失败", "success", false)
            );
        }
    }


    /**
     * DashboardList 排序白名单
     */
    private static final Set<String> SORT_WHITE_DASHBOARD_LIST
            = SortWhiteList.of(DashboardUARBean.class);

    private static final Set<String> SORT_WHITE_PERMISSION_DISTRIBUTION
            = SortWhiteList.of(PermissionDistributionBean.Detail.class);

    /**
     * 获取 CoC 用户数最高的 10 个应用，供蝴蝶图展示。
     */
    @LogOperation(value = "看板-权限分布-Top10", type = LogOperation.OperationType.QUERY)
    @GetMapping("/permission-distribution/top10")
    public ResponseEntity<?> queryPermissionDistributionTop10(
            @RequestParam(required = false) String currentDataCycle) {
        if (currentDataCycle == null || currentDataCycle.isBlank()) {
            return ResponseEntity.ok(
                    Map.of("message", "请先选择要查询的周期", "success", false)
            );
        }

        PermissionDistributionSearchBean searchBean = new PermissionDistributionSearchBean();
        searchBean.setCurrentDataCycle(currentDataCycle);
        populatePermissionDistributionScope(searchBean);

        try {
            return ResponseEntity.ok(dashboardService.queryPermissionDistributionTop10(searchBean));
        } catch (Exception e) {
            log.error("获取权限分布 Top10 异常", e);
            return ResponseEntity.ok(
                    Map.of("message", "权限分布查询失败", "success", false)
            );
        }
    }

    /**
     * 分页查询应用权限分布明细。
     */
    @LogOperation(value = "看板-权限分布-明细", type = LogOperation.OperationType.QUERY)
    @GetMapping("/permission-distribution/list")
    public ResponseEntity<?> queryPermissionDistributionPage(
            PermissionDistributionSearchBean searchBean) {
        if (searchBean.getCurrentDataCycle() == null || searchBean.getCurrentDataCycle().isBlank()) {
            return ResponseEntity.ok(
                    Map.of("message", "请先选择要查询的周期", "success", false)
            );
        }

        try {
            validatePermissionDistributionSort(searchBean);
            populatePermissionDistributionScope(searchBean);
            Page<PermissionDistributionBean.Detail> result =
                    dashboardService.queryPermissionDistributionPage(searchBean);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(
                    Map.of("message", e.getMessage(), "success", false)
            );
        } catch (Exception e) {
            log.error("获取权限分布明细异常", e);
            return ResponseEntity.ok(
                    Map.of("message", "权限分布明细查询失败", "success", false)
            );
        }
    }

    /**
     * 导出应用权限分布明细。
     */
    @LogOperation(value = "看板-权限分布-明细导出", type = LogOperation.OperationType.EXPORT)
    @GetMapping("/permission-distribution/export")
    public void exportPermissionDistribution(
            PermissionDistributionSearchBean searchBean,
            HttpServletResponse response,
            @RequestParam(defaultValue = "CN") String language) throws IOException {
        if (searchBean.getCurrentDataCycle() == null || searchBean.getCurrentDataCycle().isBlank()) {
            throw new BadRequestException("请先选择要查询的周期");
        }

        validatePermissionDistributionSort(searchBean);
        populatePermissionDistributionScope(searchBean);

        List<PermissionDistributionBean.Detail> exportData;
        try {
            exportData = dashboardService.queryPermissionDistributionList(searchBean);
        } catch (Exception e) {
            log.error("导出权限分布明细异常", e);
            throw new BadRequestException("导出失败");
        }

        String[] headers;
        String fileName = searchBean.getCurrentDataCycle() + '_';
        String sheetName;
        if ("CN".equalsIgnoreCase(language)) {
            headers = new String[]{
                    "应用编号", "应用名称", "用户总数", "CoC用户数", "非CoC用户数", "无效用户数",
                    "CoC用户占比", "权限总数", "CoC用户权限数", "非CoC用户权限数",
                    "无效用户权限数", "CoC用户权限占比"
            };
            fileName += "权限分布明细";
            sheetName = "权限分布明细";
        } else {
            headers = new String[]{
                    "App ID", "App Name", "Total Users", "CoC Users", "Non-CoC Users", "Invalid Users",
                    "CoC User %", "Total Permissions", "CoC Assigned Permissions",
                    "Non-CoC Assigned Permissions", "Invalid Users Assigned Permissions", "CoC Perm %"
            };
            fileName += "Permission_Distribution_Details";
            sheetName = "Permission Distribution";
        }

        String[] fields = {
                "cmdbId", "applicationName", "totalUsers", "cocUsers", "nonCocUsers", "invalidUsers",
                "cocUserPercentage", "totalPermissions", "cocAssignedPermissions",
                "nonCocAssignedPermissions", "invalidUsersAssignedPermissions", "cocPermissionPercentage"
        };
        ExcelUtil.exportToExcel(
                response, fileName, sheetName, exportData, headers, fields, null, false
        );
    }

    private void populatePermissionDistributionScope(PermissionDistributionSearchBean searchBean) {
        searchBean.setDataRange(roleUtils.getCurrentUserUarBusinessDataRange());
        searchBean.setIsCurrentCycle(
                uarCycleSettingService.queryIsCurrent(searchBean.getCurrentDataCycle())
        );
    }

    private static void validatePermissionDistributionSort(PermissionDistributionSearchBean searchBean) {
        SortWhiteList.check(SORT_WHITE_PERMISSION_DISTRIBUTION, searchBean.getSortField());
        if (searchBean.getSortOrder() == null || searchBean.getSortOrder().isBlank()) {
            return;
        }

        String sortOrder = searchBean.getSortOrder().toLowerCase(Locale.ROOT);
        if (!"asc".equals(sortOrder) && !"desc".equals(sortOrder)) {
            throw new IllegalArgumentException("非法排序方向: " + searchBean.getSortOrder());
        }
        searchBean.setSortOrder(sortOrder);
    }

    /**
     * 列表：获取 UAR 数据和分析
     * @return
     */
    @LogOperation(value = "看板-UAR审核结果汇总 - 列表", type = LogOperation.OperationType.QUERY)
    @GetMapping("/UarFullData/query")
    public ResponseEntity queryUarFullData(
            UseAccessReviewBean useAccessReviewBean,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size
    ) {
        if (useAccessReviewBean.getCurrentDataCycle() == null || useAccessReviewBean.getCurrentDataCycle().isEmpty()) {
            return ResponseEntity.ok(
                    Map.of("message", "请先选择要查询的周期", "success", false)
            );
        }
        // 排序字段校验
        SortWhiteList.check(SORT_WHITE_DASHBOARD_LIST, useAccessReviewBean.getSortField());

        useAccessReviewBean.setDataRange(roleUtils.getCurrentUserUarBusinessDataRange());
        useAccessReviewBean.setIsCurrentCycle(
                uarCycleSettingService.queryIsCurrent(useAccessReviewBean.getCurrentDataCycle())
        );

        try {
            Page<DashboardUARBean> result = dashboardService.queryUarFullData(page, size, useAccessReviewBean);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.ok(
                    Map.of("message", "列表查询失败", "success", false)
            );
        }
    }


    /**
     * 导出：获取 UAR 数据
     * */
    @LogOperation(value = "看板-UAR审核结果汇总 - 导出", type = LogOperation.OperationType.EXPORT)
    @GetMapping("/UarFullData/exportExcel")
    public void exportExcel(HttpServletResponse response,
                            UseAccessReviewBean useAccessReviewBean,
                            @RequestParam(defaultValue = "CN") String language
    ) throws IOException {
        useAccessReviewBean.setDataRange(roleUtils.getCurrentUserUarBusinessDataRange());
        useAccessReviewBean.setIsCurrentCycle(
                uarCycleSettingService.queryIsCurrent(useAccessReviewBean.getCurrentDataCycle())
        );


        // 获取导出数据
        List<DashboardUARBean> exportData ;
        try {
            exportData = dashboardService.exportUarFullData(useAccessReviewBean, language);
        } catch (Exception e) {
            log.error("导出 UAR 数据失败", e);
            throw new RuntimeException("导出失败");
        }

        // 根据语言设置表头
        String[] headers;
        String fileName = useAccessReviewBean.getCurrentDataCycle()+"_";
        String sheetName;

        if ("CN".equalsIgnoreCase(language)) {
            headers = new String[]{
                    "序列号", "应用编号", "应用系统", "应用运维负责人", "运维负责人Domain", "运维负责人Tower", "S&A 团队",
                    "用户 IT Code", "用户名", "用户账号", "用户职级", "账号状态", "系统角色", "角色描述", "角色分类",
                    "直属经理", "直属经理职级", "直属经理部门", "直属经理审核状态", "直属经理审核结果", "直属经理实际审核人", "直属经理实际审核时间",
                    "BPO", "BPO 审核状态", "BPO 审核结果", "BPO 实际审核人", "BPO 实际审核时间",
                    "最终审核结果", "审核结果更新日期"
            };
            fileName += "UAR数据报表";
            sheetName = "数据报表";
        } else {
            headers = new String[]{
                    "Seq Num", "App ID", "App Name", "App Ops Owner", "Ops Owner Domain", "Ops Owner Tower", "S&A Team",
                    "User IT Code", "Nick Name", "User Account", "User Band", "Ad Valid", "User Role Name", "Role Description","Role Classification",
                    "Line Manager", "Line Manager Band", "Line Manager Dept", "Line Manager Review Status", "Line Manager Review Result", "Line Manager Actual Reviewer", "Line Manager Actual Review Time",
                    "BPO", "BPO Review Status", "BPO Review Result", "BPO Actual Reviewer", "BPO Actual Review Time",
                    "Final Review Result", "Final Review Result Update Date"
            };
            fileName += "UAR_Data_Report";
            sheetName = "Data_Report";
        }

        // 定义字段映射
        String[] fields = {
                "sequenceNumber", "cmdbId", "appName", "appOperationOwner", "operationOwnerDomain", "operationOwnerTower", "appOperationFocal",
                "itCodeOfUser", "userName", "userId", "userBand", "adValid", "systemRole", "roleDescription","accessLabel",
                "lineManager", "lineManagerLevelCode", "lineManagerDept","lineManagerReviewStatus", "lineManagersReviewDecision", "lineManagerReviewItcode", "lineManagerReviewTime",
                "bpo", "bpoReviewStatus", "bpoReviewDecision", "bpoReviewItcode", "bpoReviewTime",
                "finalReviewDecision", "finalReviewTime"
        };

        // 调用导出方法
        ExcelUtil.exportToExcel(response, fileName, sheetName, exportData, headers, fields, null, false);
    }

    /**
     * overall statistics
     * 统计审核进展数据
     */
    @LogOperation(value = "看板-获取审核进展数据统计", type = LogOperation.OperationType.QUERY)
    @GetMapping("/statistics/overall")
    public ResponseEntity queryOverallStatistics(
            String currentDataCycle
    ) {
        if (currentDataCycle == null || currentDataCycle.isEmpty()) {
            return ResponseEntity.ok(
                    Map.of("message", "请先选择要查询的周期", "success", false)
            );
        }
        try {
            List<String> dataRange = roleUtils.getCurrentUserUarBusinessDataRange();
            Integer isCurrentCycle = uarCycleSettingService.queryIsCurrent(currentDataCycle);

            DashboardChartReviewBean.OverallStatistics bean = dashboardService.queryOverallStatistics(isCurrentCycle, currentDataCycle, dataRange);
            return ResponseEntity.ok( bean );
        }
        catch (Exception e ) {
            log.error("获取审核进展数据统计异常", e);
            return ResponseEntity.ok(
                    Map.of("message", "数据统计查询失败", "success", false)
            );
        }
    }

    /**
     * OVERALL 排序白名单
     */
    private static final Set<String> SORT_WHITE_OVERALL_LIST
            = SortWhiteList.of(DashboardChartReviewBean.OverallList.class);

    /**
     * 获取审核进展数据列表
     * @return
     */
    @GetMapping("/list/overall")
    @LogOperation(value = "看板-获取审核进展数据列表", type = LogOperation.OperationType.QUERY)
    public ResponseEntity queryOverallPage(
            DashboardSearchBean searchBean
    ) {
        if (searchBean.getCurrentDataCycle() == null || searchBean.getCurrentDataCycle().isEmpty()) {
            return ResponseEntity.ok(
                    Map.of("message", "请先选择要查询的周期", "success", false)
            );
        }

        try {
            // 排序字段校验
            SortWhiteList.check(SORT_WHITE_OVERALL_LIST, searchBean.getSortField());
            searchBean.setDataRange(roleUtils.getCurrentUserUarBusinessDataRange());
            searchBean.setIsCurrentCycle(uarCycleSettingService.queryIsCurrent(searchBean.getCurrentDataCycle()));

            Page<DashboardChartReviewBean.OverallList> list = dashboardService.queryOverallPage(searchBean);
            return ResponseEntity.ok(list);
        }
        catch (Exception e) {
            log.error("获取审核进展数据列表异常", e);
            return ResponseEntity.ok(
                    Map.of("message", "列表查询失败", "success", false)
            );
        }
    }



    /**
     * 导出审核进展数据报表
     * @return
     */
    @LogOperation(value = "看板-导出审核进展数据报表", type = LogOperation.OperationType.EXPORT)
    @GetMapping("/export/overall")
    public void exportOverallList(
            DashboardSearchBean searchBean,
            HttpServletResponse response,
            @RequestParam(defaultValue = "CN") String language
    ) throws IOException {
        if (searchBean.getCurrentDataCycle() == null || searchBean.getCurrentDataCycle().isEmpty()) {
            throw new BadRequestException("请先选择要查询的周期");
        }
        // 排序字段校验
        SortWhiteList.check(SORT_WHITE_OVERALL_LIST, searchBean.getSortField());
        searchBean.setDataRange(roleUtils.getCurrentUserUarBusinessDataRange());
        searchBean.setIsCurrentCycle(uarCycleSettingService.queryIsCurrent(searchBean.getCurrentDataCycle()));

        List<DashboardChartReviewBean.OverallList> exportData ;
        try {
            exportData = dashboardService.queryOverallList(searchBean);
        } catch (Exception e) {
            log.error("获取审核进展数据列表异常", e);
            throw new BadRequestException("导出失败");
        }
        // 根据语言设置表头
        String[] headers;
        String fileName = searchBean.getCurrentDataCycle() + '_';
        String sheetName;

        if ("CN".equalsIgnoreCase(language)) {
            headers = new String[]{
                    "应用编号", "应用名称", "应用运维负责人", "运维负责人Domain", "运维负责人Tower", "S&A 团队",
                    "总条目", "已审核(条目)", "未审核(条目)", "审核完成率%",
                    "直线经理回复率%", "BPO回复率%", "保留(条目)", "移除(条目)", "保留率%", "移除率%"
            };
            fileName += "审核情况";
            sheetName = "审核情况";
        } else {
            headers = new String[]{
                    "App ID", "App Name", "App Ops Owner", "Ops Owner Domain", "Ops Owner Tower", "S&A Team",
                    "Total Items", "Reviewed Items", "Pending Items", "Comp Rate %",
                    "LM Resp Rate %", "BPO Resp Rate %", "Keep (Items)", "Remove (Items)", "Keep Rate %", "Remove Rate %"

            };
            fileName += "Review Situation";
            sheetName = "Review Situation";
        }

        // 定义字段映射
        String[] fields = {
                "cmdbId", "applicationName", "appOperationOwner", "operationOwnerDomain", "operationOwnerTower", "appOperationFocal",
                "total", "reviewed", "pendingReview", "completionRate",
                "lineManagerResponseRate", "bpoResponseRate", "keep", "remove", "keepRate", "removeRate"
        };

        // 调用导出方法
        ExcelUtil.exportToExcel(response, fileName, sheetName, exportData, headers, fields, null, false);
    }



}
