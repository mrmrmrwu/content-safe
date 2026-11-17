package io.github.wrqtec.content.safe.provider.volcengine.internal;

import com.volcengine.model.request.ImageRiskResultRequest;
import com.volcengine.model.request.RiskDetectionRequest;
import com.volcengine.model.response.AsyncRiskDetectionResponse;
import com.volcengine.model.response.ImageRiskDetectionResponse;
import com.volcengine.model.response.TextRiskResponse;
import com.volcengine.model.response.VideoRiskResultResponse;
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

    /** 单例配置互斥锁（构造期同步，运行期只读） */
    private static final Object SINGLETON_LOCK = new Object();

    private final ContentSecurityService service;

    public VolcengineSdkClientInvoker(Credentials credentials) {
        ContentSecurityService instance = ContentSecurityServiceImpl.getInstance();
        synchronized (SINGLETON_LOCK) {
            String existingAk = instance.getAccessKey();
            if (existingAk != null && !existingAk.isBlank()
                    && !existingAk.equals(credentials.accessKeyId())) {
                throw new io.github.wrqtec.content.safe.api.exception.ConfigInvalidException(
                        "volcengine SDK ContentSecurityService is a process-wide singleton already configured "
                                + "with a different account; refusing to overwrite credentials. "
                                + "Use a custom VolcengineClientInvoker bean or separate processes for multi-account");
            }
            this.service = instance;
            this.service.setAccessKey(credentials.accessKeyId());
            this.service.setSecretKey(credentials.accessKeySecret());
            // 空 token 显式置 null：避免残留上一个账号的会话令牌
            this.service.setSessionToken(credentials.securityToken() == null
                    || credentials.securityToken().isBlank() ? null : credentials.securityToken());
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

    @Override
    public AsyncRiskDetectionResponse asyncVideoRisk(RiskDetectionRequest request) throws Exception {
        return service.AsyncVideoRisk(request);
    }

    @Override
    public VideoRiskResultResponse videoResult(ImageRiskResultRequest request) throws Exception {
        return service.VideoResult(request);
    }
}
