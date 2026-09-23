package com.lenovo.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.bean.AdvancedSearchBean;
import com.lenovo.bean.EmailFailedBean;
import com.lenovo.bean.ImportResult;
import com.lenovo.bean.UseAccessReviewBean;
import com.lenovo.config.LogOperation;
import com.lenovo.config.RequiresPermission;
import com.lenovo.config.Logical;
import com.lenovo.entity.UserAccessReview;
import com.lenovo.security.utils.RoleUtils;
import com.lenovo.security.utils.SecurityUtils;
import com.lenovo.security.utils.StringUtils;
import com.lenovo.service.UserAccessReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import com.lenovo.util.ExcelUtil;
import org.springframework.web.multipart.MultipartFile;

import static org.springframework.http.ResponseEntity.ok;


@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/useAccessReview")
public class UseAccessReviewStatusController
{
    private final UserAccessReviewService userAccessReviewService;
    private final RoleUtils roleUtils;

    //校验待发送邮件是否有高管邮件,以后会加到验证弹窗那，先放这吧
    /*SELECT *
    FROM allow_send_email_include_lm_and_bpo
    WHERE
    line_manager_email IN (SELECT email FROM ad_tb_upp_nature_2 WHERE band_flag = '1')
    OR
    bpo_email IN (SELECT email FROM ad_tb_upp_nature_2 WHERE band_flag = '1');*/

