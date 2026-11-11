package io.github.wrqtec.content.safe.model;

import java.time.Duration;
import java.util.Set;

import io.github.wrqtec.content.safe.api.AuditOptions;
import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.ContentRef;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AuditCapabilities} 单元测试
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
    void aigcSwitchRequiresCapability() {
        AuditCapabilities plain = AuditCapabilities.builder()
                .contentTypes(ContentType.TEXT).sync(true).build();
        AuditCapabilities aigc = AuditCapabilities.builder()
                .contentTypes(ContentType.TEXT).sync(true).aigcDetect(true).build();
        AuditRequest aigcOn = AuditRequest.builder()
                .scene("s")
                .type(ContentType.TEXT)
                .content(ContentRef.text("你好"))
                .options(AuditOptions.defaults().withAigcDetect(true))
                .build();

        assertFalse(plain.supports(aigcOn));
        assertTrue(aigc.supports(aigcOn));
        // 开关关闭或缺省不触发门控
        assertTrue(plain.supports(AuditRequest.text("s", "你好")));
        assertTrue(aigc.supports(AuditRequest.text("s", "你好")));
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

    @Test
    void suggestionsDeclaredViaBuilderAreRetained() {
        AuditCapabilities caps = AuditCapabilities.builder()
                .contentTypes(ContentType.VIDEO)
                .asyncQuery(true)
                .suggestedPollInterval(Duration.ofSeconds(30))
                .suggestedPollTimeout(Duration.ofMinutes(30))
                .build();
        assertEquals(Duration.ofSeconds(30), caps.suggestedPollInterval());
        assertEquals(Duration.ofMinutes(30), caps.suggestedPollTimeout());

        AuditCapabilities without = AuditCapabilities.builder()
                .contentTypes(ContentType.VIDEO)
                .asyncQuery(true)
                .build();
        assertNull(without.suggestedPollInterval());
        assertNull(without.suggestedPollTimeout());
    }

    @Test
    void legacyConstructorDefaultsSuggestionsToNull() {
        AuditCapabilities caps = new AuditCapabilities(Set.of(ContentType.TEXT), true,
                false, false, false, false, false, false, false,
                false, false, false, false, false, false, 1, 0L, 0L);
        assertNull(caps.suggestedPollInterval());
        assertNull(caps.suggestedPollTimeout());
    }
}
