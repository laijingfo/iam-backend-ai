package com.lenovo.controller;

import cn.hutool.core.io.resource.ClassPathResource;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.bean.BpoInfoBean;
import com.lenovo.bean.ImportResult;
import com.lenovo.bean.UseAccessReviewBean;
import com.lenovo.config.LogOperation;
import com.lenovo.entity.BatchOperationRequest;
import com.lenovo.entity.UserAccessReview;
import com.lenovo.entity.UseAccessReviewEmailSend;
import com.lenovo.security.utils.RoleUtils;
import com.lenovo.security.utils.SecurityUtils;
import com.lenovo.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static com.lenovo.util.ExcelUtil.exportReviewToExcel;
import com.lenovo.util.ExcelUtil;
import static org.springframework.http.ResponseEntity.ok;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/useAccessReview")
@RequiredArgsConstructor


public class UserAccessReviewController {

    /**
     * LineManagerReview列表页
     */
    private final LineMangerReviewService lineMangerReviewService;
    private final BPOService bpoService;
    private final LineManagerBandReviewService lineManagerBandReviewService;
    private final MyAccessService myAccessService;
    private final UserAccessReviewService userAccessReviewService;
    private final TaskStatisticsService taskStatisticsService;
    private final RoleUtils roleUtils;



    /**
     * 下载excel模板
     */
    @LogOperation(module = "UAR数据导入", type = LogOperation.OperationType.DOWNLOAD, value = "下载UAR导入模板")
    @GetMapping("/downloadUarTemplate")
    public ResponseEntity<byte[]> downloadTemplate(
            @RequestParam(defaultValue = "CN") String language
    ) throws IOException {
        // 1. 从classpath加载模板文件
        String templatePath = "templates/UarImportTemplate_CN.xlsx";
        String fileName = "FocalITcode_CMDB号_UAR导入模板";
        if (!language.equals("CN")) {
            templatePath = "templates/UarImportTemplate_EN.xlsx";
            fileName = "FocalITcode_CmdbID_UARImportTemplate";
        }
        ClassPathResource resource = new ClassPathResource(templatePath);

        // 2. 读取文件字节
        byte[] fileContent = resource.getStream().readAllBytes();

        // 3. 设置响应头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        String encodedFileName = URLEncoder.encode(fileName + ".xlsx", StandardCharsets.UTF_8);
        headers.setContentDispositionFormData("attachment", encodedFileName); // 强制下载

        // 4. 返回文件流
        return ok()
                .headers(headers)
                .body(fileContent);
    }

