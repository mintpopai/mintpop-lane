package com.mintpop.server.exception;

import com.mintpop.server.enumeration.BizCodeEnum;
import com.mintpop.server.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    /**
     * 路由不存在（框架未找到匹配的 handler）。
     * 按 api-response.md 的分工，这属于传输层的「真实异常」而非业务失败，
     * 必须保留原生 404 状态码、绕开 ApiResponse 的 200 通道——否则客户端
     * 无法区分「路径打错了」与「服务端炸了但业务码兜住了」。
     * 必须排在兜底的 Exception 处理器之前，否则会被后者吞掉。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("路由不存在：{}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleUnexpected(Exception e) {
        log.error("未预期异常", e);
        return ApiResponse.fail(BizCodeEnum.INTERNAL_ERROR);
    }
}
