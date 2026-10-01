package ai.mintpop.lane.support;

import ai.mintpop.lane.security.ClientVersionInterceptor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 让所有 MockMvc 请求默认扮演「最新版桌面端」：桌面端接口挂着版本闸门（见 ClientVersionConfig），
 * 不带 X-Client-Version 的请求一律回 CLIENT_VERSION_OUTDATED，既有用例与版本无关，不该逐个去加这个头。
 * <p>
 * 版本号取一个永远不会落后的值。要测闸门本身的用例在请求上显式 .header(...) 覆盖它即可——
 * MockMvc 合并默认请求时，请求自己带的同名头优先。
 */
@TestConfiguration(proxyBeanMethods = false)
public class CurrentDesktopClientHeader {

    public static final String VERSION = "999.0.0";

    @Bean
    MockMvcBuilderCustomizer currentDesktopClientVersion() {
        return builder -> builder.defaultRequest(get("/").header(ClientVersionInterceptor.HEADER, VERSION));
    }
}
