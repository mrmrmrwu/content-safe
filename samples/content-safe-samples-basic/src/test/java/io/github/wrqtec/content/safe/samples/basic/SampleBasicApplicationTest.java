package io.github.wrqtec.content.safe.samples.basic;

import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.ContentSafeTemplate;
import io.github.wrqtec.content.safe.api.constant.Scenes;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SampleBasicApplicationTest {

    @Autowired
    private ContentSafeTemplate template;

    @Test
    void normalTextPasses() {
        AuditResult result = template.text(Scenes.COMMENT, "今天天气不错");
        assertThat(result.pass()).isTrue();
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.PASS);
        assertThat(result.provider()).isEqualTo("fake");
    }

    @Test
    void bannedTextRejected() {
        AuditResult result = template.text(Scenes.COMMENT, "这里有违禁内容");
        assertThat(result.pass()).isFalse();
        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.REJECT);
        assertThat(result.hits()).hasSize(1);
    }

    @Test
    void customSceneFromScenesJsonWorks() {
        AuditResult result = template.text("product_title", "全新正品手机");
        assertThat(result.pass()).isTrue();
    }
}
