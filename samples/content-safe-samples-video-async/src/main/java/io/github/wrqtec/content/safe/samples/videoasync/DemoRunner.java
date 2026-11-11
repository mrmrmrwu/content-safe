package io.github.wrqtec.content.safe.samples.videoasync;

import java.time.Duration;

import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.ContentRef;
import io.github.wrqtec.content.safe.api.ContentSafeTemplate;
import io.github.wrqtec.content.safe.api.constant.Scenes;
import io.github.wrqtec.content.safe.samples.videoasync.listener.AuditEventRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 启动即演示两种视频异步审核用法：
 * 1. video() 提交拿 taskId，自行轮询 query 直到 finalResult；
 * 2. submitAndWait 一步阻塞到终态（底层是同一条任务链路）。
 *
 * @author L.David
 */
@Slf4j
@Component
public class DemoRunner implements CommandLineRunner {

    private static final int MAX_POLLS = 5;
    private static final Duration POLL_INTERVAL = Duration.ofMillis(300);
    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(10);

    private final ContentSafeTemplate template;
    private final AuditEventRecorder recorder;

    public DemoRunner(ContentSafeTemplate template, AuditEventRecorder recorder) {
        this.template = template;
        this.recorder = recorder;
    }

    @Override
    public void run(String... args) throws Exception {
        pollManually();
        waitWithSubmitAndWait();
    }

    /** 姿势一：video() 提交 + 手动轮询（适合串行任务、需要分步展示进度的场景） */
    private void pollManually() throws InterruptedException {
        String taskId = template.video(Scenes.SHORT_VIDEO, "https://example.com/videos/demo.mp4");
        log.info("[poll] submitted, taskId={}", taskId);

        AuditResult current = template.query(taskId);
        for (int polls = 1; !current.finalResult() && polls < MAX_POLLS; polls++) {
            log.info("[poll] still running (polls={}), waiting {}ms", polls, POLL_INTERVAL.toMillis());
            Thread.sleep(POLL_INTERVAL.toMillis());
            current = template.query(taskId);
        }

        logResult("[poll]", current);
    }

    /** 姿势二：submitAndWait 阻塞到终态（适合同步风格调用方，内部复用同一任务） */
    private void waitWithSubmitAndWait() {
        AuditResult result = template.submitAndWait(
                AuditRequest.video(Scenes.SHORT_VIDEO,
                        ContentRef.url("https://example.com/videos/demo-2.mp4")),
                POLL_INTERVAL, WAIT_TIMEOUT);
        logResult("[wait]", result);

        log.info("events recorded: {} audit events", recorder.auditEvents().size());
    }

    private void logResult(String tag, AuditResult result) {
        log.info("{} final={} suggestion={} provider={} hits={}", tag,
                result.finalResult(), result.suggestion(), result.provider(), result.hits().size());
        result.hits().forEach(hit -> log.info("{} hit: {} raw={}", tag, hit.labelPath(), hit.raw()));
    }
}
