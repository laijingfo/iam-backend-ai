package com.lenovo.util;


import com.lenovo.response.BaseResponse;

public class BaseResponseUtil {

    private BaseResponseUtil() {
    }

    public static final String MSG = "Success";

    public static final int SUCCESS = 200;

    public static final int ERROR = 500;

    public static <T> BaseResponse success(T object) {
        BaseResponse<T> result = new BaseResponse<>();
        result.setStatus(SUCCESS);
        result.setMessage(MSG);
        result.setData(object);
        return result;
    }

    public static <T> BaseResponse<T> success(T object, String message) {
        BaseResponse<T> result = new BaseResponse<>();
        result.setStatus(200);
        result.setMessage(message);
        result.setData(object);
        return result;
    }


    public static <T> BaseResponse<T> error(String msg) {
        BaseResponse<T> result = new BaseResponse<>();
        result.setStatus(ERROR);
        result.setMessage(msg);
        return result;
    }


}
