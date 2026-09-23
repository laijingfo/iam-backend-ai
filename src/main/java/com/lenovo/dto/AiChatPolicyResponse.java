package com.lenovo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** AI聊天只读接口的明确响应契约。 */
@Data
public class AiChatPolicyResponse {
    /** 尚未确认法律提示；读取状态不会改变确认记录。 */
    @JsonProperty("isFrist")
    private boolean first;
    /** 距最近一次明确确认超过30天，需要再次提示。 */
    @JsonProperty("isOver30")
    private boolean over30;
}
