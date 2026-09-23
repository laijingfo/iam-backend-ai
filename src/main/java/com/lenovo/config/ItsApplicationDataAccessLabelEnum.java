package com.lenovo.config;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-08-31 09:28
 * @project iam-backend
 * @description
 */
public enum ItsApplicationDataAccessLabelEnum {
    NA_SENSITIVE("NA-Sensitive", "sensitive"),
    NON_NA_SENSITIVE("Non-NA-Sensitive", "standard");

    private final String original;
    private final String mapped;

    ItsApplicationDataAccessLabelEnum(String original, String mapped) {
        this.original = original;
        this.mapped = mapped;
    }

    public static String map(String original) {
        for (ItsApplicationDataAccessLabelEnum mapping : values()) {
            if (mapping.original.equals(original)) {
                return mapping.mapped;
            }
        }
        return original; // 未匹配则原样返回
    }
}
