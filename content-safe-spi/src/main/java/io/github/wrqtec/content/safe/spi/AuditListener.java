package io.github.wrqtec.content.safe.spi;

import io.github.wrqtec.content.safe.model.event.ContentSafeEvent;

/**
 * 事件监听。core 在审核完成/风险命中/人工复核/降级/死信时发布。
 * 实现方处理异步落库、告警、统计等
 *
 * @author L.David
 */
public interface AuditListener {

    /**
     * 消费事件。必须同步返回，异步处理由实现方自行入队；
     * 实现不得抛出异常——直连监听器抛出的异常会沿调用链向上传播
     * （组合监听器内部已隔离单个监听器的失败，但实现方不应依赖该兜底）
     *
     * @param event 内容审核事件，不为 null
     */
    void onEvent(ContentSafeEvent event);
}
