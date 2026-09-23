package com.lenovo.config;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 覆盖 Spring Boot 默认的 BasicErrorController。
 * 对 SSE 请求的 error，直接返回空，避免 Map 和 text/event-stream converter 冲突。
 */
@Slf4j
@RestController
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<?> handleError(HttpServletRequest request) {
        // 原始请求 URI（error dispatch 时原 URI 会放在这个 attribute 里）
        Object origUri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        String uri = origUri == null ? "" : origUri.toString();
        log.warn(">>> CustomErrorController, original uri: {}", uri);

        // SSE 请求：返回空响应，不返回 Map
        if (uri.contains("/ai/chat")) {
            return ResponseEntity.status(500)
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .build();
        }

        // 其他请求：保持原来的 JSON 错误格式
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int statusCode = 500;
        if (status != null) {
            try {
                statusCode = Integer.parseInt(status.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return ResponseEntity.status(statusCode)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("success", false, "message", "Internal Server Error"));
    }
}