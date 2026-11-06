package io.github.wrqtec.content.safe.spi;

import io.github.wrqtec.content.safe.model.ProviderHealth;

/**
 * 健康探测。core 定期调用，结果写入健康注册表。
 * 实现方应走轻量探活，避免触发计费或高频限流。
 *
 * @author L.David
 */
public interface ProviderHealthProbe {

    /**
     * 探测某操作当前健康状态。
     *
     * @param operation 待探测的操作，不得为 null
     * @return 健康快照，不为 null
     */
    ProviderHealth probe(AuditOperation operation);

    /**
     * 探测间隔（秒）。
     *
     * @return 探测间隔秒数，默认 30
     */
    default long intervalSeconds() {
        return 30L;
    }
}
