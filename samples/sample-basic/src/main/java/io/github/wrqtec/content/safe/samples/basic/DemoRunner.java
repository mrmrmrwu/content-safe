package io.github.wrqtec.content.safe.samples.basic;

import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.ContentSafeTemplate;
import io.github.wrqtec.content.safe.api.constant.Scenes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 启动即演示：内置场景 + scenes.json 自定义场景各审核一次。
 *
 * @author L.David
 */
@Slf4j
@Component
public class DemoRunner implements CommandLineRunner {

    /** scenes.json 中声明的自定义场景 code */
    private static final String CUSTOM_SCENE = "product_title";

    private final ContentSafeTemplate template;

    public DemoRunner(ContentSafeTemplate template) {
        this.template = template;
    }

    @Override
    public void run(String... args) {
        // 内置场景：正常文本
        AuditResult normal = template.text(Scenes.COMMENT, "今天天气不错");
        log.info("comment normal   -> {}", resultLine(normal));

        // 内置场景：命中"违禁"关键词，预期 REJECT
        AuditResult risky = template.text(Scenes.COMMENT, "这里有违禁内容");
        log.info("comment risky    -> {}", resultLine(risky));

        // scenes.json 中声明的自定义场景
        AuditResult custom = template.text(CUSTOM_SCENE, "全新正品手机");
        log.info("{}    -> {}", CUSTOM_SCENE, resultLine(custom));
    }

    private static String resultLine(AuditResult result) {
        return "pass=" + result.pass() + ", suggestion=" + result.suggestion()
                + ", provider=" + result.provider() + ", hits=" + result.hits().size();
    }
}
