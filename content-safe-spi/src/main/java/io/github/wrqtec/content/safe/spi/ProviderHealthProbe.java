package io.github.wrqtec.content.safe.spi;

import io.github.wrqtec.content.safe.model.ProviderHealth;

/**
 * 健康探测。core 定期调用，结果写入健康注册表。
 * 实现方应采用轻量探活，避免触发计费或高频限流
 *
 * @author L.David
 * @deprecated 自 1.0.0 发布以来全仓库无任何调用方：core 编排层没有健康注册表，
 *     也没有任何后台探测调度，<b>实现本接口不会产生任何效果</b>（可用性由熔断器
 *     与限流护栏在调用路径上动态判定，无需主动探活）。因 1.0.0 已发布无法物理删除
 *     （二进制兼容门禁），计划 2.0.0 移除；如有主动健康探测的真实需求请提 issue 说明
 */
@Deprecated(since = "1.1.0", forRemoval = true)
public interface ProviderHealthProbe {

    /**
     * 探测某操作当前健康状态
     *
     * @param operation 待探测的操作，不得为 null
     * @return 健康快照，不为 null
     */
    ProviderHealth probe(AuditOperation operation);

    /**
     * 探测间隔（秒）
     *
     * @return 探测间隔秒数，默认 30
     */
    default long intervalSeconds() {
        return 30L;
    }
}
