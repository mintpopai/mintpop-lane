package ai.mintpop.lane.client;

import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.exception.BizException;
import lombok.Getter;

import java.time.Duration;

/** 机场返回 429：仍算 SUB_FETCH_FAILED，只是多带一个 Retry-After 供重试层决定等多久（可为 null） */
@Getter
public class SubFetchRateLimitedException extends BizException {

    private final Duration retryAfter;

    public SubFetchRateLimitedException(Duration retryAfter) {
        super(BizCodeEnum.SUB_FETCH_FAILED);
        this.retryAfter = retryAfter;
    }
}
