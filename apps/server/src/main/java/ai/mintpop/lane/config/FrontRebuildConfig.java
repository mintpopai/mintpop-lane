package ai.mintpop.lane.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** 全体重算专用的单线程执行器：任务本身互斥，不与 @Async 通知共用线程池，避免被通知拖慢或反过来 */
@Configuration
public class FrontRebuildConfig {

    @Bean(name = "frontRebuildExecutor")
    public Executor frontRebuildExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "front-rebuild");
            thread.setDaemon(true);
            return thread;
        });
    }
}
