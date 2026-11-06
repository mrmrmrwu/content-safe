package io.github.wrqtec.content.safe.spi;

import java.util.List;

import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.CallbackRequest;
import io.github.wrqtec.content.safe.api.CallbackResult;
import io.github.wrqtec.content.safe.api.exception.UnsupportedFeatureException;
import io.github.wrqtec.content.safe.api.util.Asserts;
import io.github.wrqtec.content.safe.model.AsyncTaskRef;
import io.github.wrqtec.content.safe.model.AuditCapabilities;
import io.github.wrqtec.content.safe.model.ProviderPlan;

/**
 * 供应商能力操作。由 core 路由，provider 实现。SPI 层不做配置解析，只做调用。
 *
 * @author L.David
 */
public interface AuditOperation {

    /**
     * 操作计划。
     *
     * @return 该操作的计划声明，不为 null
     */
    ProviderPlan plan();

    /**
     * 能力声明。
     *
     * @return 该操作的能力声明，不为 null
     */
    AuditCapabilities capabilities();

    /**
     * 同步审核。
     *
     * @param request 审核请求，不得为 null
     * @return 归一化后的审核结果
     */
    AuditResult audit(AuditRequest request);

    /**
     * 异步提交。默认不支持，抛 {@link UnsupportedFeatureException}。
     *
     * @param request 审核请求，不得为 null
     * @return 供应商侧任务引用
     * @throws UnsupportedFeatureException 默认实现始终抛出
     */
    default AsyncTaskRef submitAsync(AuditRequest request) {
        throw new UnsupportedFeatureException("async submit not supported");
    }

    /**
     * 异步查询。默认不支持。
     *
     * @param ref 供应商侧任务引用，不得为 null
     * @return 归一化后的审核结果
     * @throws UnsupportedFeatureException 默认实现始终抛出
     */
    default AuditResult query(AsyncTaskRef ref) {
        throw new UnsupportedFeatureException("async query not supported");
    }

    /**
     * 回调验签 + 解析。默认不支持。
     *
     * @param request 回调请求，不得为 null
     * @return 回调解析结果；默认实现返回拒绝
     */
    default CallbackResult handleCallback(CallbackRequest request) {
        Asserts.notNull(request, "request");
        return CallbackResult.rejected("not supported");
    }

    /**
     * 供 {@link io.github.wrqtec.content.safe.spi.AuditRouter} 排序/过滤使用。
     *
     * @return 排序值，数值越小优先级越高；默认 0
     */
    default int order() {
        return 0;
    }

    /**
     * 是否支持当前请求。实现类可先走 {@code capabilities().supports(request)} 再叠加自定义规则。
     *
     * @param request 审核请求，不得为 null
     * @return true 表示可处理该请求
     */
    default boolean supports(AuditRequest request) {
        return capabilities().supports(request);
    }

    /**
     * 供应商侧批量提交，仅当 capabilities().batch() 为 true 时实现。
     *
     * @param requests 批量审核请求列表
     * @return 与请求一一对应的审核结果列表
     * @throws UnsupportedFeatureException 默认实现始终抛出
     */
    default List<AuditResult> batchAudit(List<AuditRequest> requests) {
        throw new UnsupportedFeatureException("batch not supported");
    }
}
