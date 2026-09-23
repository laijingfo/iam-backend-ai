package com.lenovo.controller;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.bean.EmailFailedBean;
import com.lenovo.bean.UarMailTemplateBean;
import com.lenovo.bean.UseAccessReviewBean;
import com.lenovo.config.*;
import com.lenovo.dto.*;
import com.lenovo.entity.*;
import com.lenovo.mapper.*;
import com.lenovo.security.utils.SecurityUtils;
import com.lenovo.service.*;
import com.lenovo.service.impl.IEmailSendService;
import com.lenovo.service.impl.ISendLmOrBpoEmailService;
import com.lenovo.strategy.CheckStrategy;
import com.lenovo.strategy.SendEmailStrategyType;
import com.lenovo.strategy.factory.CheckStrategyFactory;
import com.lenovo.util.ExcelUtil;
import com.lenovo.util.I18nUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;


@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/uar-mail-templates")
public class UarMailTemplateController
{
    private final UarMailTemplateService uarMailTemplateService;
    private final UarMailService uarMailService;
    private final UserAccessReviewService userAccessReviewService;
    private final IEmailSendService emailSendService1;
    private final CheckStrategyFactory strategyFactory;
    private final UserAccessReviewMapper userAccessReviewMapper;
    private final AllowSendEmailForBpoMapper allowSendEmailForBpoMapper;
    private final AllowSendEmailForLmMapper allowSendEmailForLmMapper;
    private final AllowSendEmailForLmFailedMapper allowSendEmailForLmFailedMapper;
    private final AllowSendEmailForBpoFailedMapper allowSendEmailForBpoFailedMapper;
    private final AllowSendEmailForResultRemoveUserMapper allowSendEmailForResultRemoveUserMapper;
    private final ISendLmOrBpoEmailService sendLmOrBpoEmailService;
    private final PreSendMailService preSendMailService;

    /**
     * @Description TODO 模板管理页面--查询所有模版
     * @author wangfenglong
     * @date 2026/1/16 15:37
    **/
    @GetMapping
    @LogOperation(module = "模版管理", type = LogOperation.OperationType.QUERY, value = "模版管理_查询所有模版")
    public Page<UarMailTemplate> getAllTemplates(UarMailTemplateBean bean, @RequestParam(defaultValue = "1") Integer page, @RequestParam(defaultValue = "10") Integer size)
    {
        return uarMailTemplateService.query(bean, page, size);
    }

