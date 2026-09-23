package com.lenovo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


/**
 * @Description TODO 邮件发送异步线程池配置属性类
 * @ClassName AsyncEmailProperties
 * @Author wangfenglong
 * @Date 2025/12/15 14:31
 **/
@Data
@Component
@ConfigurationProperties(prefix = "async")
public class AsyncEmailProperties
{
    private EmailProperties email;
    private EmailProperties sendUser;

    @Data
    public static class EmailProperties
    {
        private int corePoolSize;
        private int maxPoolSize;
        private int queueCapacity;
        private String threadNamePrefix;
        private int keepAliveSeconds;
        private int awaitTerminationSeconds;
    }
}
