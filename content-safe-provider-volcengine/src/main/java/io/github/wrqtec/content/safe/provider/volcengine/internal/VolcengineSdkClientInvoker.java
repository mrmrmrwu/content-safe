package io.github.wrqtec.content.safe.provider.volcengine.internal;

import com.volcengine.model.request.RiskDetectionRequest;
import com.volcengine.model.response.ImageRiskDetectionResponse;
import com.volcengine.model.response.TextRiskResponse;
import com.volcengine.service.contentSecurity.ContentSecurityService;
import com.volcengine.service.contentSecurity.impl.ContentSecurityServiceImpl;
import io.github.wrqtec.content.safe.spi.CredentialsProvider.Credentials;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于火山引擎官方 SDK 的 {@link VolcengineClientInvoker} 实现。唯一触碰
 * {@link ContentSecurityService} 的类；构造期只注入凭证，不做任何远程调用
 * （铁律 §5.7 启动轻量）。超时/端点由 SDK ServiceInfo 默认值决定
 * （connect/socket 各 5 秒，host riskcontrol.volcengineapi.com）
 *
 * <p>注意：SDK 的 {@link ContentSecurityServiceImpl#getInstance()} 为进程级
 * 单例，凭证通过 setter 注入；同进程多实例/多账号场景需自行隔离
 *
 * @author L.David
 */
@Slf4j
public final class VolcengineSdkClientInvoker implements VolcengineClientInvoker {

    private final ContentSecurityService service;

    public VolcengineSdkClientInvoker(Credentials credentials) {
        this.service = ContentSecurityServiceImpl.getInstance();
        this.service.setAccessKey(credentials.accessKeyId());
        this.service.setSecretKey(credentials.accessKeySecret());
        if (credentials.securityToken() != null && !credentials.securityToken().isBlank()) {
            this.service.setSessionToken(credentials.securityToken());
        }
    }

    @Override
    public TextRiskResponse textRisk(RiskDetectionRequest request) throws Exception {
        return service.TextRisk(request);
    }

    @Override
    public ImageRiskDetectionResponse imageContentRiskV2(RiskDetectionRequest request) throws Exception {
        return service.ImageRiskDetectionV2(request);
    }
}
