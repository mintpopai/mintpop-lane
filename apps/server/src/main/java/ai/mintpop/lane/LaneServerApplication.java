package ai.mintpop.lane;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Mintpop 链路下发服务。
 * 只负责验身份、按人下发链路与凭据、心跳吊销，自身不承载任何代理流量。
 */
@SpringBootApplication
@MapperScan("ai.mintpop.lane.mapper")
public class LaneServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LaneServerApplication.class, args);
    }
}
