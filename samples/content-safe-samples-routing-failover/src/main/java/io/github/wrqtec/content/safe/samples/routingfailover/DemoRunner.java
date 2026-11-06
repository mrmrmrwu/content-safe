package io.github.wrqtec.content.safe.samples.routingfailover;

import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.ContentSafeTemplate;
import io.github.wrqtec.content.safe.api.constant.Scenes;
import io.github.wrqtec.content.safe.samples.routingfailover.listener.AuditEventRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 启动即演示：主供应商（fake-primary，order=0）恒故障，
 * 每次调用都自动 failover 到备用供应商（fake-secondary，order=10），
 * 并通过 {@link AuditEventRecorder} 观测 failover 事件。
 *
 * @author L.David
 */
@Slf4j
@Component
public class DemoRunner implements CommandLineRunner {

    private final ContentSafeTemplate template;

    private final AuditEventRecorder recorder;

    public DemoRunner(ContentSafeTemplate template, AuditEventRecorder recorder) {
        this.template = template;
        this.recorder = recorder;
    }

    @Override
    public void run(String... args) {
        AuditResult normal = template.text(Scenes.COMMENT, "今天天气不错");
        log.info("comment normal -> {}", resultLine(normal));

        AuditResult risky = template.text(Scenes.COMMENT, "这里有违禁内容");
        log.info("comment risky  -> {}", resultLine(risky));

        log.info("failover events: {} (primary degraded, requests served by fake-secondary)",
                recorder.degradedEvents().size());
    }

    private static String resultLine(AuditResult result) {
        return "pass=" + result.pass() + ", suggestion=" + result.suggestion()
                + ", provider=" + result.provider() + ", hits=" + result.hits().size();
    }
}
