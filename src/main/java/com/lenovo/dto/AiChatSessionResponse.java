package com.lenovo.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** AI聊天只读接口的明确响应契约。 */
@Data
public class AiChatSessionResponse {
    private String sessionId;
    private String title;
    /** 按数据库返回的本地时间展示，精确到分钟，不在Java中转换时区。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime createdAt;
    /** 按数据库返回的本地时间展示，精确到分钟，不在Java中转换时区。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime updatedAt;
}
