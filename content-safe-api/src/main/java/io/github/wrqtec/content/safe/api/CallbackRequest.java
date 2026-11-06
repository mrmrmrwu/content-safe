package io.github.wrqtec.content.safe.api;

import java.util.Map;

import io.github.wrqtec.content.safe.api.util.Asserts;

/**
 * 供应商异步回调入口报文，不可变
 *
 * @param provider 回调来源供应商（yml 中的供应商 key，如 {@code aliyun}）
 * @param headers  回调 HTTP 头（用于验签；key 统一小写）
 * @param rawBody  回调原始报文（验签必须使用原文，禁止反序列化后再拼装）
 *
 * @author L.David
 */
public record CallbackRequest(String provider, Map<String, String> headers, String rawBody) {

    public CallbackRequest {
        Asserts.notBlank(provider, "provider");
        Asserts.notNull(rawBody, "rawBody");
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }
}
