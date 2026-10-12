package com.example.aispringboot.common;

import lombok.Getter;

/**
 * 业务异常。携带 ResultCode，由全局异常处理器转成对应的响应信封。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final String code;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMsg());
        this.code = resultCode.getCode();
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

}