    /**
     * @Description TODO 根据模版id查询模版详情
     * @param id 模版ID
     * @author wangfenglong
     * @date 2026/1/30 16:39
    **/
    @GetMapping("/{id}")
    @LogOperation(module = "模版管理", type = LogOperation.OperationType.QUERY, value = "模版管理_查询选择的模版")
    public ResponseEntity<UarMailTemplate> getTemplateById(@PathVariable Long id)
    {
        UarMailTemplate template = uarMailTemplateService.getById(id);
        if (template != null)
        {
            return ResponseEntity.ok(template);
        }
        else
        {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * @Description TODO 模板管理页面--邮件模版新增
     * @author wangfenglong
     * @date 2025/12/18 13:57
    **/
    @PostMapping
    @LogOperation(module = "模版管理", type = LogOperation.OperationType.ADD, value = "模版管理_邮件模版新增")
    public ResponseEntity<?> createTemplate(@RequestBody UarMailTemplate template)
    {
        try
        {
            //设置操作人和操作时间（已在服务层处理）
            UarMailTemplate createdTemplate = uarMailTemplateService.createTemplateWithAutoVersion(template);
            return ResponseEntity.ok(createdTemplate);
        }
        catch (RuntimeException e)
        {
            log.error("创建模版失败:{}",e.getMessage(), e);
            return ResponseEntity.ok(Map.of( "success", false, "message", I18nUtil.get("common.fail") + e.getMessage()));
        }
        catch (Exception e)
        {
            log.error("创建模版失败:{}",e.getMessage(), e);
            return ResponseEntity.ok(Map.of( "success", false, "message",I18nUtil.get("common.fail") + e.getMessage()));

        }
    }

    @PutMapping("/{id}")
    @LogOperation(module = "模版管理", type = LogOperation.OperationType.UPDATE, value = "模版管理-邮件模版更新")
    public ResponseEntity<UarMailTemplate> updateTemplate(@PathVariable Long id, @RequestBody UarMailTemplate templateDetails)
    {
        UarMailTemplate existingTemplate = uarMailTemplateService.getById(id);
        if (existingTemplate != null)
        {
            templateDetails.setId(id);
            uarMailTemplateService.updateById(templateDetails);
            return ResponseEntity.ok(templateDetails);
        }
        else
        {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * @Description TODO 根据模版id删除模版
     * @param id 模版ID
     * @author wangfenglong
     * @date 2026/1/30 16:34
    **/
    @DeleteMapping("/{id}")
    @LogOperation(module = "模版管理", type = LogOperation.OperationType.DELETE, value = "模版管理_删除模版")
    public ResponseEntity<?> deleteTemplate(@PathVariable Long id)
    {
        /*if (uarMailTemplateService.removeById(id))
        {
            return ResponseEntity.ok().build();
        }
        else
        {
            return ResponseEntity.notFound().build();
        }*/

        try
        {
            Objects.requireNonNull(id, "id is null");
            UarMailTemplate template = new UarMailTemplate();
            template.setId(id);
            template.setTag("delete");
            template.setVersion("delete");
            template.setOperator(SecurityUtils.getCurrentUsername());
            template.setOperationDate(new Date());
            boolean result = uarMailTemplateService.updateById(template);
            if(result)
            {
                return ResponseEntity.ok().build();
            }
            else
            {
                return ResponseEntity.notFound().build();
            }
        }
        catch (Exception e)
        {
            log.error("删除模版失败:{}",e.getMessage(), e);
            return ResponseEntity.ok(Map.of( "success", false, "message", I18nUtil.get("common.fail") + e.getMessage()));
        }
    }

    @PostMapping("/{id}/preview")
    public String previewTemplate(@PathVariable Long id, @RequestBody Map<String, String> variables)
    {
        return uarMailTemplateService.previewTemplate(id, variables);
    }

    @PostMapping("/preview-custom")
    public String previewCustomTemplate(@RequestParam String content, @RequestBody Map<String, String> variables)
    {
        return uarMailTemplateService.previewTemplateWithCustomContent(content, variables);
    }

    @PostMapping("/{id}/preview-with-due-date")
    public String previewTemplateWithDueDate(@PathVariable Long id, @RequestBody Map<String, String> variables)
    {
        variables.put("itcode",SecurityUtils.getCurrentUsername());
        return uarMailTemplateService.previewTemplateWithDueDate(id, variables);
    }

    @PostMapping("/{id}/preview-with-stored-due-date")
    public String previewTemplateWithStoredDueDate(@PathVariable Long id)
    {
        Map<String, String> otherVariables=new HashMap<>();
        otherVariables.put("itcode",SecurityUtils.getCurrentUsername());
        return uarMailTemplateService.previewTemplateWithStoredDueDate(id, otherVariables);
    }

    @GetMapping("/{id}/due-date")
    public ResponseEntity<?> getDueDate(@PathVariable Long id)
    {
        UarMailTemplate template = uarMailTemplateService.getById(id);
        if (template == null)
        {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> response = new HashMap<>();
        response.put("dueDate", template.getDueDate());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/due-date")
    public ResponseEntity<?> setDueDate(@PathVariable Long id, @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date dueDate)
    {
        try
        {
            UarMailTemplate template = uarMailTemplateService.getById(id);
            if (template == null)
            {
                return ResponseEntity.notFound().build();
            }
            template.setDueDate(dueDate);
            template.setOperator(SecurityUtils.getCurrentUsername());
            template.setOperationDate(new Date());
            uarMailTemplateService.updateById(template);
            return ResponseEntity.ok().build();
        }
        catch (Exception e)
        {
            return ResponseEntity.ok(Map.of( "success", false, "message", "Failed to set due date: " + e.getMessage()));
        }
    }

    @GetMapping("/tag/{tag}")
    public ResponseEntity<?> getTemplatesByTag(@PathVariable String tag)
    {
        try
        {
            List<UarMailTemplate> templates = uarMailTemplateService.getTemplatesByTag(tag);
            return ResponseEntity.ok(templates);
        }
        catch (Exception e)
        {
            return ResponseEntity.ok(Map.of( "success", false, "message", "Failed to get templates: " + e.getMessage()));
        }
    }

    @GetMapping("/unique")
    public ResponseEntity<?> getTemplateByUniqueKey(@RequestParam(required = false) String tag, @RequestParam String toSomeone, @RequestParam String version)
    {
        try
        {
            UarMailTemplate template = uarMailTemplateService.getTemplateByUniqueKey(tag, toSomeone, version);
            if (template != null)
            {
                return ResponseEntity.ok(template);
            }
            else
            {
                return ResponseEntity.notFound().build();
            }
        }
        catch (Exception e)
        {
            return ResponseEntity.ok(Map.of( "success", false, "message", "Failed to get template: " + e.getMessage()));
        }
    }

    /**
     * 获取特定 tag 和 toSomeone 的所有版本
     */
    @GetMapping("/tag-to-someone")
    public ResponseEntity<?> getTemplatesByTagAndToSomeone(@RequestParam(required = false) String tag, @RequestParam String toSomeone)
    {
        try
        {
            List<UarMailTemplate> templates = uarMailTemplateService.getTemplatesByTagAndToSomeone(tag, toSomeone);
            return ResponseEntity.ok(templates);
        }
        catch (Exception e)
        {
            return ResponseEntity.ok(Map.of( "success", false, "message", "Failed to get templates: " + e.getMessage()));
        }
    }


    /**
     * 预览模板（带自定义banner）
     */
    @PostMapping("/{id}/preview-with-banner")
    public String previewTemplateWithBanner(@PathVariable Long id, @RequestParam(required = false) String bannerUrl, @RequestBody Map<String, String> variables)
    {
        return uarMailTemplateService.previewTemplateWithBanner(id, variables, bannerUrl);
    }









    //～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～//
    //～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～//
    //～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～//
    /**
     * @Description TODO 待发送页面--将需要发送的邮件写入到数据库给领导看
     * @author wangfenglong
     * @date 2026/1/9 16:34
     **/
    @PostMapping("/batch-send-insert-for-lead")
    @RequiresPermission(roles = {"View_Only_IT", "UAR_Admin_IT", "UAR_System_Admin_IT", "UAR_Processer"}, logical = Logical.OR)
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "待发送页面-将需要发送的邮件写入到数据库")
    public ResponseEntity<?> saveAllowSendEmailToLead(@RequestBody BatchSendRequest request)
    {
        try
        {
            List<UserAccessReview> selectedRecords = userAccessReviewService.getSelectedRecords(request, "1");
            if(CollectionUtil.isNotEmpty(selectedRecords))
            {
                emailSendService1.getAllowSendEmailForLead(selectedRecords);
                return ResponseEntity.ok(Map.of("success", true, "message", "允许发送的邮件正在写入到数据库中，请稍后查看吧"));
            }
            return ResponseEntity.ok(Map.of("success", false, "message", "selectedRecords是空，没有邮件需要写入到数据库"));
        }
        catch (Exception e)
        {
            log.error("待发送页面-将需要发送的邮件写入到数据库给领导看:出现异常:",e);
            return ResponseEntity.ok(Map.of("success", false, "message", "待发送页面-将需要发送的邮件写入到数据库:出现异常:"+ e));
        }
    }

    /**
     * @Description TODO 发送失败页面--发送通知校验
     * @author wangfenglong
     * @date 2025/11/27 14:45
     **/
    @PostMapping("/re-send-check")
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "发送失败页面-发送通知校验")
    public ResponseEntity<SendCheckResponse> checkReSendFailedEmails(@RequestBody SendCheckRequest request)
    {
        return checkNeedToSendEmail(request, "failSendEmail");
    }

    /**
     * @Description TODO 待发送页面--发送通知校验
     * @author wangfenglong
     * @date 2025/11/26 16:29
     **/
    @PostMapping("/send-check")
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "待发送页面-发送通知校验")
    public ResponseEntity<SendCheckResponse> checkEmailSend(@RequestBody SendCheckRequest request)
    {
        return checkNeedToSendEmail(request, "SendEmail");
    }

    /**
     * @Description TODO 校验发送数量(待发送页面和发送失败页面)
     * @author wangfenglong
     * @date 2026/1/27 16:31
     **/
    private ResponseEntity<SendCheckResponse> checkNeedToSendEmail(SendCheckRequest request,String sendFlag)
    {
        try
        {
            if (request.getRound() == null)
            {
                return ResponseEntity.ok().body(new SendCheckResponse(false, 0, 0, 0, 0));
            }
            if (Boolean.FALSE.equals(request.getIsALL()) && (request.getSequenceNumbers() == null || request.getSequenceNumbers().isEmpty()))
            {
                return ResponseEntity.ok().body(new SendCheckResponse(false, 0, 0, 0, 0));
            }
            log.info("{}-收到邮件发送检查请求: round={}, isALL={}, sequenceNumbers={}, filters={}", sendFlag,request.getRound(), request.getIsALL(), request.getSequenceNumbers(), request.getFilters());
            SendCheckResponse response = userAccessReviewService.checkEmailSend(request.getRound(), request.getIsALL(), request.getSequenceNumbers(), request.getFilters(), sendFlag);
            return ResponseEntity.ok(response);
        }
        catch (Exception e)
        {
            log.error("{}-邮件发送检查失败", sendFlag,e);
            return ResponseEntity.ok().body(new SendCheckResponse(false, 0, 0, 0, 0));
        }
    }
    //～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～//
    //～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～//
    //～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～//






    //💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗
    /**
     * @Description TODO 发送通知页面--更新待发送列表
     * @author wangfenglong
     * @date 2025/11/27 12:44
     **/
    @PostMapping("/refresh")
    @RequiresPermission(roles = {"View_Only_IT", "UAR_Admin_IT", "UAR_System_Admin_IT", "UAR_Processer"}, logical = Logical.OR)
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "更新待发送列表")
    public ResponseEntity<?> refreshData()
    {
        try
        {
            boolean success = userAccessReviewService.refreshData();
            if (success)
            {
                return ResponseEntity.ok(Map.of("success", true, "message", I18nUtil.get("common.success")));
            }
            return ResponseEntity.ok(Map.of("success", false, "message", I18nUtil.get("common.fail")));
        }
        catch (Exception e)
        {
            log.error("发送通知页面--更新待发送列表：刷新数据时发生异常：", e);
            return ResponseEntity.ok(Map.of("success", false, "message", I18nUtil.get("common.fail") + e.getMessage()));
        }
    }

    /**
     * @Description TODO 🍎🍎🍎待发送页面--将【最终审核是空的用户】写入到数据库 --{催办通知} 全量查询然后写入 allow_send_email_include_user
     * @param
     * @author wangfenglong
     * @date 2026/3/12 16:18
     **/
    @PostMapping("/batch-insert-user-for-lead")
    @RequiresPermission(roles = {"View_Only_IT", "UAR_Admin_IT", "UAR_System_Admin_IT", "UAR_Processer"}, logical = Logical.OR)
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "待发送页面-将最终审核是空的用户写入到数据库")
    public ResponseEntity<?> saveAllowSendUserEmailToLead(@RequestBody BatchSendRequest request)
    {
        try
        {
            Integer count = preSendMailService.generateReminderUserPreSendList();
            return ResponseEntity.ok(Map.of("success", true, "message", count + I18nUtil.get("send.email.msg1")));
        }
        catch (Exception e)
        {
            log.error("待发送页面-将需要发送的用户的邮件写入到数据库:出现异常:",e);
            return ResponseEntity.ok(Map.of("success", false, "message", I18nUtil.get("common.fail") + e));
        }
    }

    /**
     * @Description TODO 🍎🍎🍎待发送页面--发送【最终审核是空的用户】邮件 --{催办通知} 查询 allow_send_email_include_user表后发送
     * @param
     * @author wangfenglong
     * @date 2026/3/13 09:40
     **/
    @PostMapping("/send-userEmail-for-lead")
    @RequiresPermission(roles = {"View_Only_IT", "UAR_Admin_IT", "UAR_System_Admin_IT", "UAR_Processer"}, logical = Logical.OR)
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "待发送页面-发送【最终审核是空的用户】邮件")
    public ResponseEntity<?> sendUserEmailToLead(@RequestBody BatchSendRequest request)
    {
        try
        {
            CheckStrategy<Void,ResponseEntity<?>> strategy = strategyFactory.getStrategy(SendEmailStrategyType.USER_CYCLE_TYPE);
            return strategy.handle(request, null);
        }
        catch (Exception e)
        {
            log.error("待发送页面-发送用户邮件:出现异常:",e);
            return ResponseEntity.ok(Map.of("success", false, "message", I18nUtil.get("common.fail") + e));
        }
    }

