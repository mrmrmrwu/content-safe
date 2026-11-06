package io.github.wrqtec.content.safe.api;

import java.time.Duration;
import java.util.Map;

/**
 * 单次审核的可选开关。字段为 {@code null} 表示未指定，由 provider 配置兜底。不可变。
 *
 * @author L.David
 */
public record AuditOptions(Boolean enableOcr, Boolean enableAigcDetect, Duration timeout, Map<String, Object> ext) {

    public AuditOptions {
        ext = ext == null ? Map.of() : Map.copyOf(ext);
    }

    /**
     * 全部选项未指定的默认实例。
     *
     * @return 默认审核选项
     */
    public static AuditOptions defaults() {
        return new AuditOptions(null, null, null, null);
    }

    /**
     * 仅设置 OCR 开关的快捷构造。
     *
     * @param enableOcr 是否启用 OCR
     * @return 审核选项
     */
    public static AuditOptions enableOcr(boolean enableOcr) {
        return new AuditOptions(enableOcr, null, null, null);
    }

    /**
     * 派生一个设置超时的新实例。
     *
     * @param timeout 本次调用超时
     * @return 新审核选项
     */
    public AuditOptions withTimeout(Duration timeout) {
        return new AuditOptions(enableOcr, enableAigcDetect, timeout, ext);
    }

    /**
     * 派生一个设置 AIGC 检测开关的新实例。
     *
     * @param enableAigcDetect 是否启用 AIGC 检测
     * @return 新审核选项
     */
    public AuditOptions withAigcDetect(boolean enableAigcDetect) {
        return new AuditOptions(enableOcr, enableAigcDetect, timeout, ext);
    }
}
