package io.github.wrqtec.content.safe.spi;

import java.util.List;

import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.exception.UnsupportedFeatureException;

/**
 * 路由：从候选操作中选出唯一执行者。可扩展实现（如按健康、成本、QPS、场景路由）。
 * 禁止出现供应商 if/switch，只依赖 {@code capabilities/plan} 与请求参数
 *
 * @author L.David
 */
public interface AuditRouter {

    /**
     * 从候选中选择一个可执行操作。不允许返回 null
     *
     * @param request    审核请求，不得为 null
     * @param candidates 候选操作列表，非空
     * @return 选中的可执行操作，不为 null
     * @throws UnsupportedFeatureException 若无可用操作
     */
    AuditOperation route(AuditRequest request, List<AuditOperation> candidates);
}
