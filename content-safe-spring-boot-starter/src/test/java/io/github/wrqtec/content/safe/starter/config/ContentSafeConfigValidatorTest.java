package io.github.wrqtec.content.safe.starter.config;

import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import io.github.wrqtec.content.safe.api.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentSafeConfigValidatorTest {

    @Test
    void acceptsSupportedSchemaVersion() {
        ContentSafeProperties properties = new ContentSafeProperties();
        properties.setSchemaVersion(ContentSafeConfigValidator.SUPPORTED_SCHEMA_VERSION);

        assertThatCode(() -> new ContentSafeConfigValidator(properties)).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnsupportedSchemaVersionWithReadableMessage() {
        ContentSafeProperties properties = new ContentSafeProperties();
        properties.setSchemaVersion(2);

        assertThatThrownBy(() -> new ContentSafeConfigValidator(properties))
                .isInstanceOf(ConfigInvalidException.class)
                .hasMessageContaining("content.safe.schema-version")
                .hasMessageContaining("2")
                .hasMessageContaining("supported: 1");
    }

    @Test
    void rejectsNullProperties() {
        assertThatThrownBy(() -> new ContentSafeConfigValidator(null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("properties");
    }
}
