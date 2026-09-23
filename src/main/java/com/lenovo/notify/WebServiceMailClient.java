package com.lenovo.notify;

import com.lenovo.config.MailConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.core.io.ClassPathResource;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.annotation.Resource;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import jakarta.mail.util.ByteArrayDataSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.util.*;

import static com.lenovo.constant.MediaType.TEXT_HTML_UTF8;


/**
 * @author : chenhao
 * @date : 2023/3/21
 * @description : 发送邮件组件
 */
@Slf4j
@Component
public class WebServiceMailClient
{
    //固定图片CID（和HTML中对应）
    private static final String BANNER_CID = "email-banner";
    //图片在项目中的路径
    private static final String BANNER_PATH = "static/email-banner.jpg";
    //邮件服务器session
    private final Session session;
    //发信和日志共用的发件人配置
    private final MailConfig mailConfig;
    //发送邮件 工具
    private static Transport transport;

    //邮件附件(已经改成支持附件上传的功能）
    private static Multipart multipart;

    static
    {
        // 初始化props
    }

    public WebServiceMailClient(
            @Value("${mail.smtp.host}") String host,
            @Value("${mail.smtp.port}") String port,
            @Value("${mail.smtp.starttls.enable}") String starttlsEnable,
            @Value("${mail.smtp.auth}") String auth,
            @Value("${mail.socketFactory.port}") String socketFactoryPort,
            @Value("${mail.socketFactory.fallback}") String socketFactoryFallback,
            @Value("${mail.transport.protocol}") String transportProtocol,
            @Value("${mail.smtp.ssl.trust}") String sslTrust,
            @Value("${mail.username}") String username,
            @Value("${mail.password}") String password,
            MailConfig mailConfig
    )
    {
        this.mailConfig = mailConfig;
        Properties props = new Properties();
        props.put(MailConstant.MAIL_SMTP_HOST, host);
        props.put(MailConstant.MAIL_SMTP_PORT, port);
        props.put(MailConstant.MAIL_SMTP_STARTTLS_ENABLE, starttlsEnable);
        props.put(MailConstant.MAIL_SMTP_AUTH, auth);
        props.put(MailConstant.MAIL_SMTP_SOCKETFACTORY_PORT, socketFactoryPort);
        props.put(MailConstant.MAIL_SMTP_SOCKETFACTORY_FALLBACK, socketFactoryFallback);
        props.put(MailConstant.MAIL_TRANSPORT_PROTOCOL, transportProtocol);
        props.put(MailConstant.MAIL_SMTP_SSL_TRUST, sslTrust);
        // 仅在 SMTP 明确要求认证时提供账号密码；内部 Relay 无需创建认证器。
        if (Boolean.parseBoolean(auth))
        {
            Assert.hasText(username, "mail.username must not be empty when mail.smtp.auth=true");
            Assert.hasText(password, "mail.password must not be empty when mail.smtp.auth=true");
            session = Session.getInstance(props, new MailAuthenticator(username, password));
        }
        else
        {
            session = Session.getInstance(props);
        }

    }

    // ==================== 【新方法 · 带Excel附件】 ====================
    public void sendMail(Set<String> toRecipients, Set<String> toCcs, String subject, Object content, InputStream fileStream, String fileName) throws MessagingException
    {
        if(isEmpty(toRecipients))
        {
            return;
        }
        Assert.notNull(subject, "subject");
        Assert.notNull(content, "content");

        var message = new MimeMessage(session);
        // From 只在最终发送层设置，业务调用方不能覆盖。
        try
        {
            message.setFrom(new InternetAddress(mailConfig.getFromAddress(), mailConfig.getFromAlias(), "UTF-8"));
        }
        catch (UnsupportedEncodingException e)
        {
            throw new MessagingException("发件人名称编码失败", e);
        }
        message.setSubject(subject, "utf-8");

        // 最外层：混合模式 = 正文 + 附件
        MimeMultipart totalMultipart = new MimeMultipart("mixed");

        // 1. 正文部分（HTML + 内嵌图片）
        MimeMultipart contentMultipart = new MimeMultipart("related");
        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(content, TEXT_HTML_UTF8.alias());
        contentMultipart.addBodyPart(htmlPart);

        // 2. 内嵌Banner图片
        try (InputStream imageStream = new ClassPathResource(BANNER_PATH).getInputStream()) {
            MimeBodyPart imagePart = new MimeBodyPart();
            DataSource dataSource = new ByteArrayDataSource(imageStream, "image/jpeg");
            imagePart.setDataHandler(new DataHandler(dataSource));
            imagePart.setContentID("<" + BANNER_CID + ">");
            imagePart.setDisposition(MimeBodyPart.INLINE);
            contentMultipart.addBodyPart(imagePart);
        } catch (IOException e) {
            throw new MessagingException("内嵌邮件Banner图片失败", e);
        }

        // 把正文放入总容器
        MimeBodyPart contentBody = new MimeBodyPart();
        contentBody.setContent(contentMultipart);
        totalMultipart.addBodyPart(contentBody);

        // 3. 添加Excel附件（如果有）
        if (fileStream != null && !StringUtils.isEmpty(fileName))
        {
            try {
                MimeBodyPart attachmentPart = new MimeBodyPart();
                DataSource ds = new ByteArrayDataSource(fileStream, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                attachmentPart.setDataHandler(new DataHandler(ds));
                attachmentPart.setFileName(MimeUtility.encodeText(fileName));
                totalMultipart.addBodyPart(attachmentPart);
            } catch (Exception e) {
                throw new MessagingException("附件添加失败", e);
            }
        }

        message.setContent(totalMultipart);

        // 收件人
        if (!CollectionUtils.isEmpty(toRecipients))
        {
            Address[] recipis = toRecipients.stream()
            .filter(Objects::nonNull)
            .map(this::toInternetAddress)
            .filter(Objects::nonNull)
            .toArray(Address[]::new);
            message.setRecipients(Message.RecipientType.TO, recipis);
        }

        // 抄送人
        if (!CollectionUtils.isEmpty(toCcs))
        {
            Address[] ccs = toCcs.stream()
            .filter(Objects::nonNull)
            .map(this::toInternetAddress)
            .filter(Objects::nonNull)
            .toArray(Address[]::new);
            message.setRecipients(Message.RecipientType.CC, ccs);
        }

        Transport.send(message);
    }

    // 工具方法：转换邮箱
    private InternetAddress toInternetAddress(String mail)
    {
        try
        {
            return new InternetAddress(mail);
        }
        catch (AddressException e)
        {
            log.error("邮箱格式非法：{}", mail);
            return null;
        }
    }

    private boolean isEmpty(Collection<?> collection)
    {
        return collection == null || collection.isEmpty();
    }

}