    /**
     * @Description TODO 第一接口(待发送列表查询):Pending
     * @author wangfenglong
     * @date 2025/11/27 09:54
     **/
    @GetMapping("/pending/query")
    @RequiresPermission(roles = {"View_Only_IT", "UAR_Admin_IT", "UAR_System_Admin_IT", "UAR_Processer"}, logical = Logical.OR)
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "查询待发送邮件")
    public ResponseEntity<?> queryTempData(
    @RequestParam(required = false) String sequenceNumber, @RequestParam(required = false) String itCodeOfUser,
    @RequestParam(required = false) String appName, @RequestParam(required = false) String systemRole, @RequestParam(required = false) String lineManagerReviewStatus,
    @RequestParam(required = false) String bpoReviewStatus, @RequestParam(required = false) String overallSendStatus, @RequestParam(required = false) String bpo,
    @RequestParam(required = false) String lineManager,@RequestParam(required = false) String cmdbId,@RequestParam(required = false)String lineManagerLevelCode,@RequestParam(required = false)String dept,
    @RequestParam(value = "page", defaultValue = "1") Integer page, @RequestParam(value = "size", defaultValue = "10") Integer size)
    {
        try
        {
            UseAccessReviewBean bean = new UseAccessReviewBean();
            bean.setSequenceNumber(sequenceNumber);
            bean.setItCodeOfUser(itCodeOfUser);
            bean.setAppName(appName);
            bean.setSystemRole(systemRole);
            bean.setLineManagerReviewStatus(lineManagerReviewStatus);
            bean.setBpoReviewStatus(bpoReviewStatus);
            bean.setOverallSendStatus(overallSendStatus);
            bean.setBpo(bpo);
            bean.setLineManager(lineManager);
            bean.setCmdbId(cmdbId);
            bean.setLineManagerLevelCode(lineManagerLevelCode);
            bean.setDept(dept);

            return getAllSendData("Pending",bean, page, size);
        }
        catch (Exception e)
        {
            log.error("第一接口:查询待发送邮件数据时发生错误:{}", e.getMessage(),e);
            return ResponseEntity.ok(Map.of( "success", false, "message", "第一接口(待发送列表查询)时发生错误:" + e.getMessage()));

        }
    }


    /**
     * @Description TODO 第二接口(发送中列表查询):Sending(目前已经不在使用)
     * @author wangfenglong
     * @date 2025/11/27 10:16
    **/
    @GetMapping("/sending/query")
    @RequiresPermission(roles = {"View_Only_IT", "UAR_Admin_IT", "UAR_System_Admin_IT", "UAR_Processer"}, logical = Logical.OR)
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "查询发送中邮件")
    public ResponseEntity<?> querySendingRecords(
    @RequestParam(value = "page", defaultValue = "1") Integer page,
    @RequestParam(value = "size", defaultValue = "10") Integer size,
    @RequestParam(required = false) String sequenceNumber, @RequestParam(required = false) String itCodeOfUser,
    @RequestParam(required = false) String appName, @RequestParam(required = false) String systemRole, @RequestParam(required = false) String lineManagerReviewStatus,
    @RequestParam(required = false) String bpoReviewStatus, @RequestParam(required = false) String overallSendStatus, @RequestParam(required = false) String bpo,
    @RequestParam(required = false) String lineManager,@RequestParam(required = false) String cmdbId,@RequestParam(required = false)String lineManagerLevelCode,@RequestParam(required = false)String dept)
    {
        try
        {
            UseAccessReviewBean bean = new UseAccessReviewBean();
            bean.setSequenceNumber(sequenceNumber);
            bean.setItCodeOfUser(itCodeOfUser);
            bean.setAppName(appName);
            bean.setSystemRole(systemRole);
            bean.setLineManagerReviewStatus(lineManagerReviewStatus);
            bean.setBpoReviewStatus(bpoReviewStatus);
            bean.setOverallSendStatus(overallSendStatus);
            bean.setBpo(bpo);
            bean.setLineManager(lineManager);
            bean.setCmdbId(cmdbId);
            bean.setLineManagerLevelCode(lineManagerLevelCode);
            bean.setDept(dept);


            return getAllSendData("Sending",bean, page, size);
        }
        catch (Exception e)
        {
            log.error("第二接口(发送中列表查询):查询Sending状态记录异常:",e);
            return ResponseEntity.ok(Map.of( "success", false, "message", "第二接口(发送中列表查询):查询Sending状态记录异常:" + e.getMessage()));

        }
    }

    /**
     * @Description TODO 第三接口（已发送列表查询）:Sent
     * @author wangfenglong
     * @date 2025/11/27 12:19
    **/
    @GetMapping("/sent/query")
    @RequiresPermission(roles = {"View_Only_IT", "UAR_Admin_IT", "UAR_System_Admin_IT", "UAR_Processer"}, logical = Logical.OR)
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "查询已发送邮件")
    public ResponseEntity<?> querySentRecords(
    @RequestParam(value = "page", defaultValue = "1") Integer page, @RequestParam(value = "size", defaultValue = "10") Integer size,
    @RequestParam(required = false) String sequenceNumber, @RequestParam(required = false) String itCodeOfUser, @RequestParam(required = false) String appName,
    @RequestParam(required = false) String systemRole, @RequestParam(required = false) String lineManagerReviewStatus, @RequestParam(required = false) String bpoReviewStatus,
    @RequestParam(required = false) String overallSendStatus, @RequestParam(required = false) String bpo, @RequestParam(required = false) String lineManager,
    @RequestParam(required = false) String cmdbId,@RequestParam(required = false)String lineManagerLevelCode,@RequestParam(required = false)String dept)
    {
        try
        {
            UseAccessReviewBean bean = new UseAccessReviewBean();
            bean.setSequenceNumber(sequenceNumber);
            bean.setItCodeOfUser(itCodeOfUser);//要注意
            bean.setAppName(appName);
            bean.setSystemRole(systemRole);
            bean.setLineManagerReviewStatus(lineManagerReviewStatus);
            bean.setBpoReviewStatus(bpoReviewStatus);
            bean.setOverallSendStatus(overallSendStatus);
            bean.setBpo(bpo);
            bean.setLineManager(lineManager);
            bean.setCmdbId(cmdbId);
            bean.setLineManagerLevelCode(lineManagerLevelCode);
            bean.setDept(dept);

            return getAllSendData("Sent",bean, page, size);
        }
        catch (Exception e)
        {
            log.error("第三接口(已发送列表查询):查询Sent状态记录异常", e);
            return ResponseEntity.ok(Map.of( "success", false, "message", "第三接口(已发送列表查询):查询Sent状态记录异常:" + e.getMessage()));

        }
    }

    /**
     * @Description  第四接口（发送失败列表查询）:Failed
     * @author wangfenglong
     * @date 2025/11/27 12:22
    **/
    @GetMapping("/failed/query")
    @RequiresPermission(roles = {"View_Only_IT", "UAR_Admin_IT", "UAR_System_Admin_IT", "UAR_Processer"}, logical = Logical.OR)
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "查询发送失败邮件")
    public ResponseEntity<?> queryFailedRecords(
    @RequestParam(value = "page", defaultValue = "1") Integer page, @RequestParam(value = "size", defaultValue = "10") Integer size,
    @RequestParam(required = false) String sequenceNumber, @RequestParam(required = false) String itCodeOfUser, @RequestParam(required = false) String appName,
    @RequestParam(required = false) String systemRole, @RequestParam(required = false) String lineManagerReviewStatus, @RequestParam(required = false) String bpoReviewStatus,
    @RequestParam(required = false) String overallSendStatus, @RequestParam(required = false) String bpo, @RequestParam(required = false) String lineManager,
    @RequestParam(required = false) String cmdbId,@RequestParam(required = false)String lineManagerLevelCode,@RequestParam(required = false)String dept)
    {
        try
        {
            UseAccessReviewBean  bean = new UseAccessReviewBean();
            bean.setSequenceNumber(sequenceNumber);
            bean.setItCodeOfUser(itCodeOfUser);//要注意
            bean.setAppName(appName);
            bean.setSystemRole(systemRole);
            bean.setLineManagerReviewStatus(lineManagerReviewStatus);
            bean.setBpoReviewStatus(bpoReviewStatus);
            bean.setOverallSendStatus(overallSendStatus);
            bean.setBpo(bpo);
            bean.setLineManager(lineManager);
            bean.setCmdbId(cmdbId);
            bean.setLineManagerLevelCode(lineManagerLevelCode);
            bean.setDept(dept);

            return getAllSendData("Failed",bean, page, size);
        }
        catch (Exception e)
        {
            log.error("第四接口(失败邮件列表查询):查询Failed状态记录异常", e);
            return ResponseEntity.ok(Map.of( "success", false, "message", "第四接口(失败邮件列表查询):查询Failed状态记录异常:" + e.getMessage()));

        }
    }


    /**
     * @Description  UAR发送页面-查询待发送，发送成功，发送失败数据公共方法
     * @author wangfenglong
     * @date 2026/2/4 13:56
    **/
    public ResponseEntity<Page<UserAccessReview>> getAllSendData(String overallSendStatus,UseAccessReviewBean bean, Integer page, Integer size)
    {
        //分页参数合法性校验
        int validPage = page < 1 ? 1 : page;
        int validSize = size < 1 ? 10 : Math.min(size, 100);

        //处理多选的应用编号
        List<String> cmdbList;
        if (!StringUtils.isEmpty(bean.getCmdbId()))
        {
            cmdbList = Arrays.stream(bean.getCmdbId().split(","))
            .map(String::trim)
            .filter(trimmedId -> !trimmedId.isEmpty())
            .collect(Collectors.toList());
            bean.setCmdbIdListOfChoose(cmdbList);
        }

        //处理多选的应用名称
        List<String> appNameList;
        if (!StringUtils.isEmpty(bean.getAppName()))
        {
            appNameList = Arrays.stream(bean.getAppName().split(","))
            .map(String::trim)
            .filter(trimmedId -> !trimmedId.isEmpty())
            .collect(Collectors.toList());
            bean.setAppNameListOfChoose(appNameList);
        }

        EmailFailedBean roleBean = userAccessReviewService.getCurrentUserRole();
        String roleFlag = roleBean.getRoleFlag();
        if("3".equals(roleFlag))
        {
            bean.setCmdbIdList(roleBean.getCmdbIdList());
        }
        Page<UserAccessReview> result = userAccessReviewService.queryBySendStatus(bean,overallSendStatus, page, size,roleFlag);
        return ResponseEntity.ok(result);
    }


    /**
     * @Description TODO 第五接口：Sending发送中列表应用名称去重（发送中页面已经不存在了）
     * @author wangfenglong
     * @date 2025/11/27 12:27
    **/
    @GetMapping("/sending/distinctApplication")
    @RequiresPermission(roles = {"View_Only_IT", "UAR_Admin_IT", "UAR_System_Admin_IT", "UAR_Processer"}, logical = Logical.OR)
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "查询发送中邮件去重应用名称列表")
    public ResponseEntity<?> getSendingDistinctApplication()
    {
        try
        {
            List<AdvancedSearchBean> result = getDistinctApplication("Sending");
            return ResponseEntity.ok(result);
        }
        catch (Exception e)
        {
            log.error("第五接口：获取Sending状态去重应用名称失败", e);
            return ResponseEntity.ok(Map.of( "success", false, "message", "第五接口：获取Sending状态去重应用名称失败"));

        }
    }

    /**
     * @Description TODO 第六接口：Sent已发送列表应用名称去重
     * @author wangfenglong
     * @date 2025/11/27 12:28
    **/
    @GetMapping("/sent/distinctApplication")
    @RequiresPermission(roles = {"View_Only_IT", "UAR_Admin_IT", "UAR_System_Admin_IT", "UAR_Processer"}, logical = Logical.OR)
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "查询已发送邮件去重应用名称列表")
    public ResponseEntity<?> getSentDistinctApplication()
    {
        try
        {
            List<AdvancedSearchBean> result = getDistinctApplication("Sent");
            return ResponseEntity.ok(result);
        }
        catch (Exception e)
        {
            log.error("第六接口：获取Sent状态去重应用名称失败", e);
            return ResponseEntity.ok(Map.of( "success", false, "message", "第六接口：获取Sent状态去重应用名称失败"));

        }
    }

    /**
     * @Description TODO 第七接口：Failed发送失败列表应用名称去重
     * @author wangfenglong
     * @date 2025/11/27 12:29
    **/
    @GetMapping("/failed/distinctApplication")
    @RequiresPermission(roles = {"View_Only_IT", "UAR_Admin_IT", "UAR_System_Admin_IT", "UAR_Processer"}, logical = Logical.OR)
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "查询发送失败邮件去重应用名称列表")
    public ResponseEntity<?> getFailedDistinctApplication()
    {
        try
        {
            List<AdvancedSearchBean> result = getDistinctApplication("Failed");
            return ResponseEntity.ok(result);
        }
        catch (Exception e)
        {
            log.error("第七接口：获取Failed状态去重应用名称失败", e);
            return ResponseEntity.ok(Map.of( "success", false, "message", "第七接口：获取Failed状态去重应用名称失败"));

        }
    }

    /**
     * @Description TODO 第八接口：Pending待发送列表应用名称去重
     * @author wangfenglong
     * @date 2025/11/27 09:49
     **/
    @GetMapping("/pending/distinctApplication")
    @RequiresPermission(roles = {"View_Only_IT", "UAR_Admin_IT", "UAR_System_Admin_IT", "UAR_Processer"}, logical = Logical.OR)
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "查询待发送页面去重应用名称列表")
    public ResponseEntity<?> getPendingDistinctApplication()
    {

        try
        {
            List<AdvancedSearchBean> result = getDistinctApplication("Pending");
            return ResponseEntity.ok(result);
        }
        catch (Exception e)
        {
            log.error("第八接口：获取Pending状态去重应用名称失败", e);
            return ResponseEntity.ok(Map.of( "success", false, "message", "第八接口：获取Pending状态去重应用名称失败"));

        }
    }

    /**
     * @Description TODO UAR发送页面-获取应用名称去重公共方法
     * @author wangfenglong
     * @date 2025/12/12 17:03
    **/
    private List<AdvancedSearchBean> getDistinctApplication(String overallSendStatus)
    {

        //String roleFlag = "1";
        EmailFailedBean roleBean = userAccessReviewService.getCurrentUserRole();
        String roleFlag = roleBean.getRoleFlag();
        UseAccessReviewBean bean = new UseAccessReviewBean();
        if("3".equals(roleFlag))
        {
            bean.setCmdbIdList(roleBean.getCmdbIdList());
        }
        return userAccessReviewService.getDistinctApplicationBySendStatus(roleFlag, overallSendStatus,bean);
    }


    /**
     * @Description TODO UAR发送--待发送数量
     * @author wangfenglong
     * @date 2025/12/13 17:12
     **/
    @GetMapping("/pending/pendingCount")
    @RequiresPermission(roles = {"View_Only_IT", "UAR_Admin_IT", "UAR_System_Admin_IT", "UAR_Processer"}, logical = Logical.OR)
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "待发送页面--待发送数量")
    public ResponseEntity<?> getPendingCount()
    {
        try
        {
            int count = getCount("Pending");
            return ResponseEntity.ok(count);
        }
        catch (Exception e)
        {
            log.error("获取发送通知页面--待发送数量时发生异常", e);
            return ResponseEntity.ok(Map.of( "success", false, "message", "获取发送通知页面--待发送数量时发生异常"));

        }
    }


    /**
     * @Description TODO UAR发送--发送中数量
     * @author wangfenglong
     * @date 2025/11/30 12:51
     **/
    @GetMapping("/pending/sendingCount")
    @RequiresPermission(roles = {"View_Only_IT", "UAR_Admin_IT", "UAR_System_Admin_IT", "UAR_Processer"}, logical = Logical.OR)
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "待发送页面--发送中数量")
    public ResponseEntity<?> getSendingCount()
    {
        try
        {
            int count = getCount("Sending");
            return ResponseEntity.ok(count);
        }
        catch (Exception e)
        {
            log.error("获取发送通知页面--发送中数量时发生异常", e);
            return ResponseEntity.ok(Map.of( "success", false, "message", "获取发送通知页面--发送中数量时发生异常"));

        }
    }

    /**
     * @Description TODO UAR发送--已发送数量
     * @author wangfenglong
     * @date 2025/11/30 13:08
     **/
    @GetMapping("/pending/sentCount")
    @RequiresPermission(roles = {"View_Only_IT", "UAR_Admin_IT", "UAR_System_Admin_IT", "UAR_Processer"}, logical = Logical.OR)
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "待发送页面--已发送数量")
    public ResponseEntity<?> getSentCount()
    {
        try
        {
            int count = getCount("Sent");
            return ResponseEntity.ok(count);
        }
        catch (Exception e)
        {
            log.error("获取发送通知页面--已发送数量时发生异常：", e);
            return ResponseEntity.ok(Map.of( "success", false, "message", "获取发送通知页面--已发送数量时发生异常"));

        }
    }

    /**
     * @Description TODO UAR发送--发送失败数量
     * @author wangfenglong
     * @date 2025/11/30 13:08
     **/
    @GetMapping("/pending/failedCount")
    @RequiresPermission(roles = {"View_Only_IT", "UAR_Admin_IT", "UAR_System_Admin_IT", "UAR_Processer"}, logical = Logical.OR)
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "待发送页面--发送失败数量")
    public ResponseEntity<?> getFailedCount()
    {
        try
        {
            int count = getCount("Failed");
            return ResponseEntity.ok(count);
        }
        catch (Exception e)
        {
            log.error("获取发送通知页面--发送失败数量时发生异常：", e);
            return ResponseEntity.ok(Map.of( "success", false, "message", "获取发送通知页面--发送失败数量时发生异常"));
        }
    }

    /**
     * @Description
     * @author wangfenglong
     * @date 2025/12/13 16:40
     **/
    private Integer getCount(String overallSendStatus)
    {
        EmailFailedBean roleBean = userAccessReviewService.getCurrentUserRole();
        String roleFlag = roleBean.getRoleFlag();
        UseAccessReviewBean bean = new UseAccessReviewBean();
        if("3".equals(roleFlag))
        {
            bean.setCmdbIdList(roleBean.getCmdbIdList());
        }
        return userAccessReviewService.getSendEmailTotalCount(roleFlag,overallSendStatus,bean);
    }


    /**
     * UAR结果查询
     * 查询历史记录（overall_send_status不为Pending）
     */
    @GetMapping("/history/query")
    @LogOperation(module = "通知历史", type = LogOperation.OperationType.QUERY, value = "查询邮件通知历史记录")
    public ResponseEntity<?> queryHistoryRecords(
            UseAccessReviewBean useAccessReviewBean,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size)
    {
        useAccessReviewBean.setDataRange(roleUtils.getCurrentUserUarBusinessDataRange());

        try {
            Page<UserAccessReview> result = userAccessReviewService.queryHistoryRecords(page, size, useAccessReviewBean);
            log.info("查询历史记录成功: 页码={}, 大小={}, 结果数量={}", page, size, result.getRecords().size());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("查询历史记录失败", e);
            return ResponseEntity.ok(Map.of( "success", false, "message", "查询历史记录失败"));

        }
    }

    /**
     * @Description UAR结果导出 / 导出历史记录
     */
    @LogOperation(module = "UAR结果导出", type = LogOperation.OperationType.QUERY, value = "UAR结果导出-导出历史记录")
    @GetMapping("/history/exportExcel")
    public void historyExportExcel(
            HttpServletResponse response,
            UseAccessReviewBean useAccessReviewBean,
            @RequestParam(defaultValue = "CN") String language
    ) throws IOException {

        useAccessReviewBean.setDataRange(roleUtils.getCurrentUserUarBusinessDataRange());

        // 获取历史记录数据
        List<UserAccessReview> reviews = userAccessReviewService.getAllHistoryRecords(useAccessReviewBean, language);

        // 根据语言设置表头
        String[] headers;
        String fileName;
        String sheetName;

        if ("CN".equalsIgnoreCase(language)) {
            headers = new String[]{
                    "序列号", "应用编号", "应用名称", "用户名", "用户 ITcode","用户角色", "角色描述", "角色分类",
                    "直属经理", "直属经理邮箱", "直属经理审核状态", "直属经理审核结果", "直属经理实际审核人", "直属经理实际审核时间",
                    "BPO", "BPO邮箱", "BPO 审核状态", "BPO 审核结果", "BPO 实际审核人", "BPO 实际审核时间", "BPO 信息修改人", "BPO 信息修改时间",
                    "最终审核结果", "最终审核结果原因", "审核结果更新日期",
                    "单据号", "操作结果", "例外原因"

            };
            fileName = "历史审核记录";
            sheetName = "历史审核记录";
        } else {
            headers = new String[]{
                    "Sequence Number", "Application ID", "System Name", "User Name", "User ITcode","User Role Name", "Role Description", "Role Classification",
                    "Line Manager", "Line Manager Email", "Line Manager Review Status", "Line Manager Review Result", "Line Manager Actual Reviewer", "Line Manager Actual Review Time",
                    "BPO", "BPO Email", "BPO Review Status", "BPO Review Result", "BPO Actual Reviewer", "BPO Actual Review Time", "BPO Info Modifier", "BPO Info Modified Time",
                    "Final Review Result", "Final Review Result Reason", "Final Review Result Update Date",
                    "Ticket No.", "Op. Result", "Excep. Reason"
            };
            fileName = "History_Review_Records";
            sheetName = "History_Review_Records";
        }

        // 定义字段映射
        String[] fields = {
                "sequenceNumber", "cmdbId", "appName", "userName", "itCodeOfUser", "systemRole", "roleDescription", "accessLabel",
                "lineManager", "lineManagerEmail", "lineManagerReviewStatus", "lineManagersReviewDecision", "lineManagerReviewItcode", "lineManagerReviewTime",
                "bpo", "bpoEmail", "bpoReviewStatus", "bpoReviewDecision", "bpoReviewItcode", "bpoReviewTime", "bpoUpdateBy", "bpoUpdateTime",
                "finalReviewDecision", "finalReviewResultReason", "finalReviewTime",
                "exceptionTicketNo", "exceptionResultDecision", "exceptionReason"
        };

        // 调用导出方法
        ExcelUtil.exportToExcel(response, fileName, sheetName, reviews, headers, fields, List.of(25, 26, 27), true, List.of(26), language);
    }

    /**
     * @Description 导入UAR异常数据 (不改原数据)
     * @param file
     * @return
     */
    @LogOperation("UAR结果导出-导入异常记录")
    @PostMapping("/history/importUARExceptionProcessData")
    public ResponseEntity importUARExceptionProcessData(
            @RequestParam("file") MultipartFile file
    ) {
        try {
            List<String> dataRange = roleUtils.getCurrentUserUarBusinessDataRange();

            ImportResult result = userAccessReviewService.importUARExceptionProcessData(file, dataRange);

            Map<String, Object> response = new HashMap<>();
            response.put("success", result.getErrorCount() == 0);
            response.put("message", "Import successful "+result. getSuccessCount()+" records " +
                    (result.getErrorCount() > 0 ? "; " + result.getErrors() : "") );
            response.put("successCount", result.getSuccessCount());
            response.put("errorCount", result.getErrorCount());

            if (result.getErrorCount() > 0) {
                response.put("errors", result.getErrors());
                return ResponseEntity.ok().body(response);
            }

            log.info("Log-ImportUarExcel-Success " + result.getSuccessCount() + " total" +
                    (result.getErrorCount() > 0 ? "; " + result.getErrors(): "") );
            return ok(response);

        } catch (IOException e) {
            log.error("Log-ImportUarExcel-IOError", e);
            return ResponseEntity.ok()
                    .body(Map.of(
                            "success", false,
                            "message", "File processing failed: " + e.getMessage()
                    ));
        } catch (Exception e) {
            log.error("Log-ImportUarExcel-Error", e);
            return ResponseEntity.ok()
                    .body(Map.of(
                            "success", false,
                            "message", "Import failed: " + e.getMessage()
                    ));
        }
    }

}