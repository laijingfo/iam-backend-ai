package com.lenovo.security.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * @date 2018-11-23
 * 统一异常处理: 默认返回code 200
 *
 * Token过期 401 未授权
 * 无权限访问	403	权限不足
 * 路由不存在	404	URL 错误
 * 服务器挂了	500	后端异常
 * 其余都是200
 */
@Getter
public class BadRequestException extends RuntimeException {

    private Integer status = HttpStatus.OK.value();

    /**
     * 默认错误信息
     * @param msg
     */
    public BadRequestException(String msg) {
        super(msg);
    }

    /**
     * 自定义状态码 和 错误信息
     * @param status
     * @param msg
     */
    public BadRequestException(HttpStatus status, String msg) {
        super(msg);
        this.status = status.value();
    }
}
