package com.lenovo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lenovo.entity.*;
import com.lenovo.config.MailConfig;
import com.lenovo.mapper.FinalReviewExcelFileMapper;
import com.lenovo.mapper.UarMailSendLogMapper;
import com.lenovo.mapper.UarMailTemplateMapper;
import com.lenovo.notify.NotifyHelper;
import com.lenovo.security.utils.StringUtils;
import com.lenovo.service.SendEmailActionLogService;
import com.lenovo.service.UarMailService;
import com.lenovo.util.TemplateVariableFixer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UarMailServiceImpl implements UarMailService
{
    private final UarMailSendLogMapper uarMailSendLogMapper;
    private final UarMailTemplateMapper uarMailTemplateMapper;
    private final NotifyHelper notifyHelper;
    private final MailConfig mailConfig;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Random RANDOM = new Random();

    // 邮件统一外框：灰色背景、顶部内嵌 Banner 和正文区域。
    private static final String EMAIL_HTML_TEMPLATE = "<div class=\"WordSection1\">\n" +
            "      <table\n" +
            "        class=\"MsoNormalTable\"\n" +
            "        border=\"0\"\n" +
            "        cellspacing=\"0\"\n" +
            "        cellpadding=\"0\"\n" +
            "        width=\"100%\"\n" +
            "        style=\"\n" +
            "          width: 100%;\n" +
            "          mso-cellspacing: 0cm;\n" +
            "          background: #f2f2f2;\n" +
            "          mso-yfti-tbllook: 1184;\n" +
            "          mso-padding-alt: 0cm 0cm 0cm 0cm;\n" +
            "        \"\n" +
            "      >\n" +
            "        <tr>\n" +
            "          <td style=\"padding: 1cm 0cm 1cm 0cm\">\n" +
            "            <div align=\"center\">\n" +
            "              <table\n" +
            "                class=\"MsoNormalTable\"\n" +
            "                border=\"0\"\n" +
            "                cellspacing=\"0\"\n" +
            "                cellpadding=\"0\"\n" +
            "                width=\"695\"\n" +
            "                style=\"\n" +
            "                  width: 521.25pt;\n" +
            "                  background: white;\n" +
            "                  border: 1px solid #eee;\n" +
            "                  border-collapse: collapse;\n" +
            "                  mso-yfti-tbllook: 1184;\n" +
            "                  mso-padding-alt: 0cm 0cm 0cm 0cm;\n" +
            "                \"\n" +
            "              >\n" +
            "                <tr>\n" +
            "                  <td style=\"padding: 0\">\n" +
            "                    <img\n" +
            "                      src=\"cid:email-banner\"\n" +
            "                      alt=\"Banner\"\n" +
            "                      width=\"100%\"\n" +
            "                      style=\"display: block; border: 0\"\n" +
            "                    />\n" +
            "                  </td>\n" +
            "                </tr>\n" +
            "                <tr>\n" +
            "                  <td style=\"padding: 40px 50px 40px 50px\">{{content}}</td>\n" +
            "                </tr>\n" +
            "              </table>\n" +
            "            </div>\n" +
            "          </td>\n" +
            "        </tr>\n" +
            "      </table>\n" +
            "    </div>";

    /** 将业务正文放入统一邮件外框。 */
    private String wrapEmailContent(String content)
    {
        return EMAIL_HTML_TEMPLATE.replace("{{content}}", content);
    }

    /**
     * @Description 来自一个还未验证的方法 暂时注释发邮件实际动作
    **/
    @Override
    public void sendTemplatedEmail(Long templateId, Set<String> recipients, Set<String> ccs, Map<String, String> variables, String batchNo)
    {
        // 获取模板
        UarMailTemplate template = uarMailTemplateMapper.selectById(templateId);
        if (template == null)
        {
            throw new RuntimeException("Template not found with id: " + templateId);
        }

        try
        {
            // 替换变量
            String subject = TemplateVariableFixer.replaceTemplateVariables(template.getTheme(), variables);
            String content = TemplateVariableFixer.replaceTemplateVariables(template.getContent(), variables);

            // 套用统一邮件样式。
            String finalContent = wrapEmailContent(content);

            // 创建邮件对象
            NotifyHelper.Email email = new NotifyHelper.Email(
                    subject,
                    subject, // 使用subject作为title
                    finalContent,
                    recipients,
                    ccs
            );

            // 发送邮件 注释实际发邮件动作
            NotifyHelper.MailSendResult sendResult = null; //notifyHelper.sendMail(email);

            // 记录发送日志
            UarMailSendLog log = new UarMailSendLog();
            log.setTemplateId(templateId);
            log.setRecipientEmails(String.join(",", recipients));
            log.setCcEmails(sendResult.getEffectiveCcs().isEmpty()
                    ? null
                    : String.join(",", sendResult.getEffectiveCcs()));
            log.setSubject(subject);
            log.setContent(finalContent);
            log.setSenderAlias(mailConfig.getFromIdentity());
            log.setStatus("SUCCESS");
            if (!sendResult.getRemovedInvalidCcs().isEmpty()) {
                log.setErrorMessage("移除无效CC邮箱后发送成功: "
                        + String.join(",", sendResult.getRemovedInvalidCcs()));
            }
            log.setBatchNo(batchNo);
            uarMailSendLogMapper.insert(log);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            // 记录失败日志
            UarMailSendLog log = new UarMailSendLog();
            log.setTemplateId(templateId);
            log.setRecipientEmails(String.join(",", recipients));
            log.setCcEmails(ccs != null ? String.join(",", ccs) : null);
            log.setSubject(template.getSubject());
            log.setContent(template.getContent());
            log.setSenderAlias(mailConfig.getFromIdentity());
            log.setStatus("FAILED");
            log.setErrorMessage(getMeaningfulErrorMessage(e));
            log.setBatchNo(batchNo);
            uarMailSendLogMapper.insert(log);
            throw new RuntimeException("Failed to send email", e);
        }
    }


    /**
     * @Description TODO 只能用来给UAR管理——待发送页面，发送失败页面，UAR周期设置这三个地方发邮件统一入口，上面的发送方法应该可以弃用了
     * @author wangfenglong
     * @date 2026/1/16 18:09
    **/
    @Override
    public void sendTemplatedEmailOnlyForLmBpoAndUARSetting(UarMailTemplate template, Set<String> recipients, Set<String> ccs, Map<String, String> variables, String batchNo,String sendFlag)
    {
        String subject = "";
        String finalContent = "";
        try
        {
            // 替换变量
            subject = TemplateVariableFixer.replaceTemplateVariables(template.getTheme(), variables);
            String content = TemplateVariableFixer.replaceTemplateVariables(template.getContent(), variables);

            // 套用统一邮件样式。
            finalContent = wrapEmailContent(content);

            // 创建邮件对象
            NotifyHelper.Email email = new NotifyHelper.Email
            (
                subject,
                subject, // 使用subject作为title
                finalContent,
                recipients,
                ccs
            );

            // 发送邮件
            NotifyHelper.MailSendResult sendResult = notifyHelper.sendMail(email);

            // 记录发送日志
            UarMailSendLog log = new UarMailSendLog();
            log.setTemplateId(template.getId());
            log.setRecipientEmails(String.join(",", recipients));
            log.setCcEmails(sendResult.getEffectiveCcs().isEmpty()
                    ? null
                    : String.join(",", sendResult.getEffectiveCcs()));
            log.setSubject(subject);
            log.setContent(finalContent);
            log.setSenderAlias(mailConfig.getFromIdentity());
            log.setStatus("SUCCESS");
            if (!sendResult.getRemovedInvalidCcs().isEmpty()) {
                log.setErrorMessage("移除无效CC邮箱后发送成功: "
                        + String.join(",", sendResult.getRemovedInvalidCcs()));
            }
            log.setBatchNo(batchNo);
            log.setSendFlag(sendFlag);
            uarMailSendLogMapper.insert(log);
        }
        catch (Exception e)
        {
            // 记录失败日志
            //log.error("核心发送邮件：邮件发送失败: templateId={}, RecipientEmails={}, batchNo={}", template.getId(), recipients, batchNo,e);
            UarMailSendLog log = new UarMailSendLog();
            log.setTemplateId(template.getId());
            log.setRecipientEmails(String.join(",", recipients));
            log.setCcEmails(ccs != null ? String.join(",", ccs) : null);
            log.setSubject(subject);
            log.setContent(finalContent);
            log.setSenderAlias(mailConfig.getFromIdentity());
            log.setStatus("FAILED");
            log.setErrorMessage(getMeaningfulErrorMessage(e));
            log.setBatchNo(batchNo);
            log.setSendFlag(sendFlag);
            uarMailSendLogMapper.insert(log);
            throw new RuntimeException(e);
        }
    }

    private String getMeaningfulErrorMessage(Throwable throwable) {
        Throwable current = throwable;
        String message = null;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().trim().isEmpty()) {
                message = current.getMessage().trim();
            }
            current = current.getCause();
        }
        if (message == null) {
            return "邮件发送失败";
        }
        String runtimePrefix = "java.lang.RuntimeException: ";
        while (message.startsWith(runtimePrefix)) {
            message = message.substring(runtimePrefix.length()).trim();
        }
        return message;
    }

    public static String generateUarSequence()
    {
        String dateStr = LocalDate.now().format(DATE_FORMAT);
        int randomNum = RANDOM.nextInt(100_000_000); //8位随机数字（00000000 ~ 99999999）
        String randomSuffix = String.format("%08d", randomNum);
        return "DelegateBpo" + dateStr + randomSuffix;
    }

    /**
     * @Description TODO 只能用来给【最终审核结果是移除的用户】发送邮件，因为需要传送excel附件
     * @author wangfenglong
     * @date 2026/5/11 16:49
    **/
    @Override
    public void sendTemplatedEmailOnlyFinalRemoveUserByExcel(byte[] excelBytes, String userItCode, UarMailTemplate template, Set<String> recipients, Set<String> ccs, Map<String, String> variables, String batchNo,String sendFlag)
    {
        String subject = "";
        String finalContent = "";
        try
        {
            // 替换变量
            subject = TemplateVariableFixer.replaceTemplateVariables(template.getTheme(), variables);
            String content = TemplateVariableFixer.replaceTemplateVariables(template.getContent(), variables);

            // 套用统一邮件样式。
            finalContent = wrapEmailContent(content);



            if (excelBytes == null || excelBytes.length == 0)
            {
                log.error("邮件模板附件 {}.xlsx 不存在", userItCode);
                throw new RuntimeException("邮件模板附件 :" + userItCode + ".xlsx 不存在");
            }


            InputStream excelInputStream = new ByteArrayInputStream(excelBytes);

            //附件展示名称（收件人看到的文件名）
            String attachFileName = userItCode + " - Permission Removal List.xlsx";

            // 创建邮件对象
            NotifyHelper.MailAndAttachment emailForFinalRemoveUserByExcel = new NotifyHelper.MailAndAttachment(
                    subject,
                    subject, // 使用subject作为title
                    finalContent,
                    recipients,
                    ccs,
                    excelInputStream,
                    attachFileName
            );

            // 发送邮件
            NotifyHelper.MailSendResult sendResult = notifyHelper.sendMailAndAttachment(emailForFinalRemoveUserByExcel);

            // 记录发送日志
            UarMailSendLog log = new UarMailSendLog();
            log.setTemplateId(template.getId());
            //log.setRecipientEmails(String.join(",", recipients));
            log.setRecipientEmails(recipients == null || recipients.isEmpty() ? userItCode : String.join(",", recipients));
            log.setCcEmails(sendResult.getEffectiveCcs().isEmpty()
                    ? null
                    : String.join(",", sendResult.getEffectiveCcs()));
            log.setSubject(subject);
            log.setContent(finalContent);
            log.setSenderAlias(mailConfig.getFromIdentity());
            log.setStatus("SUCCESS");
            if (!sendResult.getRemovedInvalidCcs().isEmpty()) {
                log.setErrorMessage("移除无效CC邮箱后发送成功: "
                        + String.join(",", sendResult.getRemovedInvalidCcs()));
            }
            log.setBatchNo(batchNo);
            log.setSendFlag(sendFlag);
            uarMailSendLogMapper.insert(log);
        }
        catch (Exception e)
        {
            // 记录失败日志
            //log.error("核心发送邮件：邮件发送失败: templateId={}, RecipientEmails={}, batchNo={}", template.getId(), recipients, batchNo,e);
            UarMailSendLog log = new UarMailSendLog();
            log.setTemplateId(template.getId());
            log.setRecipientEmails(recipients == null || recipients.isEmpty() ? userItCode : String.join(",", recipients));
            log.setCcEmails(ccs != null ? String.join(",", ccs) : null);
            log.setSubject(subject);
            log.setContent(finalContent);
            log.setSenderAlias(mailConfig.getFromIdentity());
            log.setStatus("FAILED");
            log.setErrorMessage(getMeaningfulErrorMessage(e));
            log.setBatchNo(batchNo);
            log.setSendFlag(sendFlag);
            uarMailSendLogMapper.insert(log);
            throw new RuntimeException(e);
        }
    }


}
