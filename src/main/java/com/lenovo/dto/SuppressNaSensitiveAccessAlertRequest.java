package com.lenovo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SuppressNaSensitiveAccessAlertRequest {
    @NotBlank(message = "Suppressed Reason不能为空")
    @Size(max = 2000, message = "Suppressed Reason长度不能超过2000个字符")
    private String suppressedReason;

    /** 告警抑制申请人，未传时默认取当前操作人 */
    @Size(max = 255, message = "Suppressed Requested By长度不能超过255个字符")
    private String suppressedRequestedBy;
}
