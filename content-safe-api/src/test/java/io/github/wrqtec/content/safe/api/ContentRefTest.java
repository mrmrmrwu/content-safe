package io.github.wrqtec.content.safe.api;

import java.util.Map;

import io.github.wrqtec.content.safe.api.enums.ContentRefType;
import io.github.wrqtec.content.safe.api.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link ContentRef} 单元测试。
 *
 * @author L.David
 */
class ContentRefTest {

    @Test
    void textFactorySetsTypeAndValue() {
        ContentRef ref = ContentRef.text("hello");
        assertEquals(ContentRefType.TEXT, ref.type());
        assertEquals("hello", ref.value());
        assertNull(ref.bytes());
    }

    @Test
    void urlFactoryRejectsBlank() {
        assertThrows(InvalidRequestException.class, () -> ContentRef.url("  "));
    }

    @Test
    void nullTypeRejected() {
        assertThrows(InvalidRequestException.class,
                () -> new ContentRef(null, "x", null, null));
    }

    @Test
    void bytesAreDefensivelyCopied() {
        byte[] origin = {1, 2, 3};
        ContentRef ref = ContentRef.bytes(origin);
        origin[0] = 9;
        assertArrayEquals(new byte[] {1, 2, 3}, ref.bytes());

        byte[] fetched = ref.bytes();
        fetched[0] = 9;
        assertArrayEquals(new byte[] {1, 2, 3}, ref.bytes());
    }

    @Test
    void metaIsCopiedAndImmutable() {
        ContentRef ref = new ContentRef(ContentRefType.URL, "https://example.com/a.jpg", null,
                Map.of("bucket", "img"));
        assertEquals("img", ref.meta().get("bucket"));
        assertThrows(UnsupportedOperationException.class, () -> ref.meta().put("k", "v"));
    }

    @Test
    void nullMetaBecomesEmpty() {
        ContentRef ref = ContentRef.objectKey("oss://bucket/key");
        assertEquals(Map.of(), ref.meta());
    }
}
