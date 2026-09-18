package ai.mintpop.lane.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查靶子：客户端的 mihomo fallback 组用它判断链路通不通。
 *
 * 刻意**不复用 /actuator/health**：那个会查数据库，数据库一抖它返回 DOWN，
 * 内核就会以为是代理坏了、把好节点切掉。这个端点必须只证明「HTTP 到得了」，
 * 不依赖任何下游组件，因此不注入任何 service，永远返回 204。
 */
@RestController
public class PingController {

    @GetMapping("/api/ping")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ping() {
    }
}
