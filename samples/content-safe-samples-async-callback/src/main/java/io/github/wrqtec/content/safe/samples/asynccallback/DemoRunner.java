package io.github.wrqtec.content.safe.samples.asynccallback;

import java.util.Map;

import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.CallbackRequest;
import io.github.wrqtec.content.safe.api.CallbackResult;
import io.github.wrqtec.content.safe.api.ContentRef;
import io.github.wrqtec.content.safe.api.ContentSafeTemplate;
import io.github.wrqtec.content.safe.api.constant.Scenes;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.samples.asynccallback.callback.FakeCallbackVerifier;
import io.github.wrqtec.content.safe.samples.asynccallback.listener.AuditEventRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 启动即演示异步图片审核全流程：
 * submitAsync 提交 → query 轮询（中间态）→ 伪造回调推送终态 → query 命中终态 → 重复回调幂等。
 *
 * @author L.David
 */
@Slf4j
@Component
public class DemoRunner implements CommandLineRunner {

    /** 演示用业务方数据 ID（providerTaskId = fake-task- + dataId） */
    private static final String DATA_ID = "avatar-001";

    private static final String CALLBACK_SECRET = "demo-callback-secret";

    private final ContentSafeTemplate template;

    private final AuditEventRecorder recorder;

    public DemoRunner(ContentSafeTemplate template, AuditEventRecorder recorder) {
        this.template = template;
        this.recorder = recorder;
    }

    @Override
    public void run(String... args) {
        AuditRequest request = AuditRequest.builder()
                .scene(Scenes.AVATAR)
                .type(ContentType.IMAGE)
                .content(ContentRef.url("https://example.com/avatar/1.jpg"))
                .dataId(DATA_ID)
                .build();

        String taskId = template.submitAsync(request);
        log.info("submitAsync -> taskId={}", taskId);

        AuditResult pending = template.query(taskId);
        log.info("query -> pending: finalResult={} (task stays PENDING until callback)", pending.finalResult());

        CallbackRequest callback = new CallbackRequest("fake-async",
                Map.of(FakeCallbackVerifier.TOKEN_HEADER, CALLBACK_SECRET),
                "taskId=fake-task-" + DATA_ID + "&suggestion=PASS");
        CallbackResult accepted = template.handleCallback(callback);
        log.info("callback -> accepted={}", accepted.accepted());

        AuditResult terminal = template.query(taskId);
        log.info("query -> terminal: suggestion={} source={}", terminal.suggestion(), terminal.source());

        CallbackResult duplicate = template.handleCallback(callback);
        log.info("duplicate callback -> accepted={} (idempotent, audit events={})",
                duplicate.accepted(), recorder.auditEvents().size());
    }
}
