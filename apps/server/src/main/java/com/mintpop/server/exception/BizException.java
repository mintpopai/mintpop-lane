package com.mintpop.server.exception;

import com.mintpop.server.enumeration.BizCodeEnum;
import lombok.Getter;

/** 业务异常。抛出后由全局异常处理器收口成 ApiResponse。 */
@Getter
public class BizException extends RuntimeException {

    private final BizCodeEnum bizCode;

    public BizException(BizCodeEnum bizCode) {
        super(bizCode.getMessage());
        this.bizCode = bizCode;
    }
}