    /**
     * 导入UAR数据
     *  根据cmdbId清空UserAccessReview表，插入新的数据
     *  更新 ItsApplicationAccessData表的data_ready_flag 为true
     *
     * @param file
     * @param cmdbId
     * @return
     */
    @LogOperation(module = "UAR数据导入", type = LogOperation.OperationType.IMPORT, value = "导入UAR数据")
    @PostMapping("/importUarExcel")
    public ResponseEntity importUarData(
            @RequestParam("file") MultipartFile file,
            @RequestParam("cmdbId") String cmdbId
    ) {
        try {
            ImportResult result = userAccessReviewService.importUarData(file, cmdbId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", result.getErrorCount() == 0);
            response.put("message", "导入成功 " + result.getSuccessCount() + " 条记录" +
                    (result.getErrorCount() > 0 ? "; " + result.getErrorCount() + " 条记录导入失败" : "") +
                    (result.getIgnoreCount() > 0 ? "; "+ result.getIgnoreCount() +" 条记录被规则忽略" : "") );
            response.put("successCount", result.getSuccessCount());
            response.put("errorCount", result.getErrorCount());

            if (result.getErrorCount() > 0) {
                response.put("errors", result.getErrors());
                return ResponseEntity.ok().body(response);
            }

            log.info("Log-ImportUarExcel-Success " + result.getSuccessCount() + " total" +
                    (result.getErrorCount() > 0 ? "; " + result.getErrorCount() + " error" : "") +
                    (result.getIgnoreCount() > 0 ? "; "+ result.getIgnoreCount() +" ignore" : "") );
            return ok(response);

        } catch (IOException e) {
            log.error("Log-ImportUarExcel-IOError", e);
            return ResponseEntity.ok()
                    .body(Map.of(
                            "success", false,
                            "message", "文件处理失败: " + e.getMessage()
                    ));
        } catch (Exception e) {
            log.error("Log-ImportUarExcel-Error", e);
            return ResponseEntity.ok()
                    .body(Map.of(
                            "success", false,
                            "message", "导入失败: " + e.getMessage()
                    ));
        }
    }


    /**
     * 通过cmdbId同步合并Uar数据
     * 从每日更新的全量数据表中获取数据
     * @param cmdbId
     */
    @LogOperation(module = "UAR周期设置", type = LogOperation.OperationType.SYNC, value = "数据同步到UserAccessReview表")
    @GetMapping("/local-merge-uar/{cmdbId}")
    public ResponseEntity<?> localMergeUarDoUpdate(@PathVariable String cmdbId) {
        try {
            boolean success = userAccessReviewService.localMergeUarDoUpdate(cmdbId);
            if (success) {
                return ok(
                        Map.of("message","更新成功", "success", true)
                );
            } else {
                return ok(
                        Map.of("message","数据更新失败", "success", false)
                );
            }
        } catch (Exception e) {
            return ok(
                    Map.of("message",e.getMessage(), "success", false)
            );
        }
    }


    @LogOperation(module = "直属经理待办任务", type = LogOperation.OperationType.QUERY, value = "查询直属经理待办任务")
    @GetMapping("/LineManagerReview/getAll")
    public ResponseEntity query(
            String itCodeOfUser, String appName, String cmdbId,
            String lineManager, String status, String lineManagersReviewDecision,
            String bpo, String systemRole, String accessLabel,
            String userCocType, String bpoCocType,
            @RequestParam(value = "page", defaultValue = "1") Integer page, @RequestParam(value = "size", defaultValue = "10") Integer size
    ) {
        UseAccessReviewBean useAccessReviewBean = new UseAccessReviewBean();
        useAccessReviewBean.setItCodeOfUser(itCodeOfUser);
        useAccessReviewBean.setCmdbId(cmdbId);
        useAccessReviewBean.setAppName(appName);
        useAccessReviewBean.setLineManager(lineManager);
        useAccessReviewBean.setLineManagerReviewStatus(status);
        useAccessReviewBean.setLineManagersReviewDecision(lineManagersReviewDecision);
        useAccessReviewBean.setBpo(bpo);
        useAccessReviewBean.setSystemRole(systemRole);
        useAccessReviewBean.setAccessLabel(accessLabel);
        useAccessReviewBean.setUserCocType(userCocType);
        useAccessReviewBean.setBpoCocType(bpoCocType);

        // 获取数据隔离范围
        List<String> dataRange = roleUtils.getCurrentUserBusinessDataRangeOfMgr(RoleUtils.ROLE_READ_PERMISSION);
        useAccessReviewBean.setDataRange(dataRange);


        return ok(lineMangerReviewService.query(useAccessReviewBean, page, size));
    }

    /**
     * 统计
     * Line Manager 最上方的（全部、未审核、已审核 的数字）
     */
    @LogOperation(module = "直属经理待办任务", type = LogOperation.OperationType.QUERY, value = "统计直属经理代办任务")
    @GetMapping("/LineManagerReview/UseAccessReviewStatistics")
    public ResponseEntity UseAccessReviewStatistics() {
        // 获取数据隔离范围
        List<String> dataRange = roleUtils.getCurrentUserBusinessDataRangeOfMgr(RoleUtils.ROLE_READ_PERMISSION);
        return ok(lineMangerReviewService.UseAccessReviewStatistics(dataRange));
    }

    /**
     * 统计当前用户的直属经理、BPO待办和已办任务。
     */
    @LogOperation(module = "首页任务统计", type = LogOperation.OperationType.QUERY, value = "统计当前用户待办和已办任务")
    @GetMapping("/task/statistics")
    public ResponseEntity taskStatistics() {
        return ok(taskStatisticsService.getTaskStatistics());
    }


    /**
     * 修改LineManagerReview
     */

    @LogOperation(module = "直属经理待办任务", type = LogOperation.OperationType.UPDATE, value = "直属经理审核用户权限(保留or移除)")
    @PutMapping("/LineManagerReview/updateUseAccessReview")
    public ResponseEntity updateLmAccessReview(@RequestBody BatchOperationRequest batchOperationRequest)  {
        batchOperationRequest.getFilters().setDataRange(roleUtils.getCurrentUserBusinessDataRangeOfMgr(RoleUtils.ROLE_WRITE_PERMISSION));
        return ok(lineMangerReviewService.updateLmAccessReviewStatus(batchOperationRequest));
    }


    /**
     * LineManagerReview导出excel
     *
     * @param response
     * @param itCodeOfUser
     * @param appName
     * @param lineManagersReviewDecision
     * @throws IOException
     */
    @LogOperation(module = "直属经理待办任务", type = LogOperation.OperationType.EXPORT, value = "直属经理待办任务-导出用户权限列表")
    @GetMapping("/LineManagerReview/exportExcel")
    public void exportExcel(
            HttpServletResponse response,
            String itCodeOfUser, String appName, String cmdbId,
            String lineManager, String lineManagersReviewDecision,
            String bpo, String systemRole, String accessLabel,
            String userCocType, String bpoCocType,
            String status, String language
    ) throws IOException {
        UseAccessReviewBean useAccessReviewBean = new UseAccessReviewBean();
        useAccessReviewBean.setLineManagerReviewStatus(status);
        useAccessReviewBean.setItCodeOfUser(itCodeOfUser);
        useAccessReviewBean.setAppName(appName);
        useAccessReviewBean.setCmdbId(cmdbId);
        useAccessReviewBean.setLineManagersReviewDecision(lineManagersReviewDecision);
        useAccessReviewBean.setLineManager(lineManager);
        useAccessReviewBean.setBpo(bpo);
        useAccessReviewBean.setSystemRole(systemRole);
        useAccessReviewBean.setAccessLabel(accessLabel);
        useAccessReviewBean.setUserCocType(userCocType);
        useAccessReviewBean.setBpoCocType(bpoCocType);

        // 获取数据隔离范围
        List<String> dataRange = roleUtils.getCurrentUserBusinessDataRangeOfMgr(RoleUtils.ROLE_READ_PERMISSION);
        useAccessReviewBean.setDataRange(dataRange);

        lineMangerReviewService.exportExcel(response, useAccessReviewBean, language);
    }


    @LogOperation(module = "直属经理待办任务", type = LogOperation.OperationType.IMPORT, value = "直属经理待办任务-导入审核后用户权限列表")
    @PostMapping("/LineManagerReview/importExcel")
    public ResponseEntity<?> importExcel(@RequestParam("file") MultipartFile file) {
        try {
            List<String> dataRange = roleUtils.getCurrentUserPersonalBusinessDataRangeOfMgr();
            ImportResult result = lineMangerReviewService.importExcel(file, dataRange);

            Map<String, Object> response = new HashMap<>();
            response.put("success", result.getErrorCount() == 0);
            response.put("message", result.getErrorCount() == 0 ? "导入成功" : "部分记录导入失败");
            response.put("successCount", result.getSuccessCount());
            response.put("errorCount", result.getErrorCount());

            if (result.getErrorCount() > 0) {
                response.put("errors", result.getErrors());
                return ResponseEntity.ok().body(response);
            }

            return ok(response);

        } catch (IOException e) {
            return ResponseEntity.ok(Map.of(
                            "success", false,
                            "message", "文件处理失败: " + e.getMessage()
                    ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok(Map.of(
                            "success", false,
                            "message", "导入失败: " + e.getMessage()
                    ));
        }
    }

    /**
     * uar的Line Manager Review的Application下拉选
     *
     * @return
     */
    @GetMapping("/LineManagerReview/getDistinctApplication")
    @LogOperation(module = "数据处理", type = LogOperation.OperationType.QUERY, value = "获取去重应用名称")
    public ResponseEntity getDistinctApplication(String lineManagerReviewStatus,String itCodeOfUser,String lineManagersReviewDecision) {

        return ok(lineMangerReviewService.getDistinctApplication(lineManagerReviewStatus,itCodeOfUser,lineManagersReviewDecision));
    }


    /**
     * 统计
     * BPO 最上方的（全部、未审核、已审核 的数字）
     */
    @LogOperation(module = "BPO待办任务", type = LogOperation.OperationType.QUERY, value = "统计BPO待办任务")
    @GetMapping("/BPOReview/BPOStatistics")
    public ResponseEntity BPOStatistics() {
        // 获取数据隔离范围
        Map<String, List<String>> dataRangeMap = roleUtils.getCurrentUserBusinessDataRangeOfBpo(RoleUtils.ROLE_READ_PERMISSION);
        return ok(bpoService.BPOStatistics(dataRangeMap));
    }

    /**
     * BPO用户Application的下拉列表
     * @return
     */
    @LogOperation(module = "BPO待办任务", type = LogOperation.OperationType.QUERY, value = "获取BPO用户Application的下拉列表")
    @GetMapping("/BPOReview/getDistinctApplication")
    public ResponseEntity BPODistinctApplication(String bpoReviewStatus,String itCodeOfUser,String bpoReviewDecision) {

        return ok(bpoService.getDistinctApplication(bpoReviewStatus,itCodeOfUser,bpoReviewDecision));
    }


    @LogOperation(module = "业务流程负责人待办任务", type = LogOperation.OperationType.QUERY, value = "查询BPO待办任务")
    @GetMapping("/BPOReview/getAll")
    public ResponseEntity BPOquery(
            String itCodeOfUser, String cmdbId, String appName,
            String bpo, String status, String bpoReviewDecision,
            String lineManager, String lineManagerReviewStatus, String lineManagersReviewDecision,
            String systemRole, String accessLabel,
            String userCocType, String bpoCocType,
            @RequestParam(value = "page", defaultValue = "1") Integer page, @RequestParam(value = "size", defaultValue = "10") Integer size
    ) {
        UseAccessReviewBean useAccessReviewBean = new UseAccessReviewBean();
        useAccessReviewBean.setItCodeOfUser(itCodeOfUser);
        useAccessReviewBean.setCmdbId(cmdbId);
        useAccessReviewBean.setAppName(appName);
        useAccessReviewBean.setBpo(bpo);
        useAccessReviewBean.setBpoReviewStatus(status);
        useAccessReviewBean.setBpoReviewDecision(bpoReviewDecision);
        useAccessReviewBean.setLineManager(lineManager);
        useAccessReviewBean.setLineManagerReviewStatus(lineManagerReviewStatus);
        useAccessReviewBean.setLineManagersReviewDecision(lineManagersReviewDecision);
        useAccessReviewBean.setSystemRole(systemRole);
        useAccessReviewBean.setAccessLabel(accessLabel);
        useAccessReviewBean.setUserCocType(userCocType);
        useAccessReviewBean.setBpoCocType(bpoCocType);
        // 获取数据隔离范围
        useAccessReviewBean.setDataRangeMap(roleUtils.getCurrentUserBusinessDataRangeOfBpo(RoleUtils.ROLE_READ_PERMISSION));

        return ok(bpoService.query(useAccessReviewBean, page, size));
    }

    /**
     *   BPO模块的批量修改
     */
    @LogOperation(module = "业务流程负责人待办任务", type = LogOperation.OperationType.UPDATE, value = "BPO审核用户权限(保留or移除)")
    @PutMapping("/BPOReview/updateBPOReview")
    public ResponseEntity updateBPOReview(@RequestBody BatchOperationRequest batchOperationRequest)  {
        batchOperationRequest.getFilters().setDataRangeMap(roleUtils.getCurrentUserBusinessDataRangeOfBpo(RoleUtils.ROLE_WRITE_PERMISSION));
        return ok(bpoService.updateBpoAccessReviewStatus(batchOperationRequest));
    }


    /**
     * BPO Review Page导出excel
     *
     * @param response
     * @param itCodeOfUser
     * @param appName
     * @param bpoReviewDecision
     * @throws IOException
     */
    @LogOperation(module = "业务流程负责人待办任务", type = LogOperation.OperationType.EXPORT, value = "业务流程负责人待办任务-导出用户权限列表")
    @GetMapping("/BPOReview/exportExcel")
    public void BPOexportExcel(
            HttpServletResponse response,
            String itCodeOfUser, String appName, String cmdbId,
            String bpo, String bpoReviewDecision,
            String lineManager, String lineManagerReviewStatus, String lineManagersReviewDecision,
            String systemRole, String accessLabel,
            String userCocType, String bpoCocType,
            String status, String language
    ) throws IOException {

        UseAccessReviewBean useAccessReviewBean = new UseAccessReviewBean();
        useAccessReviewBean.setBpoReviewStatus(status);
        useAccessReviewBean.setItCodeOfUser(itCodeOfUser);
        useAccessReviewBean.setAppName(appName);
        useAccessReviewBean.setCmdbId(cmdbId);
        useAccessReviewBean.setBpo(bpo);
        useAccessReviewBean.setBpoReviewDecision(bpoReviewDecision);
        useAccessReviewBean.setLineManager(lineManager);
        useAccessReviewBean.setLineManagerReviewStatus(lineManagerReviewStatus);
        useAccessReviewBean.setLineManagersReviewDecision(lineManagersReviewDecision);
        useAccessReviewBean.setSystemRole(systemRole);
        useAccessReviewBean.setAccessLabel(accessLabel);
        useAccessReviewBean.setUserCocType(userCocType);
        useAccessReviewBean.setBpoCocType(bpoCocType);
        // 获取数据隔离范围
        useAccessReviewBean.setDataRangeMap(roleUtils.getCurrentUserBusinessDataRangeOfBpo(RoleUtils.ROLE_READ_PERMISSION));

        bpoService.exportExcel(response, useAccessReviewBean, language);
    }

    @LogOperation(module = "业务流程负责人待办任务", type = LogOperation.OperationType.IMPORT, value = "业务流程负责人待办任务-导入审核后用户权限列表")
    @PostMapping("/BPOReview/importBPOExcel")
    public ResponseEntity<?> importBPOExcel(@RequestParam("file") MultipartFile file) {
        try {
            Map<String, List<String>> dataRangeMap =
                    roleUtils.getCurrentUserPersonalBusinessDataRangeOfBpo();
            ImportResult result = bpoService.importBPOExcel(file, dataRangeMap);

            Map<String, Object> response = new HashMap<>();
            response.put("success", result.getErrorCount() == 0);
            response.put("message", result.getErrorCount() == 0 ? "导入成功" : "部分记录导入失败");
            response.put("successCount", result.getSuccessCount());
            response.put("errorCount", result.getErrorCount());

            if (result.getErrorCount() > 0) {
                response.put("errors", result.getErrors());
                return ResponseEntity.ok(response);
            }

            return ok(response);

        } catch (IOException e) {
            return ResponseEntity.ok(Map.of(
                            "success", false,
                            "message", "文件处理失败: " + e.getMessage()
                    ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                            "success", false,
                            "message", "导入失败: " + e.getMessage()
                    ));
        }
    }

    /**
     * 统计
     * My Review 最上方的（全部、未审核、已审核 的数字）
     */
    @LogOperation(module = "我的权限清单", type = LogOperation.OperationType.QUERY, value = "统计我的权限清单")
    @GetMapping("/Myaccess/MyaccessStatistics")
    public ResponseEntity MyaccessStatistics() {
        String itCodeOfUser = SecurityUtils.getCurrentUserId();
        return ok(myAccessService.myAccessStatistics(itCodeOfUser));
    }


    @LogOperation(module = "我的权限清单", type = LogOperation.OperationType.QUERY, value = "查询我的权限清单")
    @GetMapping("/Myaccess/getAll")
    public ResponseEntity Myaccessquery(
            String itCodeOfUser, String appName, String status,String finalReviewDecision,
            String cmdbId, String accessLabel,
            @RequestParam(value = "page", defaultValue = "1") Integer page, @RequestParam(value = "size", defaultValue = "10") Integer size
    ) {
        UseAccessReviewBean useAccessReviewBean = new UseAccessReviewBean();
        useAccessReviewBean.setItCodeOfUser(SecurityUtils.getCurrentUserId());
        useAccessReviewBean.setCmdbId(cmdbId);
        useAccessReviewBean.setAppName(appName);
        useAccessReviewBean.setLineManagerReviewStatus(status);
        useAccessReviewBean.setFinalReviewDecision(finalReviewDecision);
        useAccessReviewBean.setAccessLabel(accessLabel);

        return ok(myAccessService.query(useAccessReviewBean, page, size));
    }
    /**
     * My Access导出excel
     *
     * @param response
     * @param itCodeOfUser
     * @param appName
     * @param finalReviewDecision
     * @throws IOException
     */
    @LogOperation(module = "我的权限清单", type = LogOperation.OperationType.EXPORT, value = "导出我的权限清单")
    @GetMapping("/Myaccess/exportExcel")
    public void exportMyAccessExcel(
            HttpServletResponse response,
            String itCodeOfUser,
            String cmdbId,
            String appName,
            String finalReviewDecision,
            String status,
            String accessLabel,
            String language
    ) throws IOException {
        UseAccessReviewBean useAccessReviewBean = new UseAccessReviewBean();
        useAccessReviewBean.setItCodeOfUser(SecurityUtils.getCurrentUserId());
        useAccessReviewBean.setCmdbId(cmdbId);
        useAccessReviewBean.setAppName(appName);
        useAccessReviewBean.setFinalReviewDecision(finalReviewDecision);
        useAccessReviewBean.setLineManagerReviewStatus(status);
        useAccessReviewBean.setAccessLabel(accessLabel);
        List<UserAccessReview> reviews = myAccessService.getAllLMyAccessReview(useAccessReviewBean, language);


        // 根据语言选择表头、文件名和工作表名
        String[] headers;
        String fileName;
        String sheetName;

        if ("CN".equalsIgnoreCase(language)) {
            // 中文版表头
            headers = new String[]{
                    "应用编号", "应用名称", "用户 ITcode", "用户角色", "角色描述", "角色分类",
                    "直属经理", "直属经理审核状态", "直属经理审核结果",
                    "BPO", "BPO 审核状态", "BPO 审核结果",
                    "最终审核结果", "最终审核结果原因", "审核结果更新日期"
            };
            fileName = "我的权限清单";
            sheetName = "我的权限清单";
        } else {
            // 英文版表头（默认）
            headers = new String[]{
                    "Application ID", "System Name", "User ITcode", "User Role Name", "Role Description", "Role Classification",
                    "Line Manager","Line Manager Review Status", "Line Manager Review Result",
                    "BPO", "BPO Review Status", "BPO Review Result",
                    "Final Review Result", "Final Review Result Reason", "Final Review Result Update Date"
            };
            fileName = "My_Access";
            sheetName = "My_Access";
        }

        // 定义实体类字段名（与实体类属性一致）
        String[] fields = {
                "cmdbId", "appName", "itCodeOfUser", "systemRole", "roleDescription", "accessLabel",
                "lineManager", "lineManagerReviewStatus", "lineManagersReviewDecision",
                "bpo", "bpoReviewStatus", "bpoReviewDecision",
                "finalReviewDecision", "finalReviewResultReason", "finalReviewTime"
        };

        exportReviewToExcel(
                response,
                fileName,
                sheetName,
                reviews,
                headers,
                fields,
                null,
                language
        );
    }

    /**
     * 我的权限清单下拉列表
     * @return
     */
    @LogOperation(module = "我的权限清单", type = LogOperation.OperationType.QUERY, value = "我的权限清单下拉列表")
    @GetMapping("/Myaccess/getDistinctApplication")
    public ResponseEntity MyaccessDistinctApplication(String finalReviewStatus,String finalReviewDecision) {
        String itCodeOfUser = SecurityUtils.getCurrentUserId();
        return ok(myAccessService.getDistinctApplication(finalReviewStatus,itCodeOfUser,finalReviewDecision));
    }


    /**
     * UarProcessorReview导出excel 针对于 line manager band =1 的
     *
     * @param response
     * @param itCodeOfUser
     * @param appName
     * @param lineManagersReviewDecision
     * @throws IOException
     */
    @LogOperation(module = "SVP及以上直属经理待办任务", type = LogOperation.OperationType.EXPORT, value = "导出需手工处理数据 (LM为SVP及以上)")
    @GetMapping("/LineManagerBandReview/exportExcel")
    public void LineManagerBandReviewExportExcel(
            HttpServletResponse response, String itCodeOfUser, String appName, String cmdbId,
            String lineManagersReviewDecision, String status, String accessLabel,
            String language
    ) throws IOException {
        UseAccessReviewBean useAccessReviewBean = new UseAccessReviewBean();
        useAccessReviewBean.setItCodeOfUser(itCodeOfUser);
        useAccessReviewBean.setAppName(appName);
        useAccessReviewBean.setCmdbId(cmdbId);
        useAccessReviewBean.setLineManagersReviewDecision(lineManagersReviewDecision);
        useAccessReviewBean.setLineManagerReviewStatus(status);
        useAccessReviewBean.setAccessLabel(accessLabel);

        // 获取数据隔离范围
        List<String> dataRange = roleUtils.getCurrentUserUarBusinessDataRange();
//        if (dataRange.isEmpty()) {
//            return ;
//        }
        useAccessReviewBean.setDataRange(dataRange);

        // 传入language参数
        List<UserAccessReview> reviews = lineManagerBandReviewService.getAllLineManagerBandReview(useAccessReviewBean, language);

        // 根据语言选择表头、文件名和工作表名
        String[] headers;
        String fileName;
        String sheetName;
        String headerTips;

        if ("CN".equalsIgnoreCase(language)) {
            // 中文版表头
            headers = new String[]{
                    "序列号", "应用编号", "应用名称", "用户名", "用户角色", "角色描述", "系统编号", "系统描述",
                    "用户 ITcode", "用户账号", "姓名", "角色分类",
                    "直属经理", "BPO", "BPO 审核状态", "BPO 审核结果", "BPO 实际审核人", "BPO 实际审核时间",
                    "您的审核状态", "您的审核结果", "直属经理实际审核人", "直属经理实际审核时间"
            };
            fileName = "待处理数据（LineManager为SVP及以上）";
            sheetName = "待处理数据（LineManager为SVP及以上）";
            headerTips = "为避免误操作修改，我们已锁定A列至S列。请在T列选择您的审核意见 (保留或移除)。";
        } else {
            // 英文版表头（默认）
            headers = new String[]{
                    "Sequence Number", "Application ID", "System Name", "User Name",  "User Role Name", "Role Description", "System ID", "System Description",
                    "User ITcode","User Account", "User Full Name", "Role Classification",
                    "Line Manager", "BPO", "BPO Review Status", "BPO Review Result", "BPO Actual Reviewer", "BPO Actual Review Time",
                    "Your Review Status", "Your Review Result", "Line Manager Actual Reviewer", "Line Manager Actual Review Time"
            };
            fileName = "Pending_Data(for-LM：SVP&Above)";
            sheetName = "Pending Data";
            headerTips = "Please note that columns A through S have been locked to prevent accidental modification.Please select your review results (keep/remove) in Column T.";
        }

        // 定义实体类字段名（与实体类属性一致）
        String[] fields = {
                "sequenceNumber", "cmdbId", "appName", "userName",  "systemRole", "roleDescription", "leitSystemId", "leitSystemName",
                "itCodeOfUser", "userId", "userRealName", "accessLabel",
                "lineManager", "bpo", "bpoReviewStatus", "bpoReviewDecision", "bpoReviewItcode", "bpoReviewTime",
                "lineManagerReviewStatus", "lineManagersReviewDecision", "lineManagerReviewItcode", "lineManagerReviewTime"
        };

        exportReviewToExcel(
                response,
                fileName,
                sheetName,
                reviews,
                headers,
                fields,
                headerTips,
                language
        );
    }


    /**
     * UarProcessorReview导入excel 针对于 line manager band =1 的
     *
     * @param file
     * @throws IOException
     */
    @LogOperation(module = "SVP及以上直属经理待办任务", type = LogOperation.OperationType.IMPORT, value = "导入手工已处理结果 (LM为SVP及以上)")
    @PostMapping("/LineManagerBandReview/importExcel")
    public ResponseEntity<?> importLineManagerBandExcel(@RequestParam("file") MultipartFile file) {
        try {
            ImportResult result = lineManagerBandReviewService.importExcel(file);

            Map<String, Object> response = new HashMap<>();
            response.put("success", result.getErrorCount() == 0);
            response.put("message", result.getErrorCount() == 0 ? "导入成功" : "部分记录导入失败");
            response.put("successCount", result.getSuccessCount());
            response.put("errorCount", result.getErrorCount());

            if (result.getErrorCount() > 0) {
                response.put("errors", result.getErrors());
                return ResponseEntity.ok(response);
            }

            return ok(response);

        } catch (IOException e) {
            return ResponseEntity.ok(Map.of(
                            "success", false,
                            "message", "文件处理失败: " + e.getMessage()
                    ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok(Map.of(
                            "success", false,
                            "message", "导入失败: " + e.getMessage()
                    ));
        }
    }


    /**
     * 修改BPO信息
     *
     * @param sequenceNumber
     * @param bpoInfo
     * @return
     */
    @LogOperation(module = "BPO待办任务", type = LogOperation.OperationType.UPDATE, value = "修改BPO信息")
    @PutMapping("/bpoInfo/{sequenceNumber}")
    public ResponseEntity<?> updateBpoInfo(@PathVariable String sequenceNumber, @RequestBody BpoInfoBean bpoInfo) {
        if (sequenceNumber == null || sequenceNumber.trim().isEmpty()) {
            return ResponseEntity.ok().body(
                    Map.of(
                            "success", false,
                            "message", "sequenceNumber 不能为空"
                    )
            );
        }
        bpoInfo.setSequenceNumber(sequenceNumber);
        try {
            boolean updated = bpoService.updateBpoInfo(bpoInfo);
            if (updated) {
                return ResponseEntity.ok(
                        Map.of(
                                "success", true,
                                "message", "BPO信息更新成功"
                        )
                );
            } else {
                return ResponseEntity.ok().body(
                        Map.of(
                                "success", false,
                                "message", "BPO信息未找到"
                        )
                );
            }
         } catch (Exception e) {
            return ResponseEntity.ok().body(
                    Map.of(
                            "success", false,
                            "message", "BPO信息更新失败: " + e.getMessage()
                    )
            );
        }
    }

    /**
     * 导入BPO信息
     *
     * @param file
     * @return
     */
    @LogOperation(module = "UAR结果", type = LogOperation.OperationType.IMPORT, value = "导入BPO信息")
    @PostMapping("/BPOReview/importBpoInfo")
    public ResponseEntity<?> importBpoInfo(@RequestParam("file") MultipartFile file) {
        try {
            ImportResult result = bpoService.importBPOInfoExcel(file);

            Map<String, Object> response = new HashMap<>();
            response.put("success", result.getErrorCount() == 0);
            response.put("message", result.getErrorCount() == 0 ? "导入成功" : "部分记录导入失败");
            response.put("successCount", result.getSuccessCount());
            response.put("errorCount", result.getErrorCount());

            if (result.getErrorCount() > 0) {
                response.put("errors", result.getErrors());
                return ResponseEntity.ok().body(response);
            }

            return ok(response);

        } catch (IOException e) {
            return ResponseEntity.ok()
                    .body(Map.of(
                            "success", false,
                            "message", "文件处理失败: " + e.getMessage()
                    ));
        } catch (Exception e) {
            return ResponseEntity.ok()
                    .body(Map.of(
                            "success", false,
                            "message", "导入失败: " + e.getMessage()
                    ));
        }
    }

    /**
     * 导出BPO信息模板
     */
    @LogOperation(module = "UAR结果", type = LogOperation.OperationType.QUERY, value = "导出BPO信息")
    @GetMapping("/BPOReview/exportBpoInfo")
    public void exportBpoInfo(
            HttpServletResponse response,
            UseAccessReviewBean useAccessReviewBean,
            @RequestParam(defaultValue = "CN") String language
    ) throws IOException {
        useAccessReviewBean.setDataRange(roleUtils.getCurrentUserUarBusinessDataRange());
        List<UserAccessReview> reviews = userAccessReviewService.getAllHistoryRecords(useAccessReviewBean, language);

        // 根据语言设置表头
        String[] headers;
        String fileName;
        String sheetName;

        if ("CN".equalsIgnoreCase(language)) {
            headers = new String[]{
                    "序列号", "应用编号", "应用名称", "用户名", "用户 ITcode","用户角色", "角色描述",
                    "BPO", "BPO邮箱"

            };
            fileName = "BPO信息模板";
            sheetName = "BPO信息模板";
        } else {
            headers = new String[]{
                    "Sequence Number", "Application ID", "System Name", "User Name", "User ITcode","User Role Name", "Role Description",
                    "BPO", "BPO Email"
            };
            fileName = "BPO_Info_Template";
            sheetName = "BPO_Info_Template";
        }

        // 定义字段映射
        String[] fields = {
                "sequenceNumber", "cmdbId", "appName", "userName", "itCodeOfUser", "systemRole", "roleDescription",
                "bpo", "bpoEmail"
        };

        // 调用导出方法
        ExcelUtil.exportToExcel(response, fileName, sheetName, reviews, headers, fields, List.of(7, 8), true);

    }


    /**
     * 根据应用ID和BPO获取去重的系统角色
     *
     * @param cmdbId
     * @param bpo
     * @return
     */
    @LogOperation(module = "UAR数据查询", type = LogOperation.OperationType.QUERY, value = "根据应用ID和BPO获取去重的系统角色")
    @GetMapping("/getDistinctSystemRoles")
    public ResponseEntity<List<String>> getDistinctSystemRoles(
            String cmdbId,
            String bpo
    ) {
        if (cmdbId == null || cmdbId.trim().isEmpty() || bpo == null || bpo.trim().isEmpty()) {
            return ResponseEntity.ok(
                    Collections.emptyList()
            );
        }
        return ok(userAccessReviewService.getDistinctSystemRolesByCmdbId(cmdbId, bpo));
    }

}
