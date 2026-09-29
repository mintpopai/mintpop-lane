package ai.mintpop.lane.exception;

import ai.mintpop.lane.enumeration.BizCodeEnum;
import lombok.Getter;

/** 业务异常。抛出后由全局异常处理器收口成 ApiResponse。 */
@Getter
public class BizException extends RuntimeException {

    private final BizCodeEnum bizCode;

    public BizException(BizCodeEnum bizCode) {
        super(bizCode.getMessage());
        this.bizCode = bizCode;
    }

    /** 带明细的业务异常：文案 = 枚举文案 + "：" + 明细（如「需要 120 个主用名额，现有 90」） */
    public BizException(BizCodeEnum bizCode, String detail) {
        super(bizCode.getMessage() + "：" + detail);
        this.bizCode = bizCode;
    }
}
