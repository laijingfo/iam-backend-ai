package com.lenovo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Configuration
public class LocaleConfig {

    @Bean
    public LocaleResolver localeResolver() {
        return new AcceptHeaderLocaleResolver() {
            @Override
            public Locale resolveLocale(HttpServletRequest request) {
                // 第一层：X-Language header
                String lang = request.getHeader("X-Language");
                if (StringUtils.hasText(lang)) {
                    if (isChinese(lang)) {
                        return Locale.CHINESE;
                    }
                    return Locale.ENGLISH;
                }

                // 第二层：Accept-Language header
                Locale browserLocale = request.getLocale();
                if (isChinese(browserLocale.getLanguage())) {
                    return Locale.CHINESE;
                }

                // 第三层：兜底英文
                return Locale.ENGLISH;
            }
        };
    }

    private boolean isChinese(String lang) {
        return lang != null && (lang.startsWith("zh") || lang.startsWith("cn"));
    }
}
