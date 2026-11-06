package io.github.wrqtec.content.safe.samples.basic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 最小样例：引入 content-safe-spring-boot-starter 即可获得可注入的 ContentSafeTemplate。
 *
 * @author L.David
 */
@SpringBootApplication
public class SampleBasicApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleBasicApplication.class, args);
    }
}
