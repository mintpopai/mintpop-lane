package ai.mintpop.lane.client;

/**
 * 订阅拉取口。定成接口是为了让 service 层不绑定具体 HTTP 实现，
 * 控制器集成测试用 @MockitoBean 替换后即可离线跑。
 */
public interface SubFetchClient {

    /** 拉取订阅链接，返回响应体文本；网络错误/非 2xx/空响应抛 BizException(SUB_FETCH_FAILED) */
    String fetch(String subUrl);
}
