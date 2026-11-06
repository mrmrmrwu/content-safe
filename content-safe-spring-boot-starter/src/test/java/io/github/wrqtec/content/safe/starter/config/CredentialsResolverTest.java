package io.github.wrqtec.content.safe.starter.config;

import java.util.List;
import java.util.Map;

import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import io.github.wrqtec.content.safe.spi.CredentialsProvider;
import io.github.wrqtec.content.safe.spi.CredentialsProvider.Credentials;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CredentialsResolverTest {

    private static CredentialsResolver resolver(ContentSafeProperties properties,
                                                Map<String, String> env,
                                                CredentialsProvider... custom) {
        return new CredentialsResolver(List.of(custom), properties, env::get);
    }

    @Test
    void staticCredentialsFromProperties() {
        ContentSafeProperties properties = new ContentSafeProperties();
        ContentSafeProperties.ProviderProperties aliyun = new ContentSafeProperties.ProviderProperties();
        aliyun.setAk("ak-1");
        aliyun.setSk("sk-1");
        properties.getProviders().put("aliyun", aliyun);

        Credentials credentials = resolver(properties, Map.of()).resolve("aliyun");
        assertEquals("ak-1", credentials.accessKeyId());
        assertEquals("sk-1", credentials.accessKeySecret());
        assertNull(credentials.securityToken());
    }

    @Test
    void fallsBackToEnvironmentVariables() {
        Credentials credentials = resolver(new ContentSafeProperties(),
                Map.of("CONTENT_SAFE_TENCENT_AK", "env-ak", "CONTENT_SAFE_TENCENT_SK", "env-sk"))
                .resolve("tencent");
        assertEquals("env-ak", credentials.accessKeyId());
        assertEquals("env-sk", credentials.accessKeySecret());
    }

    @Test
    void customProviderTakesPrecedence() {
        ContentSafeProperties properties = new ContentSafeProperties();
        ContentSafeProperties.ProviderProperties aliyun = new ContentSafeProperties.ProviderProperties();
        aliyun.setAk("static-ak");
        aliyun.setSk("static-sk");
        properties.getProviders().put("aliyun", aliyun);

        CredentialsProvider sts = new CredentialsProvider() {
            @Override
            public String provider() {
                return "aliyun";
            }

            @Override
            public Credentials resolve(Map<String, Object> config) {
                return new Credentials("sts-ak", "sts-sk", "sts-token");
            }
        };

        Credentials credentials = resolver(properties, Map.of(), sts).resolve("aliyun");
        assertEquals("sts-ak", credentials.accessKeyId());
        assertEquals("sts-token", credentials.securityToken());
    }

    @Test
    void missingCredentialsThrowsWithoutLeakingSecrets() {
        ContentSafeProperties properties = new ContentSafeProperties();
        ContentSafeProperties.ProviderProperties aliyun = new ContentSafeProperties.ProviderProperties();
        aliyun.setAk("only-ak-configured");
        properties.getProviders().put("aliyun", aliyun);

        ConfigInvalidException e = assertThrows(ConfigInvalidException.class,
                () -> resolver(properties, Map.of()).resolve("aliyun"));
        assertFalse(e.getMessage().contains("only-ak-configured"));
    }

    @Test
    void configOfMergesExtra() {
        ContentSafeProperties properties = new ContentSafeProperties();
        ContentSafeProperties.ProviderProperties aliyun = new ContentSafeProperties.ProviderProperties();
        aliyun.setEndpoint("endpoint-1");
        aliyun.getExtra().put("custom", "v1");
        properties.getProviders().put("aliyun", aliyun);

        Map<String, Object> config = resolver(properties, Map.of()).configOf("aliyun");
        assertEquals("endpoint-1", config.get("endpoint"));
        assertEquals("v1", config.get("custom"));
    }
}
