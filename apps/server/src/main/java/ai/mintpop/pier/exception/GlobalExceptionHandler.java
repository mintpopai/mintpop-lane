package ai.mintpop.pier.exception;

import ai.mintpop.pier.enumeration.BizCodeEnum;
import ai.mintpop.pier.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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
     * `@Valid` 参数校验失败（请求体字段）。统一映射成 110001 参数非法，
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

    /**
     * 查询参数/路径变量类型不匹配，典型场景是枚举类型的 `@RequestParam` 传了非法取值
     * （如 `?role=XXX`）。与上面的 {@link MethodArgumentNotValidException} 分工：
     * 那个处理器管请求体里 `@Valid` 字段校验失败，这个处理器管框架在绑定单个请求参数
     * 时类型转换失败——两者都是「用户传参有问题」，同样归 110001，不能让后者漏网
     * 落到兜底的 Exception 分支变成 110002（内部错误语义）。
     * msg 里只带参数名与期望类型，不回显用户传入的原始值，避免把用户输入反射回响应体。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiResponse<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String expectedType = e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "预期类型";
        String detail = "参数 " + e.getName() + " 取值非法，期望类型：" + expectedType;
        log.warn("参数类型不匹配：{}", detail);
        return new ApiResponse<>(BizCodeEnum.PARAM_INVALID.getCode(), null, detail);
    }

    /**
     * 请求体反序列化失败：JSON 语法错、字段类型不匹配（如 {@code "port":"abc"}）、
     * body 里枚举取值非法等。与 {@link MethodArgumentTypeMismatchException} 是同一类
     * 「用户传参有问题」，只是一个管 query/path 参数、一个管 body，同样归 110001，
     * 不能让 body 这条漏网落到兜底的 Exception 分支变成 110002（内部错误语义），
     * 否则前端无法区分「你传错了」和「服务端炸了」。
     * <p>
     * msg 用固定文案，绝不回显 {@code e.getMessage()}：Jackson 的报错里会带上
     * 出错位置附近的原始输入片段，而请求体里恰恰可能装着用户提交的密码/凭据，
     * 原样回显等于把敏感字段吐回响应体。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<Void> handleBodyNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败", e);
        return new ApiResponse<>(BizCodeEnum.PARAM_INVALID.getCode(), null, "请求体格式非法");
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleUnexpected(Exception e) {
        log.error("未预期异常", e);
        return ApiResponse.fail(BizCodeEnum.INTERNAL_ERROR);
    }
}
