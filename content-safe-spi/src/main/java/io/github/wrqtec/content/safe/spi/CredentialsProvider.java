package io.github.wrqtec.content.safe.spi;

import java.util.Map;

/**
 * 凭据提供器。从配置/环境/KMS 中解析供应商访问密钥。禁止把密钥写入日志
 *
 * @author L.David
 */
public interface CredentialsProvider {

    /**
     * 供应商 key
     *
     * @return 供应商 key
     */
    String provider();

    /**
     * 解析访问密钥
     *
     * @param config 供应商配置（如 yml 中 content.safe.providers.aliyun 下的子树）
     * @return 密钥对；不可用时抛异常
     */
    Credentials resolve(Map<String, Object> config);

    /**
     * 密钥对
     *
     * @param accessKeyId     访问密钥 ID，不得为空
     * @param accessKeySecret 访问密钥 Secret，不得为空
     * @param securityToken   临时安全令牌，可为 null
     * @author L.David
     */
    record Credentials(String accessKeyId, String accessKeySecret, String securityToken) {
        public Credentials {
            if (accessKeyId == null || accessKeyId.isBlank()) {
                throw new IllegalArgumentException("accessKeyId must not be blank");
            }
            if (accessKeySecret == null || accessKeySecret.isBlank()) {
                throw new IllegalArgumentException("accessKeySecret must not be blank");
            }
        }
    }
}
