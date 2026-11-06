package io.github.wrqtec.content.safe.spi;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link CredentialsProvider} 及其 {@link CredentialsProvider.Credentials} 单元测试。
 *
 * @author L.David
 */
class CredentialsProviderTest {

    @Test
    void credentialsValidation() {
        assertThrows(IllegalArgumentException.class,
                () -> new CredentialsProvider.Credentials(" ", "s", null));
        assertThrows(IllegalArgumentException.class,
                () -> new CredentialsProvider.Credentials("id", null, null));
        CredentialsProvider.Credentials c = new CredentialsProvider.Credentials("id", "secret", null);
        assertEquals("id", c.accessKeyId());
    }

    @Test
    void minimalImplementationCompiles() {
        CredentialsProvider provider = new CredentialsProvider() {
            @Override
            public String provider() {
                return "fake";
            }

            @Override
            public Credentials resolve(Map<String, Object> config) {
                return new Credentials((String) config.get("ak"), (String) config.get("sk"), null);
            }
        };
        CredentialsProvider.Credentials c = provider.resolve(Map.of("ak", "a", "sk", "b"));
        assertEquals("a", c.accessKeyId());
    }
}
