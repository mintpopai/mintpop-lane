package com.mintpop.server.response;

import com.mintpop.server.enumeration.BizCodeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一返回体。HTTP 状态码一律 200，业务成败只看 code。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApiResponse<T> {

    /** 业务状态码：0 成功，非 0 失败 */
    private Integer code;

    /** 业务数据，失败时为 null */
    private T data;

    /** 描述或错误信息，成功时为 null */
    private String msg;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, data, null);
    }

    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(0, null, null);
    }

    public static <T> ApiResponse<T> error(String msg) {
        return new ApiResponse<>(-1, null, msg);
    }

    public static <T> ApiResponse<T> fail(BizCodeEnum e) {
        return new ApiResponse<>(e.getCode(), null, e.getMessage());
    }

    public boolean isSuccess() {
        return code != null && code == 0;
    }
}
