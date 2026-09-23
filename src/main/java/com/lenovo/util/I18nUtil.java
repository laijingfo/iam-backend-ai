package com.lenovo.util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

@Component
public class I18nUtil {


    private static MessageSource staticMessageSource;

    @Autowired
    public void setMessageSource(MessageSource messageSource) {
        staticMessageSource = messageSource;
    }
    /**
     * 获取当前语言环境下的消息
     */
    public static String get(String code) {
        return staticMessageSource.getMessage(code, null, LocaleContextHolder.getLocale());
    }

    /**
     * 带参数的消息
     * args 按顺序填充 {0}, {1}, {2}...
     */
    public static String get(String code, Object... args) {
        return staticMessageSource.getMessage(code, args, LocaleContextHolder.getLocale());
    }
}