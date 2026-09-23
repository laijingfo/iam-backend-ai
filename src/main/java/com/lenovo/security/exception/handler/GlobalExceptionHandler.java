package com.lenovo.security.exception.handler;

import com.lenovo.security.exception.BadRequestException;
import com.lenovo.security.exception.EntityExistException;
import com.lenovo.security.exception.EntityNotFoundException;
import com.lenovo.security.utils.ThrowableUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.Objects;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * @date 2018-11-23
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理所有不可知的异常
     */
    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Map> handleException(Throwable e) {
        // 打印堆栈信息
        log.error(ThrowableUtil.getStackTrace(e));
        return buildResponseEntity(e.getMessage());
    }

    /**
     * BadCredentialsException
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map> badCredentialsException(BadCredentialsException e) {
        // 打印堆栈信息
        String message = "坏的凭证".equals(e.getMessage()) ? "userName or password incorrect！" : "userName or password error!";
        log.error(message);
        return buildResponseEntity(message);
    }

    /**
     * 处理自定义异常
     */
    @ExceptionHandler(value = BadRequestException.class)
    public ResponseEntity<Map> badRequestException(BadRequestException e) {
        // 打印堆栈信息
        log.error(ThrowableUtil.getStackTrace(e));
        return buildResponseEntity(e.getMessage());
    }

    /**
     * 处理 EntityExist
     */
    @ExceptionHandler(value = EntityExistException.class)
    public ResponseEntity<Map> entityExistException(EntityExistException e) {
        // 打印堆栈信息
        log.error(ThrowableUtil.getStackTrace(e));
        return buildResponseEntity(e.getMessage());
    }

    /**
     * 处理 EntityNotFound
     */
    @ExceptionHandler(value = EntityNotFoundException.class)
    public ResponseEntity<Map> entityNotFoundException(EntityNotFoundException e) {
        // 打印堆栈信息
        log.error(ThrowableUtil.getStackTrace(e));
        return buildResponseEntity(e.getMessage());
    }

    /**
     * 处理所有接口数据验证异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        // 打印堆栈信息
        log.error(ThrowableUtil.getStackTrace(e));
        String[] str = Objects.requireNonNull(e.getBindingResult().getAllErrors().get(0).getCodes())[1].split("\\.");
        String message = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        String msg = "不能为空";
        if (msg.equals(message)) {
            message = str[1] + ":" + message;
        }
        return buildResponseEntity(message);
    }

    /**
     * 统一返回
     */
    private ResponseEntity<Map> buildResponseEntity(String message) {
        String errorMessage = message == null || message.isBlank()
                ? "系统异常，请联系管理员"
                : message;

        return ResponseEntity.ok().body(
                Map.of("message", errorMessage, "success", false)
        );
    }
}
