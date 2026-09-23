package com.lenovo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量发送结果对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchSendResult {
    private String sequenceNumber;
    private String uuid;
    private String lineManagerStatus;
    private String lineManagerMessage;
    private String bpoStatus;
    private String bpoMessage;
    private String overallStatus;
    private String errorMessage;

    public BatchSendResult(Object o, Object o1, String failed, String 未找到对应的记录) {
    }
}