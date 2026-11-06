package io.github.wrqtec.content.safe.samples.basic;

import io.github.wrqtec.content.safe.api.AuditOptions;
import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.ContentRef;
import io.github.wrqtec.content.safe.api.ContentSafeTemplate;
import io.github.wrqtec.content.safe.api.ReviewResolution;
import io.github.wrqtec.content.safe.api.constant.Scenes;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.enums.ReviewDecision;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 启动即演示：内置场景 + scenes.json 自定义场景各审核一次；
 * AIGC 识别开关与人审闭环（REVIEW -> resolveReview 终态归一）
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

        // AIGC 识别开关：请求期开启，fake 供应商声明了 aigcDetect 能力位，路由放行并命中
        AuditResult aigc = template.audit(AuditRequest.builder()
                .scene(Scenes.COMMENT)
                .type(ContentType.TEXT)
                .content(ContentRef.text("这篇文章是AI生成的"))
                .options(AuditOptions.defaults().withAigcDetect(true))
                .build());
        log.info("comment aigc     -> {}", resultLine(aigc));

        // 人审闭环：机审 REVIEW -> resolveReview 落终态（sink 回写 + 终态归一）
        // 业务侧通常自带内容 ID，便于人审系统回查
        AuditResult suspect = template.audit(AuditRequest.builder()
                .scene(Scenes.COMMENT)
                .type(ContentType.TEXT)
                .content(ContentRef.text("疑似违规内容"))
                .dataId("comment-1001")
                .build());
        log.info("comment suspect  -> {}", resultLine(suspect));
        if (suspect.suggestion() == RiskSuggestion.REVIEW) {
            AuditResult manual = template.resolveReview(ReviewResolution.of(
                    Scenes.COMMENT, suspect.dataId(), null,
                    ReviewDecision.REJECT, "alice", "确认违规"));
            log.info("review resolved  -> source={}, finalResult={}, suggestion={}",
                    manual.source(), manual.finalResult(), manual.suggestion());
        }
    }

    private static String resultLine(AuditResult result) {
        return "pass=" + result.pass() + ", suggestion=" + result.suggestion()
                + ", provider=" + result.provider() + ", hits=" + result.hits().size();
    }
}
