package io.github.wrqtec.content.safe.provider.aliyun.internal;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import io.github.wrqtec.content.safe.api.CallbackRequest;
import io.github.wrqtec.content.safe.provider.aliyun.AliyunProviderConstants;
import io.github.wrqtec.content.safe.provider.aliyun.AliyunProviderProperties;
import io.github.wrqtec.content.safe.spi.CallbackVerifier;
import lombok.extern.slf4j.Slf4j;

/**
 * 阿里云异步检测回调验签。官方契约（help.aliyun.com/doc-detail/2638108，查阅 2026-11-07）：
 * 回调为 application/x-www-form-urlencoded 表单，参数 ReqId / Checksum / Content；
 * {@code Checksum = SHA256_HEX(主账号UID + seed + Content)}，本实现仅支持 SHA256
 * （cryptType 默认值）。seed 取 extra callback-seed，缺省回退 dispatcher 注入的
 * secret；主账号 UID 取 extra callback-uid（非 RAM 用户 UID），未配置时验签直接失败。
 * 防重放现状（如实声明）：官方协议的 Checksum 不含时间戳/nonce，本验签<b>不防重放</b>——
 * 一次截获的合法回调可被无限重放（终态迁移幂等，重放无二次事件，但有解析成本）。
 * 防重放是 CallbackVerifier 的契约责任，本实现受协议所限无法履行，部署方须在网络层
 * 补偿：回调地址仅内网可达、来源 IP 白名单、网关限流（另见
 * content.safe.callback.rate-limit）。日志绝不输出 Content 原文与 seed 值（铁律 §9.4）
 *
 * @author L.David
 */
@Slf4j
public class AliyunCallbackVerifier implements CallbackVerifier {

    /** 回调表单字段名（官方固定） */
    static final String FIELD_CHECKSUM = "Checksum";
    static final String FIELD_CONTENT = "Content";

    private final String uid;
    private final String seed;

    public AliyunCallbackVerifier(AliyunProviderProperties properties) {
        this.uid = trimToNull(properties.getExtra().get(AliyunProviderConstants.EXTRA_CALLBACK_UID));
        this.seed = trimToNull(properties.getExtra().get(AliyunProviderConstants.EXTRA_CALLBACK_SEED));
    }

    @Override
    public String provider() {
        return AliyunProviderConstants.PROVIDER;
    }

    @Override
    public boolean verify(CallbackRequest request, String secret, Map<String, String> ext) {
        String effectiveSeed = seed != null ? seed : secret;
        if (uid == null || effectiveSeed == null) {
            log.warn("aliyun callback verify failed: missing config {}",
                    uid == null
                            ? AliyunProviderConstants.EXTRA_CALLBACK_UID
                            : AliyunProviderConstants.EXTRA_CALLBACK_SEED);
            return false;
        }
        Map<String, String> form = parseForm(request.rawBody());
        String content = form.get(FIELD_CONTENT);
        String checksum = form.get(FIELD_CHECKSUM);
        if (content == null || checksum == null) {
            log.warn("aliyun callback verify failed: missing {} or {} field", FIELD_CHECKSUM, FIELD_CONTENT);
            return false;
        }
        String expected = sha256Hex(uid + effectiveSeed + content);
        // 常量时间比较防时序侧信道；官方未注明大小写口径，统一小写比较
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                checksum.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    /** 解析 application/x-www-form-urlencoded 表单（回调原文，禁止反序列化后拼装） */
    static Map<String, String> parseForm(String rawBody) {
        Map<String, String> form = new LinkedHashMap<>();
        if (rawBody == null || rawBody.isBlank()) {
            return form;
        }
        for (String pair : rawBody.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            form.put(key, value);
        }
        return form;
    }

    static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16))
                        .append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM missing SHA-256 digest", e);
        }
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
