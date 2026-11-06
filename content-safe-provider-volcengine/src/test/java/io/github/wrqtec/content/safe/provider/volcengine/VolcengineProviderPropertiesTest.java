package io.github.wrqtec.content.safe.provider.volcengine;

import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VolcengineProviderPropertiesTest {

    @Test
    void disabledSkipsValidation() {
        VolcengineProviderProperties properties = new VolcengineProviderProperties();
        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    void enabledRequiresAppId() {
        VolcengineProviderProperties properties = new VolcengineProviderProperties();
        properties.setEnabled(true);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(ConfigInvalidException.class)
                .hasMessageContaining("app-id");
    }

    @Test
    void appIdMustBeInteger() {
        VolcengineProviderProperties properties = new VolcengineProviderProperties();
        properties.setEnabled(true);
        properties.getExtra().put(VolcengineProviderConstants.EXTRA_APP_ID, "not-a-number");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(ConfigInvalidException.class)
                .hasMessageContaining("integer");
    }

    @Test
    void validConfigPasses() {
        VolcengineProviderProperties properties = new VolcengineProviderProperties();
        properties.setEnabled(true);
        properties.getExtra().put(VolcengineProviderConstants.EXTRA_APP_ID, " 6801 ");

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }
}
