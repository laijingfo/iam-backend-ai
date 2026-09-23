package com.lenovo.constant;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * UAR 告警类型。
 */
public enum UarAlertType {
    USER_IT_CODE_INVALID(10),
    LINE_MANAGER_IT_CODE_INVALID(11),
    BPO_IT_CODE_INVALID(12),
    LINE_MANAGER_CHANGED(21),
    MANUAL_BPO_INVALID(32);

    private static final List<UarAlertType> AUTOMATIC_DETECTION_ORDER =
            Collections.unmodifiableList(Arrays.asList(
                    USER_IT_CODE_INVALID,
                    LINE_MANAGER_IT_CODE_INVALID,
                    BPO_IT_CODE_INVALID,
                    LINE_MANAGER_CHANGED
            ));

    private final int code;

    UarAlertType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public String getCodeValue() {
        return String.valueOf(code);
    }

    /**
     * 生成查询参数使用的逗号分隔告警类型，例如 {@code 12,32}。
     */
    public static String codeValues(UarAlertType... alertTypes) {
        return Arrays.stream(alertTypes)
                .map(UarAlertType::getCodeValue)
                .collect(Collectors.joining(","));
    }

    /**
     * 自动检测的执行顺序。
     * 当前去重规则使该顺序具有业务含义，调整前需要同步确认告警优先级。
     */
    public static List<UarAlertType> automaticDetectionOrder() {
        return AUTOMATIC_DETECTION_ORDER;
    }
}
