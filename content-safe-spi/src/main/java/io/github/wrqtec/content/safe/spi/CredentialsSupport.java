package io.github.wrqtec.content.safe.spi;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

import io.github.wrqtec.content.safe.spi.CredentialsProvider.Credentials;

/**
 * 静态凭据解析的公共支撑：统一实现"显式配置优先、缺省回退环境变量"的解析约定，
 * 供 starter 与各 provider 模块复用，避免各自新造平行体系
 *
 * <p>环境变量约定：{@code CONTENT_SAFE_<PROVIDER>_AK} / {@code CONTENT_SAFE_<PROVIDER>_SK} /
 * {@code CONTENT_SAFE_<PROVIDER>_STSTOKEN}（provider 名中的 '-' 转 '_' 并大写）
 *
 * <p>安全约束：本类只搬运密钥值，任何异常与日志不得包含密钥内容
 *
 * @author L.David
 */
public final class CredentialsSupport {

    /** 环境变量前缀，如 CONTENT_SAFE_ALIYUN_AK */
    public static final String ENV_PREFIX = "CONTENT_SAFE_";

    /** 环境变量后缀 */
    public static final String ENV_SUFFIX_AK = "AK";
    public static final String ENV_SUFFIX_SK = "SK";
    public static final String ENV_SUFFIX_STS_TOKEN = "STSTOKEN";

    private CredentialsSupport() {
    }

    /**
     * 按约定回退环境变量后解析静态凭据
     *
     * @param provider      供应商 key（如 aliyun）
     * @param accessKeyId   显式配置的 AK，可为空（空则回退环境变量）
     * @param accessKeySecret 显式配置的 SK，可为空（空则回退环境变量）
     * @param securityToken 显式配置的 STS Token，可为空（空则回退环境变量）
     * @param envLookup     环境变量查询函数，测试可注入
     * @return 解析成功的凭据；AK/SK 任一缺失时返回 {@link Optional#empty()}，由调用方决定报错文案
     */
    public static Optional<Credentials> resolveStatic(String provider, String accessKeyId,
                                                      String accessKeySecret, String securityToken,
                                                      Function<String, String> envLookup) {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider must not be blank");
        }
        if (envLookup == null) {
            throw new IllegalArgumentException("envLookup must not be null");
        }
        String ak = accessKeyId;
        String sk = accessKeySecret;
        String token = securityToken;
        if (isBlank(ak)) {
            ak = envLookup.apply(accessKeyIdEnvKey(provider));
        }
        if (isBlank(sk)) {
            sk = envLookup.apply(accessKeySecretEnvKey(provider));
        }
        if (isBlank(token)) {
            token = envLookup.apply(securityTokenEnvKey(provider));
        }
        if (isBlank(ak) || isBlank(sk)) {
            return Optional.empty();
        }
        return Optional.of(new Credentials(ak, sk, token));
    }

    /** AK 环境变量名，如 CONTENT_SAFE_ALIYUN_AK */
    public static String accessKeyIdEnvKey(String provider) {
        return envKey(provider, ENV_SUFFIX_AK);
    }

    /** SK 环境变量名，如 CONTENT_SAFE_ALIYUN_SK */
    public static String accessKeySecretEnvKey(String provider) {
        return envKey(provider, ENV_SUFFIX_SK);
    }

    /** STS Token 环境变量名，如 CONTENT_SAFE_ALIYUN_STSTOKEN */
    public static String securityTokenEnvKey(String provider) {
        return envKey(provider, ENV_SUFFIX_STS_TOKEN);
    }

    /** 环境变量名推导：provider 名中的 '-' 转 '_' 并大写 */
    public static String envKey(String provider, String suffix) {
        return ENV_PREFIX + provider.replace('-', '_').toUpperCase(Locale.ROOT)
                + "_" + suffix;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
