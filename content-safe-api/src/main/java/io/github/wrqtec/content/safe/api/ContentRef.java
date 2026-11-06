package io.github.wrqtec.content.safe.api;

import java.util.Map;

import io.github.wrqtec.content.safe.api.enums.ContentRefType;
import io.github.wrqtec.content.safe.api.util.Asserts;

/**
 * 待审核内容引用，不可变；{@code bytes} 与 {@code meta} 均做防御性拷贝
 *
 * <p>注意：{@code value}/{@code bytes} 属于用户内容原文，禁止进入日志与异常 message</p>
 *
 * @author L.David
 */
public record ContentRef(ContentRefType type, String value, byte[] bytes, Map<String, Object> meta) {

    public ContentRef {
        Asserts.notNull(type, "type");
        bytes = bytes == null ? null : bytes.clone();
        meta = meta == null ? Map.of() : Map.copyOf(meta);
    }

    @Override
    public byte[] bytes() {
        return bytes == null ? null : bytes.clone();
    }

    /**
     * 构造纯文本内容引用
     *
     * @param text 文本原文，非空白
     * @return 内容引用
     */
    public static ContentRef text(String text) {
        return new ContentRef(ContentRefType.TEXT, Asserts.notBlank(text, "text"), null, null);
    }

    /**
     * 构造公网 URL 内容引用
     *
     * @param url 公网可访问的 URL，非空白
     * @return 内容引用
     */
    public static ContentRef url(String url) {
        return new ContentRef(ContentRefType.URL, Asserts.notBlank(url, "url"), null, null);
    }

    /**
     * 构造 Base64 编码内容引用
     *
     * @param base64 Base64 编码内容，非空白
     * @return 内容引用
     */
    public static ContentRef base64(String base64) {
        return new ContentRef(ContentRefType.BASE64, Asserts.notBlank(base64, "base64"), null, null);
    }

    /**
     * 构造原始字节内容引用
     *
     * @param bytes 原始字节，非 {@code null}
     * @return 内容引用
     */
    public static ContentRef bytes(byte[] bytes) {
        return new ContentRef(ContentRefType.BYTES, null, Asserts.notNull(bytes, "bytes"), null);
    }

    /**
     * 构造对象存储 key 内容引用
     *
     * @param objectKey 对象存储 key，非空白
     * @return 内容引用
     */
    public static ContentRef objectKey(String objectKey) {
        return new ContentRef(ContentRefType.OBJECT_KEY, Asserts.notBlank(objectKey, "objectKey"), null, null);
    }
}
