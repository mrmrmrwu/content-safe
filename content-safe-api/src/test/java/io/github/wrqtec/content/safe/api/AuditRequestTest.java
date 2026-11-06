package io.github.wrqtec.content.safe.api;

import java.util.Map;

import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link AuditRequest} 单元测试
 *
 * @author L.David
 */
class AuditRequestTest {

    @Test
    void textShortcutBuildsTextRequest() {
        AuditRequest request = AuditRequest.text("nickname", "张三");
        assertEquals("nickname", request.scene());
        assertEquals(ContentType.TEXT, request.type());
        assertEquals("张三", request.content().value());
        assertNotNull(request.options());
    }

    @Test
    void imageShortcutBuildsImageRequest() {
        AuditRequest request = AuditRequest.image("avatar", ContentRef.url("https://example.com/a.jpg"));
        assertEquals(ContentType.IMAGE, request.type());
    }

    @Test
    void builderSupportsFullChain() {
        AuditRequest request = AuditRequest.builder()
                .scene("avatar")
                .type(ContentType.IMAGE)
                .content(ContentRef.url("https://example.com/a.jpg"))
                .dataId("user-1")
                .context(AuditContext.ofUser("user-1", "device-9"))
                .option(AuditOptions.enableOcr(true))
                .passThrough(Map.of("tenant", "t1"))
                .build();
        assertEquals("user-1", request.dataId());
        assertEquals("device-9", request.context().deviceId());
        assertEquals(Boolean.TRUE, request.options().enableOcr());
        assertEquals("t1", request.passThrough().get("tenant"));
    }

    @Test
    void missingSceneRejected() {
        assertThrows(InvalidRequestException.class,
                () -> AuditRequest.builder().type(ContentType.TEXT)
                        .content(ContentRef.text("x")).build());
    }

    @Test
    void missingContentRejected() {
        assertThrows(InvalidRequestException.class,
                () -> AuditRequest.builder().scene("nickname").type(ContentType.TEXT).build());
    }

    @Test
    void passThroughIsImmutable() {
        AuditRequest request = AuditRequest.builder()
                .scene("nickname").type(ContentType.TEXT).content(ContentRef.text("x"))
                .passThrough(Map.of("k", "v")).build();
        assertThrows(UnsupportedOperationException.class, () -> request.passThrough().put("a", "b"));
    }
}
