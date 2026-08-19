package ai.mintpop.pier;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Mintpop 链路下发服务。
 * 只负责验身份、按人下发链路与凭据、心跳吊销，自身不承载任何代理流量。
 */
@SpringBootApplication
@MapperScan("ai.mintpop.pier.mapper")
public class PierServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PierServerApplication.class, args);
    }
}
