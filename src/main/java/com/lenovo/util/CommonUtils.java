package com.lenovo.util;

import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

import java.util.concurrent.*;

/**
 * @author : chenhao
 * @date : 2023/2/15
 * @description :
 */
public class CommonUtils {

    private static class TimeoutFactory extends CustomizableThreadFactory {

        private static final long serialVersionUID = -2469586984939077580L;
        private Thread thread;

        @Override
        @SuppressWarnings("NullableProblems")
        public Thread newThread(Runnable runnable) {
            this.thread = this.createThread(runnable);
            return this.thread;
        }

    }

    public static <T> T doWithTimeout(Callable<T> callable, long timeoutLimit) {
        var factory = new TimeoutFactory();
        var executor = Executors.newSingleThreadExecutor(factory);
        Future<T> future = null;
        try {
            future = executor.submit(callable);
            return future.get(timeoutLimit, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new RuntimeException("邮件发送超时(20s)，SMTP无响应; Email sending timeout (20 seconds), SMTP unresponsive ", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            // 邮件发送失败 具体错误
            while (cause != null) {
                if (cause instanceof SMTPAddressFailedException) {
                    SMTPAddressFailedException smtp = (SMTPAddressFailedException) cause;
                    throw new RuntimeException(
                            "邮件发送失败: " + smtp.getMessage(),
                            smtp);
                }
                cause = cause.getCause();
            }
            throw new RuntimeException(
                    "邮件发送失败: " + e.getCause().getMessage(),
                    e.getCause()
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("发送被中断; Transmission was interrupted ", e);
        } catch (Exception e) {
            throw new RuntimeException("邮件发送失败; Email sending failed: " + e.getMessage(), e);
        } finally {
            executor.shutdownNow();
        }
    }
}
