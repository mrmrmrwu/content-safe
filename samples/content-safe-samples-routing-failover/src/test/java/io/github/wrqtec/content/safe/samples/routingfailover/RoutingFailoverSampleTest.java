package io.github.wrqtec.content.safe.samples.routingfailover;

import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.ContentSafeTemplate;
import io.github.wrqtec.content.safe.api.constant.Scenes;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.samples.routingfailover.listener.AuditEventRecorder;
import io.github.wrqtec.content.safe.samples.routingfailover.provider.FakePrimaryProvider;
import io.github.wrqtec.content.safe.samples.routingfailover.provider.FakeSecondaryProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RoutingFailoverSampleTest {

    @Autowired
    private ContentSafeTemplate template;

    @Autowired
    private AuditEventRecorder recorder;

    @Autowired
    private FakePrimaryProvider primary;

    @Test
    void failoverToSecondaryWhenPrimaryFails() {
        AuditResult result = template.text(Scenes.COMMENT, "正常评论");

        assertThat(result.pass()).isTrue();
        assertThat(result.provider()).isEqualTo(FakeSecondaryProvider.PROVIDER);
        assertThat(primary.calls()).isGreaterThan(0);
        assertThat(recorder.degradedEvents())
                .anySatisfy(event -> {
                    assertThat(event.provider()).isEqualTo(FakePrimaryProvider.PROVIDER);
                    assertThat(event.reason()).contains("ProviderCallException");
                });
    }

    @Test
    void riskyContentRejectedBySecondary() {
        AuditResult result = template.text(Scenes.COMMENT, "这里有违禁内容");

        assertThat(result.pass()).isFalse();
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REJECT);
        assertThat(result.provider()).isEqualTo(FakeSecondaryProvider.PROVIDER);
    }
}
