package io.github.wrqtec.content.safe.api.enums;

/**
 * 内容引用方式。是否可用取决于 provider 的 {@code AuditCapabilities} 声明。
 *
 * @author L.David
 */
public enum ContentRefType {

    /** 纯文本 */
    TEXT,
    /** 公网可访问的 URL */
    URL,
    /** Base64 编码内容 */
    BASE64,
    /** 原始字节 */
    BYTES,
    /** 对象存储 key（由 provider 侧转换为签名 URL） */
    OBJECT_KEY
}
