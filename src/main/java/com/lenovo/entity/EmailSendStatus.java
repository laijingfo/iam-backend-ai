package com.lenovo.entity;

import lombok.Data;

import java.time.LocalDateTime;

// 单独的EmailSendStatus类
@Data
public class EmailSendStatus {
    private Integer round;
    private String status;
    private LocalDateTime sendTime;
    private String errorMessage;
}
