package com.example.aispringboot.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;


@RestControllerAdvice
public class GlobarExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobarExceptionHandler.class);

    //处理请求体参数校验异常
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> handleException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(","));
        return Result.error(ResultCode.PARAM_ERROR.getCode(), ResultCode.PARAM_ERROR.getMsg(), message);
    }

    //缺少必填的请求参数
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<String> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.error(ResultCode.PARAM_MISSING.getCode(), ResultCode.PARAM_MISSING.getMsg(),
                e.getParameterName());
    }

    //参数类型无法转换，例如把非数字字符串绑定到 Long 上
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<String> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return Result.error(ResultCode.PARAM_INVALID.getCode(), ResultCode.PARAM_INVALID.getMsg(),
                e.getName());
    }

    //请求体不是合法 JSON
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<String> handleNotReadable(HttpMessageNotReadableException e) {
        return Result.error(ResultCode.PARAM_INVALID.getCode(), ResultCode.PARAM_INVALID.getMsg(),
                "请求体格式错误");
    }

    //业务异常，直接透出既定的错误码与提示
    @ExceptionHandler(BusinessException.class)
    public Result<String> handleBusiness(BusinessException e) {
        return Result.error(e.getCode(), e.getMessage(), null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<String> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return Result.error(ResultCode.FILE_SIZE_EXCEEDED.getCode(),
                ResultCode.FILE_SIZE_EXCEEDED.getMsg(), null);
    }

    //兜底：任何未预期的异常都收敛为系统错误，避免把堆栈泄漏给前端
    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        log.error("未处理的系统异常", e);
        return Result.error(ResultCode.SYSTEM_ERROR.getCode(), ResultCode.SYSTEM_ERROR.getMsg(), null);
    }

}
