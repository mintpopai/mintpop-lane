package ai.mintpop.lane.client;

import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.exception.BizException;

import java.time.Duration;

/** 睡眠的注入点：生产用 Thread.sleep，测试记录时长而不真等 */
@FunctionalInterface
public interface Sleeper {

    Sleeper SYSTEM = duration -> {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // 被打断视为这次拉取失败，交给上层按失败处理，不吞掉中断
            throw new BizException(BizCodeEnum.SUB_FETCH_FAILED);
        }
    };

    void sleep(Duration duration);
}
