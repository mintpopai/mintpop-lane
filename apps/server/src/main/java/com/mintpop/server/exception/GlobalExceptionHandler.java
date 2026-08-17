package com.mintpop.server.exception;

import com.mintpop.server.enumeration.BizCodeEnum;
import com.mintpop.server.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常收口。业务异常与未预期异常都转成 ApiResponse，
 * Controller 里因此不需要手写 try-catch 拼返回体。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBiz(BizException e) {
        log.warn("业务异常：{}", e.getMessage());
        return ApiResponse.fail(e.getBizCode());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleUnexpected(Exception e) {
        log.error("未预期异常", e);
        return ApiResponse.fail(BizCodeEnum.INTERNAL_ERROR);
    }
}
