package com.mintpop.server.exception;

import com.mintpop.server.enumeration.BizCodeEnum;
import com.mintpop.server.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
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
     * 会优先于兜底的 Exception 处理器命中：Spring 按异常类型的继承距离择优
     * （ExceptionDepthComparator），与本类里各方法的声明顺序无关——
     * NoResourceFoundException 是比 Exception 更具体的类型，因此总是它胜出。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("路由不存在：{}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    /**
     * `@Valid` 参数校验失败。统一映射成 110001 参数非法，
     * 否则会落到兜底的 Exception 分支变成 110002（内部错误语义），
     * 前端无法区分「你参数填错了」和「服务端炸了」。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleInvalidParam(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .findFirst()
                .orElse(BizCodeEnum.PARAM_INVALID.getMessage());
        log.warn("参数校验失败：{}", detail);
        return new ApiResponse<>(BizCodeEnum.PARAM_INVALID.getCode(), null, detail);
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleUnexpected(Exception e) {
        log.error("未预期异常", e);
        return ApiResponse.fail(BizCodeEnum.INTERNAL_ERROR);
    }
}
