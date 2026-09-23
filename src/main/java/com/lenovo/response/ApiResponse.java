package com.lenovo.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** 统一JSON响应：查询携带data，纯操作不输出data字段；不用于SSE事件。 */
@Data
public class ApiResponse<T> {
    private boolean success;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private T data;
    private String message;

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setData(data);
        response.setMessage("");
        return response;
    }

    public static ApiResponse<Void> successMessage(String message) {
        ApiResponse<Void> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setMessage(message);
        return response;
    }
}