    /**
     * @Description TODO 🪀🪀🪀待发送页面--发送【最终审核是移除的用户】邮件 --{最终结果通知}} 查询 allow_send_email_include_final_remove_user后发送
     * @author wangfenglong
     * @date 2026/5/9 15:35
     **/
    @PostMapping("/send-userEmail-for-finalRemove")
    @RequiresPermission(roles = {"View_Only_IT", "UAR_Admin_IT", "UAR_System_Admin_IT", "UAR_Processer"}, logical = Logical.OR)
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "待发送页面-发送最终审核是移除的用户邮件")
    public ResponseEntity<?> sendUserEmailForFinalRemove(@RequestBody BatchSendRequest request)
    {
        try
        {
            CheckStrategy<Void,ResponseEntity<?>> strategy = strategyFactory.getStrategy(SendEmailStrategyType.UAR_FINAL_REMOVE_USER_TYPE);
            return strategy.handle(request, null);
        }
        catch (Exception e)
        {
            log.error("待发送页面-发送用户邮件:出现异常:",e);
            return ResponseEntity.ok(Map.of("success", false, "message", I18nUtil.get("common.fail") + e));
        }
    }

    /**
     * @Description TODO 🟣🟣🟣 待发送页面--将[审核结果是remove的用户]写入到数据库 --{移除通知} 全量查询然后写入 allow_send_email_for_result_remove_user表
     * @param [request]
     * @author wangfenglong
     * @date 2026/6/25 14:18
    **/
    @PostMapping("/batch-insert-user-for-remove")
    @RequiresPermission(roles = {"View_Only_IT", "UAR_Admin_IT", "UAR_System_Admin_IT", "UAR_Processer"}, logical = Logical.OR)
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "待发送页面-将审核结果是remove的用户写入到数据库")
    public ResponseEntity<?> saveReviewResultIsRemoveUserToDatabase(@RequestBody BatchSendRequest request)
    {
        try
        {

            Integer count = preSendMailService.generateUserWillRemoveList();
            return ResponseEntity.ok(Map.of("success", true, "message", count + I18nUtil.get("send.email.msg1")));
        }
        catch (Exception e)
        {
            log.error("待发送页面-将需要发送的用户的邮件写入到数据库:出现异常:",e);
            return ResponseEntity.ok(Map.of("success", false, "message", I18nUtil.get("common.fail") + e));
        }
    }

    /**
     * @Description TODO 🟣🟣🟣 待发送页面--发送[审核结果是remove的用户]邮件 --{移除通知} 查询 allow_send_email_for_result_remove_user表后发送
     * @param [request]
     * @author wangfenglong
     * @date 2026/6/25 16:48
    **/
    @PostMapping("/send-userEmail-for-remove")
    @RequiresPermission(roles = {"View_Only_IT", "UAR_Admin_IT", "UAR_System_Admin_IT", "UAR_Processer"}, logical = Logical.OR)
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "待发送页面-发送[审核结果是remove的用户]邮件")
    public ResponseEntity<?> sendUserEmailForReviewResultIsRemove(@RequestBody BatchSendRequest request)
    {
        try
        {
            CheckStrategy<Void,ResponseEntity<?>> strategy = strategyFactory.getStrategy(SendEmailStrategyType.UAR_REVIEW_REMOVE_USER_SEND_TYPE);
            return strategy.handle(request, null);
        }
        catch (Exception e)
        {
            log.error("待发送页面-发送[审核结果是remove的用户]邮件:出现异常:",e);
            return ResponseEntity.ok(Map.of("success", false, "message", I18nUtil.get("common.fail") + e));
        }
    }

    /**
     * @Description TODO 待发送页面-发送[审核结果是remove的用户]邮件：获取审核结果是remove用户的邮件数量
     * @author wangfenglong
     * @date 2026/6/26 14:06
    **/
    @GetMapping("/getRemoveUserSendNum")
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "待发送页面-获取审核结果是remove的用户发送数量")
    public ResponseEntity<?> getRemoveUserSendNum()
    {
        Long count = preSendMailService.getPreSendUserWillRemoveCount();
        return ResponseEntity.ok(count);
    }

    /**
     * @Description TODO 🔵🔵🔵待发送页面--给lm发送邮件
     * @param [request]
     * @author wangfenglong
     * @date 2026/6/2 14:01
     **/
    @PostMapping("/batch-send-lineManager")
    @RequiresPermission(roles = {"View_Only_IT", "UAR_Admin_IT", "UAR_System_Admin_IT", "UAR_Processer"}, logical = Logical.OR)
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "待发送页面-给lm发送邮件")
    public ResponseEntity<?> batchSendEmailsLineManager(@RequestBody BatchSendRequest request)
    {
        try
        {
            log.info("待发送页面-给lineManager发送邮件-发送邮件开始:");
            CheckStrategy<String,ResponseEntity<?>> strategy = strategyFactory.getStrategy(SendEmailStrategyType.UAR_LM_SEND_TYPE);
            return strategy.handle(request, "1");
        }
        catch (Exception e)
        {
            log.error("待发送页面-给lineManager发送邮件-发送邮件：批量发送邮件任务提交失败:{}",e.getMessage(),e);
            return ResponseEntity.ok(Map.of("success", false, "message", I18nUtil.get("common.fail") + e.getMessage()));
        }
    }

    /**
     * @Description TODO 🔵🔵🔵待发送页面--给bpo发送邮件
     * @param [request]
     * @author wangfenglong
     * @date 2026/6/3 12:38
     **/
    @PostMapping("/batch-send-bpo")
    @RequiresPermission(roles = {"View_Only_IT", "UAR_Admin_IT", "UAR_System_Admin_IT", "UAR_Processer"}, logical = Logical.OR)
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "待发送页面-给bpo发送邮件")
    public ResponseEntity<?> batchSendEmailsBpo(@RequestBody BatchSendRequest request)
    {
        try
        {
            log.info("待发送页面-给bpo发送邮件-发送邮件开始:");
            CheckStrategy<String,ResponseEntity<?>> strategy = strategyFactory.getStrategy(SendEmailStrategyType.UAR_BPO_SEND_TYPE);
            return strategy.handle(request, "1");
        }
        catch (Exception e)
        {
            log.error("待发送页面-给BPO发送邮件-发送邮件：批量发送邮件任务提交失败:{}",e.getMessage(),e);
            return ResponseEntity.ok(Map.of("success", false, "message", I18nUtil.get("common.fail") + e.getMessage()));
        }
    }

    /**
     * @Description TODO 待发送页面--lm信息写入到数据库
     * @param [response]
     * @author wangfenglong
     * @date 2026/6/3 12:43
    **/
    @PostMapping("/batch-insert-lineManager")
    @RequiresPermission(roles = {"View_Only_IT", "UAR_Admin_IT", "UAR_System_Admin_IT", "UAR_Processer"}, logical = Logical.OR)
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "待发送页面-将需要发送的lm邮件写入到数据库")
    public ResponseEntity<?> batchInsertLineManagerToDb(@RequestBody MailSendRequest request)
    {
        try
        {
            preSendMailService.generateLmPreSendList(request);
            return ResponseEntity.ok(Map.of("success", true, "message", I18nUtil.get("common.success")));
        }
        catch (Exception e)
        {
            log.error("batch-insert-lm:",e);
            return ResponseEntity.ok(Map.of("success", false, "message", I18nUtil.get("common.fail")));
        }

    }

    /**
     * @Description TODO 待发送页面--bpo信息写入到数据库
     * @param [response]
     * @author wangfenglong
     * @date 2026/6/3 13:14
    **/
    @PostMapping("/batch-insert-bpo")
    @RequiresPermission(roles = {"View_Only_IT", "UAR_Admin_IT", "UAR_System_Admin_IT", "UAR_Processer"}, logical = Logical.OR)
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "待发送页面-将需要发送的bpo邮件写入到数据库")
    public ResponseEntity<?> batchInsertBpoToDb(@RequestBody MailSendRequest request)
    {
        try
        {
            preSendMailService.generateBpoPreSendList(request);
            return ResponseEntity.ok(Map.of("success", true, "message", I18nUtil.get("common.success")));
        }
        catch (Exception e)
        {
            log.error("batch-insert-bpo:",e);
            return ResponseEntity.ok(Map.of("success", false, "message", I18nUtil.get("common.fail")));
        }
    }

    /**
     * @Description TODO 待发送页面--导出BPO预发送用户数据到Excel
     * @author wangfenglong
     * @date 2026/6/4 11:11
    **/
    @GetMapping("/exportBpoUserData")
    @RequiresPermission(roles = {"View_Only_IT", "UAR_Admin_IT", "UAR_System_Admin_IT", "UAR_Processer"}, logical = Logical.OR)
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "待发送页面-将需要发送的bpo用户数据到Excel")
    public void exportBpoUserData(HttpServletResponse response,String language) throws Exception
    {
        QueryWrapper<AllowSendEmailForBpo> queryWrapper = new QueryWrapper<>();
        List<AllowSendEmailForBpo> records = allowSendEmailForBpoMapper.selectList(queryWrapper);
        String[] headers;
        String[] fields;
        if ("CN".equals(language))
        {
            headers = new String[]{"ID", "UUID", "流水号", "用户IT编码", "用户名", "应用名称", "部门", "UAR编号", "CMDB编号", "BPO姓名", "BPO邮箱", "BPO审核状态", "BPO职级编辑标识", "抄送邮箱", "创建时间", "更新时间"};
            fields = new String[]{"id", "uuid", "sequenceNumber", "itCodeOfUser", "userName", "appName", "department", "uarId", "cmdbId", "bpo", "bpoEmail", "bpoReviewStatus", "bpoBandEdFlag", "ccEmail", "createTime", "updateTime"};
            ExcelUtil.exportToExcel(response, "BPO预发送数据", "BPO预发送用户数据", records, headers, fields, null, false);
        }
        else
        {
            headers = new String[]{"ID", "UUID", "Sequence Number", "User IT Code", "User Name", "App Name", "Department", "UAR ID", "CMDB ID", "BPO", "BPO Email", "BPO Review Status", "BPO Band Ed Flag", "CC Email", "Create Time", "Update Time"};
            fields = new String[]{"id", "uuid", "sequenceNumber", "itCodeOfUser", "userName", "appName", "department", "uarId", "cmdbId", "bpo", "bpoEmail", "bpoReviewStatus", "bpoBandEdFlag", "ccEmail", "createTime", "updateTime"};
            ExcelUtil.exportToExcel(response, "BPO Pre-Send Data", "BPO Pre-Send User Data", records, headers, fields, null, false);
        }
    }

    /**
     * @Description TODO 待发送页面--导出LM预发送用户数据到Excel
     * @author wangfenglong
     * @date 2026/6/4 11:13
    **/
    @GetMapping("/exportLmUserData")
    @RequiresPermission(roles = {"View_Only_IT", "UAR_Admin_IT", "UAR_System_Admin_IT", "UAR_Processer"}, logical = Logical.OR)
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "待发送页面-将需要发送的lm用户数据到Excel")
    public void exportLmUserData(HttpServletResponse response, String language) throws Exception
    {
        QueryWrapper<AllowSendEmailForLm> queryWrapper = new QueryWrapper<>();
        List<AllowSendEmailForLm> records = allowSendEmailForLmMapper.selectList(queryWrapper);
        String[] headers;
        String[] fields;
        if ("CN".equals(language))
        {
            headers = new String[]{"ID", "UUID", "流水号", "用户IT编码", "用户名", "应用名称", "部门", "UAR编号", "CMDB编号", "直属经理姓名", "直属经理邮箱", "抄送邮箱", "直属经理审核状态", "标识Lm是否高管", "经理职级编码", "创建时间", "更新时间"};
            fields = new String[]{"id", "uuid", "sequenceNumber", "itCodeOfUser", "userName", "appName", "department", "uarId", "cmdbId", "lineManager", "lineManagerEmail", "ccEmail", "lineManagerReviewStatus", "lineManagerBandEdFlag", "lineManagerLevelCode", "createTime", "updateTime"};
            ExcelUtil.exportToExcel(response, "LM预发送数据", "LM预发送用户数据", records, headers, fields, null, false);
        }
        else
        {
            headers = new String[]{"ID", "UUID", "Sequence Number", "User IT Code", "User Name", "App Name", "Department", "UAR ID", "CMDB ID", "Line Manager", "Line Manager Email", "CC Email", "Line Manager Review Status", "Flag for Line Manager Band", "Line Manager Level Code", "Create Time", "Update Time"};
            fields = new String[]{"id", "uuid", "sequenceNumber", "itCodeOfUser", "userName", "appName", "department", "uarId", "cmdbId", "lineManager", "lineManagerEmail", "ccEmail", "lineManagerReviewStatus", "lineManagerBandEdFlag", "lineManagerLevelCode", "createTime", "updateTime"};
            ExcelUtil.exportToExcel(response, "LM Pre-Send Data", "LM Pre-Send User Data", records, headers, fields, null, false);
        }
    }

    @GetMapping("/getBpoSendNum")
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "待发送页面-获取BPO发送数量")
    public ResponseEntity<?> getBpoSendNum()
    {
        Long count = preSendMailService.getPreSendBpoCount();
        return ResponseEntity.ok(count);
    }

    @GetMapping("/getLmSendNum")
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "待发送页面-获取LM发送数量")
    public ResponseEntity<?> getLmSendNum()
    {
        Long count = preSendMailService.getPreSendLmCount();
        return ResponseEntity.ok(count);
    }

    @PostMapping("/send-lm-check")
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "待发送页面-lm发送通知校验")
    public ResponseEntity<?> checkEmailSendForLm(@RequestBody SendCheckRequest request)
    {
        try
        {
            Long count = preSendMailService.getPreSendLmCount();
            int lineManagerEmailCount = count.intValue();
            int bpoEmailCount = 0;
            int totalEmailCount = count.intValue();
            boolean exceedsLimit = lineManagerEmailCount > 100000;
            SendCheckResponse response = new SendCheckResponse(exceedsLimit, lineManagerEmailCount, bpoEmailCount, totalEmailCount, totalEmailCount);
            return ResponseEntity.ok(response);
        }
        catch (Exception e)
        {
            return ResponseEntity.ok(Map.of("success", false, "message", I18nUtil.get("common.fail") + e));
        }
    }

    @PostMapping("/send-bpo-check")
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "待发送页面-bpo发送通知校验")
    public ResponseEntity<?> checkEmailSendForBpo(@RequestBody SendCheckRequest request)
    {
        try
        {
            Long count = preSendMailService.getPreSendBpoCount();
            int lineManagerEmailCount = 0;
            int bpoEmailCount = count.intValue();
            int totalEmailCount = count.intValue();
            boolean exceedsLimit = bpoEmailCount > 100000;
            SendCheckResponse response = new SendCheckResponse(exceedsLimit, lineManagerEmailCount, bpoEmailCount, totalEmailCount, totalEmailCount);
            return ResponseEntity.ok(response);
        }
        catch (Exception e)
        {
            return ResponseEntity.ok(Map.of("success", false, "message", I18nUtil.get("common.fail") + e));
        }
    }
    //💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗💗




    //🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞
    /**
     * @Description TODO 发送失败页面-将需要发送的lm邮件写入到数据库
     * @param [request]
     * @author wangfenglong
     * @date 2026/6/10 09:35
    **/
    @PostMapping("/batch-insert-lineManager-for-failed")
    @RequiresPermission(roles = {"View_Only_IT", "UAR_Admin_IT", "UAR_System_Admin_IT", "UAR_Processer"}, logical = Logical.OR)
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "发送失败页面-将需要发送的lm邮件写入到数据库")
    public ResponseEntity<?> batchInsertLineManagerToDbForFailed(@RequestBody BatchSendRequest request)
    {
        try
        {
            userAccessReviewMapper.truncateAllowSendEmailForLmFailed();
            List<UserAccessReview> selectedRecords = sendLmOrBpoEmailService.getLmSelectedRecords(request, "2");
            if(CollectionUtil.isNotEmpty(selectedRecords))
            {
                emailSendService1.saveBpoAndLmInfoToDb("lm",selectedRecords,"2",request);
                return ResponseEntity.ok(Map.of("success", true, "message", I18nUtil.get("send.email.msg1")));
            }
            return ResponseEntity.ok(Map.of("success", false, "message", I18nUtil.get("send.email.msg4")));
        }
        catch (Exception e)
        {
            log.error("发送失败页面-将需要发送的邮件写入到数据库给领导看:出现异常:",e);
            return ResponseEntity.ok(Map.of("success", false, "message", I18nUtil.get("common.fail") + e));
        }
    }

    /**
     * @Description TODO 发送失败页面-将需要发送的bpo邮件写入到数据库
     * @param [request]
     * @author wangfenglong
     * @date 2026/6/10 09:35
    **/
    @PostMapping("/batch-insert-bpo-for-failed")
    @RequiresPermission(roles = {"View_Only_IT", "UAR_Admin_IT", "UAR_System_Admin_IT", "UAR_Processer"}, logical = Logical.OR)
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "发送失败页面-将需要发送的bpo邮件写入到数据库")
    public ResponseEntity<?> batchInsertBpoToDbForFailed(@RequestBody BatchSendRequest request)
    {
        try
        {
            userAccessReviewMapper.truncateAllowSendEmailForBpoFailed();
            List<UserAccessReview> selectedRecords = sendLmOrBpoEmailService.getBpoSelectedRecords(request, "2");
            if(CollectionUtil.isNotEmpty(selectedRecords))
            {
                emailSendService1.saveBpoAndLmInfoToDb("bpo",selectedRecords,"2",request);
                return ResponseEntity.ok(Map.of("success", true, "message", I18nUtil.get("send.email.msg1")));
            }
            return ResponseEntity.ok(Map.of("success", false, "message", I18nUtil.get("send.email.msg4")));
        }
        catch (Exception e)
        {
            log.error("发送失败页面-将需要发送的邮件写入到数据库给领导看:出现异常:",e);
            return ResponseEntity.ok(Map.of("success", false, "message", I18nUtil.get("common.fail") + e));
        }
    }

    /**
     * @Description TODO 发送失败页面-lm发送通知校验
     * @param [request]
     * @author wangfenglong
     * @date 2026/6/10 09:36
    **/
    @GetMapping("/send-lm-check-for-failed")
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "发送失败页面-lm发送通知校验")
    public ResponseEntity<?> checkEmailSendForLmForFailed()
    {
        try
        {
            QueryWrapper<AllowSendEmailForLmFailed> queryWrapper = new QueryWrapper<>();
            Long count = allowSendEmailForLmFailedMapper.selectCount(queryWrapper);
            int lineManagerEmailCount = count.intValue();
            int bpoEmailCount = 0;
            int totalEmailCount = count.intValue();
            boolean exceedsLimit = lineManagerEmailCount > 100000;
            SendCheckResponse response = new SendCheckResponse(exceedsLimit, lineManagerEmailCount, bpoEmailCount, totalEmailCount, totalEmailCount);
            return ResponseEntity.ok(response);
        }
        catch (Exception e)
        {
            return ResponseEntity.ok(Map.of("success", false, "message", I18nUtil.get("common.fail") + e));
        }
    }

    /**
     * @Description TODO 发送失败页面-bpo发送通知校验
     * @param [request]
     * @author wangfenglong
     * @date 2026/6/10 09:36
    **/
    @GetMapping("/send-bpo-check-for-failed")
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "发送失败页面-bpo发送通知校验")
    public ResponseEntity<?> checkEmailSendForBpoForFailed()
    {
        try
        {
            QueryWrapper<AllowSendEmailForBpoFailed> queryWrapper = new QueryWrapper<>();
            Long count = allowSendEmailForBpoFailedMapper.selectCount(queryWrapper);
            int lineManagerEmailCount = 0;
            int bpoEmailCount = count.intValue();
            int totalEmailCount = count.intValue();
            boolean exceedsLimit = bpoEmailCount > 100000;
            SendCheckResponse response = new SendCheckResponse(exceedsLimit, lineManagerEmailCount, bpoEmailCount, totalEmailCount, totalEmailCount);
            return ResponseEntity.ok(response);
        }
        catch (Exception e)
        {
            return ResponseEntity.ok(Map.of("success", false, "message", I18nUtil.get("common.fail") + e));
        }
    }

    /**
     * @Description TODO 发送失败页面-给lm发送邮件
     * @param [request]
     * @author wangfenglong
     * @date 2026/6/9 16:27
    **/
    @PostMapping("/batch-send-lineManager-for-failed")
    @RequiresPermission(roles = {"View_Only_IT", "UAR_Admin_IT", "UAR_System_Admin_IT", "UAR_Processer"}, logical = Logical.OR)
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "发送失败页面-给lineManager发送邮件")
    public ResponseEntity<?> batchSendEmailsLineManagerForFailed(@RequestBody BatchSendRequest request)
    {
        try
        {
            log.info("发送失败页面-给lineManager发送邮件-发送邮件开始:");
            CheckStrategy<String,ResponseEntity<?>> strategy = strategyFactory.getStrategy(SendEmailStrategyType.UAR_LM_SEND_TYPE);
            return strategy.handle(request, "2");
        }
        catch (Exception e)
        {
            log.error("发送失败页面-给lineManager发送邮件-发送邮件：批量发送邮件任务提交失败:{}",e.getMessage(),e);
            return ResponseEntity.ok(Map.of("success", false, "message", I18nUtil.get("common.fail") + e.getMessage()));
        }
    }

    /**
     * @Description TODO 发送失败页面-给bpo发送邮件
     * @param [request]
     * @author wangfenglong
     * @date 2026/6/9 16:47
    **/
    @PostMapping("/batch-send-bpo-for-failed")
    @RequiresPermission(roles = {"View_Only_IT", "UAR_Admin_IT", "UAR_System_Admin_IT", "UAR_Processer"}, logical = Logical.OR)
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "发送失败页面-给bpo发送邮件")
    public ResponseEntity<?> batchSendEmailsBpoForFailed(@RequestBody BatchSendRequest request)
    {
        try
        {
            log.info("发送失败页面-给bpo发送邮件-发送邮件开始:");
            CheckStrategy<String,ResponseEntity<?>> strategy = strategyFactory.getStrategy(SendEmailStrategyType.UAR_BPO_SEND_TYPE);
            return strategy.handle(request, "2");
        }
        catch (Exception e)
        {
            log.error("发送失败页面-给BPO发送邮件-发送邮件：批量发送邮件任务提交失败:{}",e.getMessage(),e);
            return ResponseEntity.ok(Map.of("success", false, "message", I18nUtil.get("common.fail") + e.getMessage()));
        }
    }
    //🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞🌞




    /**
     * @Description TODO 待发送页面-将【最终审核是移除的用户】写入到数据库 --{最终结果通知} 全量查询然后写入allow_send_email_include_final_remove_user表
     * @param [request]
     * @author wangfenglong
     * @date 2026/5/9 14:32
    **/
    @PostMapping("/batch-insert-user-for-finalRemove")
    @RequiresPermission(roles = {"View_Only_IT", "UAR_Admin_IT", "UAR_System_Admin_IT", "UAR_Processer"}, logical = Logical.OR)
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "待发送页面-将最终审核是移除的用户写入到数据库")
    public ResponseEntity<?> saveAllFinalRemoveUserToDatabase(@RequestBody BatchSendRequest request)
    {
        try
        {
            Integer count = preSendMailService.generateFinalRemoveUserPreSendList();
            return ResponseEntity.ok(Map.of("success", true, "message", count + I18nUtil.get("send.email.msg1")));
        }
        catch (Exception e)
        {
            log.error("待发送页面-将需要发送的最终被移除的用户邮件写入到数据库:出现异常:",e);
            return ResponseEntity.ok(Map.of("success", false, "message", I18nUtil.get("common.fail") + e));
        }
    }


    /**
     * @Description  TODO  🪀🪀🪀待发送页面-将【最终审核是移除的用户】表格导入到数据库 --{最终结果通知}
     * @author wangfenglong
     * @date 2026/5/9 14:32
     **/
    @PostMapping("/batch-import-user-for-finalRemove")
    @LogOperation(module = "邮件发送", type = LogOperation.OperationType.QUERY, value = "待发送页面-将最终审核是移除的用户导入到数据库")
    public ResponseEntity<?> importAllFinalRemoveUserToDatabase(@RequestParam("file") MultipartFile file)
    {
        try
        {
            List<String> employees = new ArrayList<>();

            try (
                    InputStream is = file.getInputStream();
                    Workbook workbook = createWorkbook(is, file.getOriginalFilename())
            )
            {
                Sheet sheet = workbook.getSheetAt(0);
                // 从指定行开始遍历
                for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null || isEmptyRow(row)) continue;
                    employees.add(getCellValueAsString(row.getCell(0)).toLowerCase());
                }
            }
            int total = employees.size();

            if (employees.isEmpty())
            {
                return ResponseEntity.ok(Map.of("success", false, "message", I18nUtil.get("send.email.error1")));
            }
            Map<String, String> result = userAccessReviewService.relatedLeaderAndSave(employees);
            String message = "文件读取到[" + total + "]条数据，导入成功：["+ result.get("success") + "]条数据";
            if (result.containsKey("fail") )
            {
                message += "，失败：["+ result.get("fail") + "]条数据，ITCode：" + result.get("failUser");
            }
            log.info("将最终审核是移除的用户导入到数据库: {}", message);
            return ResponseEntity.ok(Map.of("success", true, "message", message));
        }
        catch (Exception e)
        {
            log.error("待发送页面-将需要发送的最终被移除的用户邮件写入到数据库:出现异常:",e);
            return ResponseEntity.ok(Map.of("success", false, "message", I18nUtil.get("common.fail") + e));
        }
    }

    private Workbook createWorkbook(InputStream is, String filename) throws IOException
    {
        if (filename != null && filename.toLowerCase().endsWith(".xls")) {
            return new HSSFWorkbook(is);  // Excel 97-2003
        } else {
            return new XSSFWorkbook(is);  // Excel 2007+
        }
    }

    /**
     * 判断行是否为空
     */
    private boolean isEmptyRow(Row row)
    {
        if (row == null) return true;
        for (int i = row.getFirstCellNum(); i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && !getCellValueAsString(cell).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取单元格字符串值（处理所有类型）
     */
    private String getCellValueAsString(Cell cell)
    {
        if (cell == null) return "";

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                // 处理数字转字符串，避免科学计数法
                double num = cell.getNumericCellValue();
                if (num == (long) num) {
                    return String.valueOf((long) num);
                }
                return BigDecimal.valueOf(num).stripTrailingZeros().toPlainString();
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    return cell.getStringCellValue();
                }
            case BLANK:
                return "";
            default:
                return "";
        }
    }


}
