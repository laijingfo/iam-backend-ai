package com.lenovo.config;

import com.lenovo.bean.RequestLogMessage;
import com.lenovo.security.utils.SecurityUtils;
import org.apache.http.entity.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.WebUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.regex.Pattern;

@Component
public class RequestLoggerDispatcher extends OncePerRequestFilter {

    @Value("${ignore.log.address:/ping}")
    private String ignoreLogAddress;

    private static final Logger logger = LoggerFactory.getLogger(RequestLoggerDispatcher.class);
    private static final String NOTHING = "[nothing]";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // === 关键：SSE 请求跳过包装，直接放行 ===
        // ContentCachingResponseWrapper 会缓存响应体，对 SSE 异步流是致命的：
        // filterChain.doFilter 返回时 SSE 还没结束，finally 里 copyBodyToResponse
        // 会提前提交/关闭响应，导致 emitter.send 写不进去，触发 /error。
        if (isSseRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper requestWrapper = request instanceof ContentCachingRequestWrapper wrapper
                ? wrapper : new ContentCachingRequestWrapper(request, 8192);
        ContentCachingResponseWrapper responseWrapper = response instanceof ContentCachingResponseWrapper wrapper
                ? wrapper : new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            String[] selectPaths = ignoreLogAddress.split(",");
            boolean ignored = Arrays.stream(selectPaths)
                    .map(String::trim)
                    .anyMatch(selectPath -> Pattern.matches(selectPath, requestWrapper.getServletPath()));
            if (!ignored) {
                log(requestWrapper, responseWrapper);
            }
            responseWrapper.copyBodyToResponse();
        }
    }

    /**
     * 判断是否 SSE 请求。
     * 兼容：Accept 头、Content-Type 头、URI。
     */
    private boolean isSseRequest(HttpServletRequest request) {
        if (request == null) return false;

        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("text/event-stream")) {
            return true;
        }

        String contentType = request.getContentType();
        if (contentType != null && contentType.contains("text/event-stream")) {
            return true;
        }

        String uri = request.getRequestURI();
        if (uri != null && uri.contains("/ai/chat")) {
            return true;
        }

        return false;
    }

    private void log(HttpServletRequest request, HttpServletResponse response) {
        RequestLogMessage log = new RequestLogMessage();
        log.setMethod(request.getMethod());
        log.setUrl(request.getRequestURL().toString());

        Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        HandlerMethod handlerMethod = handler instanceof HandlerMethod method ? method : null;
        if (handlerMethod != null) {
            LogOperation logOperation = handlerMethod.getMethodAnnotation(LogOperation.class);
            if (logOperation == null) {
                logOperation = handlerMethod.getBeanType().getAnnotation(LogOperation.class);
            }
            if (logOperation != null) {
                log.setOperationName(logOperation.value());
                log.setOperationType(logOperation.type().name());
            }
        }

        try {
            String currentUsername = SecurityUtils.getCurrentUsername();
            log.addRequestHeader("ItCode", currentUsername);
        } catch (Exception e) {
            logger.info("user info invalid");
        }

        Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            log.addParam(name, request.getParameter(name));
        }

        log.setRequestContentType(request.getContentType());
        log.setRequestEncoding(request.getCharacterEncoding());
        log.setRequestBody(getRequestPayload(request));

        log.setResponseStatus(response.getStatus());
        response.getHeaderNames().forEach(s -> log.addResponseHeader(s, response.getHeader(s)));
        log.setResponseContentType(response.getContentType());
        log.setResponseEncoding(response.getCharacterEncoding());

        String contentType = response.getContentType();
        if (contentType != null && contentType.equals(ContentType.APPLICATION_OCTET_STREAM.getMimeType())) {
            log.setResponseBody("Byte stream, size: " + getResponsePayload(response).getBytes().length);
        } else {
            log.setResponseBody(getResponsePayload(response));
        }

        logger.info("*****RequestLoggerDispatcher*****:{}", log);
    }

    private String getResponsePayload(HttpServletResponse response) {
        ContentCachingResponseWrapper wrapper = WebUtils.getNativeResponse(response, ContentCachingResponseWrapper.class);
        if (wrapper != null) {
            return transformByteArrayToString(wrapper.getContentAsByteArray(), wrapper.getCharacterEncoding());
        }
        return NOTHING;
    }

    private String getRequestPayload(HttpServletRequest request) {
        ContentCachingRequestWrapper wrapper = WebUtils.getNativeRequest(request, ContentCachingRequestWrapper.class);
        if (wrapper != null) {
            return transformByteArrayToString(wrapper.getContentAsByteArray(), wrapper.getCharacterEncoding());
        }
        return NOTHING;
    }

    private String transformByteArrayToString(byte[] byteArrayContent, String encoding) {
        if (byteArrayContent.length > 0) {
            int length = Math.min(byteArrayContent.length, 8192);
            try {
                return new String(byteArrayContent, 0, length, encoding);
            } catch (UnsupportedEncodingException ex) {
                logger.warn("Unsupported encoding in request or response.");
            }
        }
        return NOTHING;
    }
}