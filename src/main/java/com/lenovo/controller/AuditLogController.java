package com.lenovo.controller;

import com.lenovo.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @description: 审计日志控制类
 */

@RestController
@RequestMapping("/auditLog")
@RequiredArgsConstructor
@Slf4j
public class AuditLogController {
    private final AuditLogService auditLogService;

}
