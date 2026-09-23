package com.lenovo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MailConfig {

    @Value("${mail.from-address}")
    private String fromAddress;

    @Value("${mail.from-alias}")
    private String fromAlias;

    @Value("${mail.send-enabled:false}")
    private boolean sendEnabled;

    public String getFromAddress() {
        return fromAddress;
    }

    public String getFromAlias() {
        return fromAlias;
    }

    public boolean isSendEnabled() {
        return sendEnabled;
    }

    /** 返回最终发件人，供实际发送和发送日志统一使用。 */
    public String getFromIdentity() {
        return fromAlias + " <" + fromAddress + ">";
    }
}
