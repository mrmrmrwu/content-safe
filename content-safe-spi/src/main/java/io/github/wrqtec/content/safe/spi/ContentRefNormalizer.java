package io.github.wrqtec.content.safe.spi;

import io.github.wrqtec.content.safe.api.ContentRef;

/**
 * 内容引用规范化。用于将 provider 特定的 content 表达转换为统一 {@link ContentRef}。
 * 例如：把 byte[] 转 base64、把对象存储 key 转换为带签名的 URL。
 *
 * @author L.David
 */
public interface ContentRefNormalizer {

    /**
     * 供应商 key。
     *
     * @return 供应商 key
     */
    String provider();

    /**
     * 规范化。
     *
     * @param ref 原始内容引用，不得为 null
     * @return 规范化后的统一内容引用
     */
    ContentRef normalize(ContentRef ref);
}
