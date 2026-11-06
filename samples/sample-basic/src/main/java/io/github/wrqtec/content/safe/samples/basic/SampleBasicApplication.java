package io.github.wrqtec.content.safe.samples.basic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 最小样例：引入 content-safe-spring-boot-starter 后自动装配 ContentSafeTemplate，可直接注入使用
 *
 * @author L.David
 */
@SpringBootApplication
public class SampleBasicApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleBasicApplication.class, args);
    }
}
