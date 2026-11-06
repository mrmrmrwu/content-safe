package io.github.wrqtec.content.safe.samples.routingfailover;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 路由与故障转移样例：主供应商故障时自动切换到备用供应商
 *
 * @author L.David
 */
@SpringBootApplication
public class SampleRoutingFailoverApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleRoutingFailoverApplication.class, args);
    }
}
