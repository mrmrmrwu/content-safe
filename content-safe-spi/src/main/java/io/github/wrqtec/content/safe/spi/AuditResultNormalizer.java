package io.github.wrqtec.content.safe.spi;

import java.util.Map;

import io.github.wrqtec.content.safe.api.AuditResult;

/**
 * 供应商响应归一化。将各 provider 返回的异构结构转换为统一 {@link AuditResult}。
 * provider 内部实现，core 不感知
 *
 * @author L.David
 */
public interface AuditResultNormalizer {

    /**
     * 供应商 key
     *
     * @return 供应商 key
     */
    String provider();

    /**
     * 归一化。rawResponse 为 provider SDK 原始响应对象（provider 模块内部类型）
     *
     * @param rawResponse provider SDK 原始响应对象
     * @param context     归一化上下文（如场景、请求参数），可为 null
     * @return 归一化后的统一审核结果
     */
    AuditResult normalize(Object rawResponse, Map<String, Object> context);
}
