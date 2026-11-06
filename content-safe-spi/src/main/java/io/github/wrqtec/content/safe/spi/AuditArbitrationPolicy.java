package io.github.wrqtec.content.safe.spi;

import io.github.wrqtec.content.safe.api.AuditResult;

import java.util.List;

/**
 * 多供应商结果仲裁。将多个 provider 的 {@link AuditResult} 合并为一个统一结果
 *
 * @author L.David
 */
public interface AuditArbitrationPolicy {

    /**
     * 仲裁策略名称（用于日志/诊断）
     *
     * @return 策略名称
     */
    String name();

    /**
     * 仲裁。输入列表必须非空；返回一个统一结果
     *
     * @param results 各供应商的审核结果，非空
     * @return 仲裁后的统一审核结果
     */
    AuditResult arbitrate(List<AuditResult> results);
}
