package io.github.wrqtec.content.safe.samples.asynccallback;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 异步回调样例：submitAsync 提交图片审核任务，经回调推送终态结果
 *
 * @author L.David
 */
@SpringBootApplication
public class SampleAsyncCallbackApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleAsyncCallbackApplication.class, args);
    }
}
