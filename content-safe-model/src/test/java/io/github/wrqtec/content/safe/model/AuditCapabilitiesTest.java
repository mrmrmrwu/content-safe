package io.github.wrqtec.content.safe.model;

import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.ContentRef;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AuditCapabilities} 单元测试。
 *
 * @author L.David
 */
class AuditCapabilitiesTest {

    @Test
    void supportsChecksContentTypeAndRefType() {
        AuditCapabilities caps = AuditCapabilities.builder()
                .contentTypes(ContentType.TEXT, ContentType.IMAGE)
                .sync(true)
                .url(true)
                .base64(false)
                .build();

        assertTrue(caps.supports(AuditRequest.text("s", "你好")));
        assertTrue(caps.supports(AuditRequest.image("s",
                ContentRef.url("https://example.com/a.jpg"))));
        assertFalse(caps.supports(new AuditRequest("s", ContentType.IMAGE,
                ContentRef.base64("xxx"), null, null, null, null)));
        assertFalse(caps.supports(new AuditRequest("s", ContentType.AUDIO,
                ContentRef.text("xx"), null, null, null, null)));
    }

    @Test
    void nullRequestRejected() {
        AuditCapabilities caps = AuditCapabilities.builder().contentTypes(ContentType.TEXT).build();
        assertThrows(InvalidRequestException.class, () -> caps.supports(null));
    }

    @Test
    void builderDefaultsAreImmutable() {
        AuditCapabilities caps = AuditCapabilities.builder()
                .contentTypes(ContentType.TEXT)
                .sync(true)
                .build();
        assertTrue(caps.sync());
        assertTrue(caps.contentTypes().contains(ContentType.TEXT));
        assertThrows(UnsupportedOperationException.class, () -> caps.contentTypes().add(ContentType.IMAGE));
    }
}
