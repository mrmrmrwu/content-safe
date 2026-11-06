package io.github.wrqtec.content.safe.provider.tencent;

import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link TencentProviderProperties} 校验规则测试
 *
 * @author L.David
 */
class TencentProviderPropertiesTest {

    @Test
    void disabledSkipsValidation() {
        TencentProviderProperties properties = new TencentProviderProperties();
        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    void enabledRequiresCredentials() {
        TencentProviderProperties properties = new TencentProviderProperties();
        properties.setEnabled(true);
        assertThatThrownBy(properties::validate)
                .isInstanceOf(ConfigInvalidException.class)
                .hasMessageContaining("secretId/secretKey");

        properties.setSecretId("sid");
        assertThatThrownBy(properties::validate).isInstanceOf(ConfigInvalidException.class);

        properties.setSecretKey("skey");
        assertThatCode(properties::validate).doesNotThrowAnyException();
    }
}
