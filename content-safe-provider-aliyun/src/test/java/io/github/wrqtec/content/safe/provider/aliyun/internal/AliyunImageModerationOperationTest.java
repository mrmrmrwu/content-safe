package io.github.wrqtec.content.safe.provider.aliyun.internal;

import java.util.Map;

import com.aliyun.green20220302.models.ImageModerationResponseBody;
import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.ContentRef;
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
 * {@link AliyunImageModerationOperation} 单元测试：URL-only 门控与异常映射
 *
 * @author L.David
 */
class AliyunImageModerationOperationTest {

    private static final ProviderPlan PLAN = new ProviderPlan(
            "aliyun", "green-cip", "2022-03-02", "ImageModeration", "baselineCheck",
            AuditMode.SYNC, AliyunLimits.imageSync(), null);

    private static final String IMAGE_URL = "https://example.com/a.jpg";

    private final GreenClientInvoker invoker = mock(GreenClientInvoker.class);
    private final AliyunImageModerationOperation operation =
            new AliyunImageModerationOperation(PLAN, invoker, new AliyunAuditResultNormalizer());

    @Test
    void capabilitiesDeclareUrlOnly() {
        assertThat(operation.capabilities().contentTypes()).containsExactly(ContentType.IMAGE);
        assertThat(operation.capabilities().url()).isTrue();
        assertThat(operation.capabilities().base64()).isFalse();
        assertThat(operation.capabilities().bytes()).isFalse();
        assertThat(operation.capabilities().aigcDetect()).isFalse();
    }

    @Test
    void aigcServiceDeclaresCapability() {
        ProviderPlan aigcPlan = new ProviderPlan("aliyun", "green-cip", "2022-03-02",
                "ImageModeration", AliyunProviderConstants.SERVICE_AIGC_DETECTOR,
                AuditMode.SYNC, AliyunLimits.imageSync(), null);
        AliyunImageModerationOperation aigcOperation =
                new AliyunImageModerationOperation(aigcPlan, invoker, new AliyunAuditResultNormalizer());
        assertThat(aigcOperation.capabilities().aigcDetect()).isTrue();
    }

    @Test
    void happyPathAssemblesParamsAndNormalizes() throws Exception {
        ImageModerationResponseBody body = new ImageModerationResponseBody()
                .setCode(200).setRequestId("r-img-1")
                .setData(new ImageModerationResponseBody.ImageModerationResponseBodyData()
                        .setRiskLevel("none"));
        when(invoker.imageModeration(eq("baselineCheck"), anyMap())).thenReturn(body);

        AuditRequest request = AuditRequest.image("avatar", ContentRef.url(IMAGE_URL));
        AuditResult result = operation.audit(request);

        assertThat(result.suggestion()).isEqualTo(RiskSuggestion.PASS);
        assertThat(result.requestId()).isEqualTo("r-img-1");
        verify(invoker).imageModeration("baselineCheck", Map.of("imageUrl", IMAGE_URL));
    }

    @Test
    void rejectsBase64Content() {
        AuditRequest request = AuditRequest.builder()
                .scene("avatar").type(ContentType.IMAGE)
                .content(ContentRef.base64("aGVsbG8="))
                .build();
        assertThatThrownBy(() -> operation.audit(request))
                .isInstanceOf(UnsupportedContentException.class);
    }

    @Test
    void errorCodeMapsToProviderCallException() throws Exception {
        when(invoker.imageModeration(eq("baselineCheck"), anyMap()))
                .thenReturn(new ImageModerationResponseBody().setCode(592).setMsg("download timeout"));
        AuditRequest request = AuditRequest.image("avatar", ContentRef.url(IMAGE_URL));
        assertThatThrownBy(() -> operation.audit(request))
                .isInstanceOf(ProviderCallException.class)
                .hasMessageContaining("592");
    }
}
