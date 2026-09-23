package com.lenovo.notify;

import com.lenovo.config.MailConfig;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import jakarta.mail.MessagingException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.lenovo.util.CommonUtils.doWithTimeout;

/**
 * @author chenhao
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotifyHelper
{
    private final WebServiceMailClient client;
    private final MailConfig mailConfig;

    @Data
    @AllArgsConstructor
    public static class Email
    {
        private final String subject;
        private final String title;
        private final Object context;
        private final Set<String> recipients;
        private final Set<String> ccs;
    }

    @Data
    @AllArgsConstructor
    public static class MailAndAttachment {
        private final String subject;
        private final String title;
        private final Object context;
        private final Set<String> recipients;
        private final Set<String> ccs;
        private final InputStream fileStream;
        private final String fileName;
    }

    @Data
    @AllArgsConstructor
    public static class MailSendResult {
        private final Set<String> effectiveCcs;
        private final Set<String> removedInvalidCcs;
    }

    /**
     * 发送邮件
     * @param email
     */
    public MailSendResult sendMail(Email email)
    {
        Assert.notNull(email, "email");
        return sendWithRetry(
                email.getRecipients(),
                email.getCcs(),
                email.getSubject(),
                email.getContext(),
                null,
                null,
                "send email"
        );
    }


    /**
     * 发送邮件并添加附件
     * @param email
     */
    public MailSendResult sendMailAndAttachment(MailAndAttachment email)
    {
        Assert.notNull(email, "email");
        return sendWithRetry(
                email.getRecipients(),
                email.getCcs(),
                email.getSubject(),
                email.getContext(),
                email.getFileStream(),
                email.getFileName(),
                "send email and attachment"
        );
    }

    private MailSendResult sendWithRetry(
            Set<String> originalRecipients, Set<String> originalCcs,
            String subject, Object context,
            InputStream fileStream, String fileName,
            String operationName
    )
    {
        Set<String> recipients = copyAddresses(originalRecipients);
        Set<String> ccs = copyAddresses(originalCcs);
        Set<String> removedInvalidCcs = new LinkedHashSet<>();
        try
        {
            doWithTimeout(() ->
            {
                var retry = 3;
                for (; ; )
                {
                    try
                    {
                        if (!mailConfig.isSendEnabled())
                        {
                            log.warn("[{}] mail sending is disabled, skip sending to:{},cc:{}", operationName, recipients, ccs);
                            break;
                        }

                        long start = System.currentTimeMillis();
                        log.info("[{}] to:{},cc:{} start,Retry the remaining: {}", operationName, recipients, ccs, retry);

                        client.sendMail(recipients, ccs, subject, context, fileStream, fileName);

                        long cost = System.currentTimeMillis() - start;
                        log.info("[{}] to:{},cc:{} success,time: {}ms", operationName, recipients, ccs, cost);
                        break;
                    }
                    catch (Exception e)
                    {
                        if (handleUnknownAddress(e, recipients, ccs, removedInvalidCcs)) {
                            continue;
                        }
                        log.warn("[{}] to:{},cc:{} fail: {}, Retry the remaining: {}", operationName, recipients, ccs, e.getMessage(), retry);
                        if (retry-- <= 0)
                        {
                            throw e;
                        }
                        TimeUnit.SECONDS.sleep(2L);
                    }
                }
                return null;
            }, 20000L);
            return new MailSendResult(
                    Collections.unmodifiableSet(new LinkedHashSet<>(ccs)),
                    Collections.unmodifiableSet(new LinkedHashSet<>(removedInvalidCcs))
            );
        }
        catch (Exception e)
        {
            log.error("邮件发送主服务发送失败NotifyHelper.{} failed: {}", operationName, e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * SMTP 明确返回邮箱不存在时：收件人失败直接结束，抄送人失败则移除后重发。
     *
     * @return 是否已移除无效抄送人，可以立即重发
     */
    private boolean handleUnknownAddress(Exception exception, Set<String> recipients, Set<String> ccs,
                                         Set<String> removedInvalidCcs) throws Exception {
        String failedAddress = findUnknownAddress(exception);
        if (failedAddress == null) {
            return false;
        }

        if (containsIgnoreCase(recipients, failedAddress)) {
            log.warn("[send email] recipient does not exist, skip retry: {}", failedAddress);
            throw exception;
        }

        if (removeIgnoreCase(ccs, failedAddress)) {
            removedInvalidCcs.add(failedAddress);
            log.warn("[send email] cc does not exist, remove it and retry: {}", failedAddress);
            return true;
        }
        return false;
    }

    private String findUnknownAddress(Throwable throwable) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Throwable> pending = new ArrayDeque<>();
        pending.add(throwable);
        while (!pending.isEmpty()) {
            Throwable current = pending.removeFirst();
            if (current == null || !visited.add(current)) {
                continue;
            }
            if (current instanceof SMTPAddressFailedException) {
                SMTPAddressFailedException smtp = (SMTPAddressFailedException) current;
                String message = smtp.getMessage();
                boolean unknownAddress = smtp.getReturnCode() == 550
                        && message != null
                        && (message.contains("5.1.1") || message.toLowerCase(Locale.ROOT).contains("user unknown"));
                if (unknownAddress && smtp.getAddress() != null) {
                    return smtp.getAddress().toString();
                }
            }
            if (current.getCause() != null) {
                pending.addLast(current.getCause());
            }
            if (current instanceof MessagingException) {
                Exception next = ((MessagingException) current).getNextException();
                if (next != null) {
                    pending.addLast(next);
                }
            }
        }
        return null;
    }

    private Set<String> copyAddresses(Set<String> addresses) {
        if (addresses == null) {
            return new LinkedHashSet<>();
        }
        return addresses.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(address -> !address.isEmpty())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean containsIgnoreCase(Set<String> addresses, String target) {
        return addresses.stream().anyMatch(address -> address != null && address.equalsIgnoreCase(target));
    }

    private boolean removeIgnoreCase(Set<String> addresses, String target) {
        return addresses.removeIf(address -> address != null && address.equalsIgnoreCase(target));
    }

}
