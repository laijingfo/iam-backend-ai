package com.lenovo.constant;

/**
 * @author : chenhao
 * @date : 2023/2/15
 * @description :
 */
public enum MediaType {
    TEXT("text/plain"),
    JSON("application/json"),
    JAVASCRIPT("application/javascript"),
    APPLICATION_XML("application/xml"),
    APPLICATION_X_FORM("application/x-www-form-urlencoded"),
    TEXT_XML("text/xml"),
    TEXT_HTML_UTF8("text/html; charset=utf-8"),
    HTML("text/html");

    private final String alias;

    MediaType(String alias) {
        this.alias = alias;
    }

    public String alias() {
        return alias;
    }
}
