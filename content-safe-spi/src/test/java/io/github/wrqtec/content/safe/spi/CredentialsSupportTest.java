package io.github.wrqtec.content.safe.spi;

import java.util.Map;
import java.util.Optional;

import io.github.wrqtec.content.safe.spi.CredentialsProvider.Credentials;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CredentialsSupport} 单元测试：静态凭据与环境变量回退
 *
 * @author L.David
 */
class CredentialsSupportTest {

    @Test
    void explicitCredentialsWin() {
        Map<String, String> env = Map.of(
                "CONTENT_SAFE_ALIYUN_AK", "env-ak",
                "CONTENT_SAFE_ALIYUN_SK", "env-sk");
        Optional<Credentials> credentials = CredentialsSupport.resolveStatic(
                "aliyun", "cfg-ak", "cfg-sk", null, env::get);
        assertTrue(credentials.isPresent());
        assertEquals("cfg-ak", credentials.get().accessKeyId());
        assertEquals("cfg-sk", credentials.get().accessKeySecret());
        assertNull(credentials.get().securityToken());
    }

    @Test
    void envFallbackWhenConfigMissing() {
        Map<String, String> env = Map.of(
                "CONTENT_SAFE_ALIYUN_AK", "env-ak",
                "CONTENT_SAFE_ALIYUN_SK", "env-sk",
                "CONTENT_SAFE_ALIYUN_STSTOKEN", "env-token");
        Optional<Credentials> credentials = CredentialsSupport.resolveStatic(
                "aliyun", null, "  ", null, env::get);
        assertTrue(credentials.isPresent());
        assertEquals("env-ak", credentials.get().accessKeyId());
        assertEquals("env-sk", credentials.get().accessKeySecret());
        assertEquals("env-token", credentials.get().securityToken());
    }

    @Test
    void emptyWhenNothingConfigured() {
        Optional<Credentials> credentials = CredentialsSupport.resolveStatic(
                "aliyun", null, null, null, key -> null);
        assertTrue(credentials.isEmpty());
    }

    @Test
    void envKeysDerivedFromProviderName() {
        assertEquals("CONTENT_SAFE_ALIYUN_AK", CredentialsSupport.accessKeyIdEnvKey("aliyun"));
        assertEquals("CONTENT_SAFE_TENCENT_CLOUD_SK", CredentialsSupport.accessKeySecretEnvKey("tencent-cloud"));
        assertEquals("CONTENT_SAFE_ALIYUN_STSTOKEN", CredentialsSupport.securityTokenEnvKey("aliyun"));
    }

    @Test
    void partialCredentialsResolveEmpty() {
        // 仅 ak 无 sk：不完整，返回 empty 由调用方决定报错文案
        assertTrue(CredentialsSupport.resolveStatic("aliyun", "cfg-ak", null, null, key -> null).isEmpty());
        assertTrue(CredentialsSupport.resolveStatic("aliyun", null, "cfg-sk", null, key -> null).isEmpty());
    }
}
