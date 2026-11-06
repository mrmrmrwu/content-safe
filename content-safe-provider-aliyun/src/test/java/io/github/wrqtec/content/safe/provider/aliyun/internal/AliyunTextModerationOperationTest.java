package io.github.wrqtec.content.safe.provider.aliyun.internal;

import java.util.Map;

import com.aliyun.green20220302.models.TextModerationResponseBody;
import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedContentException;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.model.enums.AuditMode;
import io.github.wrqtec.content.safe.provider.aliyun.AliyunProviderConstants;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AliyunTextModerationOperation} 单元测试：能力门控、限制校验、
 * 调用装配与异常映射（Mock invoker，不触网）
 *
 * @author L.David
 */
class AliyunTextModerationOperationTest {

    private static final ProviderPlan PLAN = new ProviderPlan(
            "aliyun", "green-cip", "2022-03-02", "TextModeration", "comment_detection",
            AuditMode.SYNC, AliyunLimits.textSync(), null);

    private final GreenClientInvoker invoker = mock(GreenClientInvoker.class);
    private final AliyunTextModerationOperation operation =
            new AliyunTextModerationOperation(PLAN, invoker, new AliyunAuditResultNormalizer());

    @Test
    void capabilitiesDeclareSyncTextOnly() {
        assertThat(operation.plan()).isEqualTo(PLAN);
        assertThat(operation.capabilities().contentTypes()).containsExactly(ContentType.TEXT);
        assertThat(operation.capabilities().sync()).isTrue();
        assertThat(operation.capabilities().url()).isFalse();
        assertThat(operation.capabilities().aigcDetect()).isFalse();
    }

    @Test
    void aigcServiceDeclaresCapabilityAndSwitchGatesRouting() {
        ProviderPlan aigcPlan = new ProviderPlan("aliyun", "green-cip", "2022-03-02",
                "TextModeration", AliyunProviderConstants.SERVICE_AIGC_DETECTOR,
                AuditMode.SYNC, AliyunLimits.textSync(), null);
        AliyunTextModerationOperation aigcOperation =
                new AliyunTextModerationOperation(aigcPlan, invoker, new AliyunAuditResultNormalizer());
        assertThat(aigcOperation.capabilities().aigcDetect()).isTrue();
        assertThat(AliyunProviderConstants.isAigcService(AliyunProviderConstants.SERVICE_AIGC_DETECTOR_FULL)).isTrue();
        assertThat(AliyunProviderConstants.isAigcService("comment_detection")).isFalse();

        AuditRequest aigcOn = AuditRequest.builder()
                .scene("aigc-text").type(ContentType.TEXT)
                .content(io.github.wrqtec.content.safe.api.ContentRef.text("疑似AI生成文本"))
                .options(io.github.wrqtec.content.safe.api.AuditOptions.defaults().withAigcDetect(true))
                .build();
        assertThat(aigcOperation.capabilities().supports(aigcOn)).isTrue();
        // 普通 service 操作被 AIGC 开关门控排除
        assertThat(operation.capabilities().supports(aigcOn)).isFalse();
    }

    @Test
    void happyPathAssemblesParamsAndNormalizes() throws Exception {
        TextModerationResponseBody body = new TextModerationResponseBody()
                .setCode(200).setRequestId("r-text-1")
                .setData(new TextModerationResponseBody.TextModerationResponseBodyData()
                        .setLabels("").setReason("{\"riskLevel\":\"none\"}"));
        when(invoker.textModeration(eq("comment_detection"), anyMap())).thenReturn(body);

        AuditRequest request = AuditRequest.builder()
                .scene("comment").type(ContentType.TEXT)
                .content(io.github.wrqtec.content.safe.api.ContentRef.text("你好"))
                .dataId("d-1").build();
        AuditResult result = operation.audit(request);

        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.PASS);
        assertThat(result.requestId()).isEqualTo("r-text-1");
        assertThat(result.dataId()).isEqualTo("d-1");
        assertThat(result.cost()).isNotNull();
        verify(invoker).textModeration("comment_detection",
                Map.of("content", "你好", "dataId", "d-1"));
    }

    @Test
    void rejectsNonTextContent() {
        AuditRequest request = AuditRequest.builder()
                .scene("comment").type(ContentType.TEXT)
                .content(io.github.wrqtec.content.safe.api.ContentRef.url("https://example.com/a.txt"))
                .build();
        assertThatThrownBy(() -> operation.audit(request))
                .isInstanceOf(UnsupportedContentException.class);
    }

    @Test
    void rejectsOversizeContent() {
        // 801 个中文字符 = 2403 字节（UTF-8），超过 2400 字节上限
        String oversized = "字".repeat(801);
        AuditRequest request = AuditRequest.builder()
                .scene("comment").type(ContentType.TEXT)
                .content(io.github.wrqtec.content.safe.api.ContentRef.text(oversized))
                .build();
        assertThatThrownBy(() -> operation.audit(request))
                .isInstanceOf(UnsupportedContentException.class)
                .hasMessageContaining("exceeds");
    }

    @Test
    void errorCodeMapsToProviderCallException() throws Exception {
        when(invoker.textModeration(eq("comment_detection"), anyMap()))
                .thenReturn(new TextModerationResponseBody().setCode(400).setMessage("service is invalid"));
        AuditRequest request = AuditRequest.text("comment", "你好");
        assertThatThrownBy(() -> operation.audit(request))
                .isInstanceOf(ProviderCallException.class)
                .hasMessageContaining("400");
    }

    @Test
    void sdkExceptionMapsToProviderCallException() throws Exception {
        when(invoker.textModeration(eq("comment_detection"), anyMap()))
                .thenThrow(new RuntimeException("connect reset"));
        AuditRequest request = AuditRequest.text("comment", "你好");
        assertThatThrownBy(() -> operation.audit(request))
                .isInstanceOf(ProviderCallException.class);
    }
}
