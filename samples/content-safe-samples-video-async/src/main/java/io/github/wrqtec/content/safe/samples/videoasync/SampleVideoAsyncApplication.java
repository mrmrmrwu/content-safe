package io.github.wrqtec.content.safe.samples.videoasync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 视频异步审核样例：video() 提交任务，轮询 query 直到终态（或 submitAndWait 一步到位）
 *
 * @author L.David
 */
@SpringBootApplication
public class SampleVideoAsyncApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleVideoAsyncApplication.class, args);
    }
}
