package com.lenovo.constant;

/**
 * UAR 权限审核范围。
 */
public enum AccessReviewScope {

    /** 审核全部权限。 */
    ALL_ACCESS,

    /** 仅审核敏感权限。 */
    SENSITIVE_ACCESS;

    public boolean isSensitiveOnly() {
        return this == SENSITIVE_ACCESS;
    }
}
