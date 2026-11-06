package io.github.wrqtec.content.safe.spi;

import io.github.wrqtec.content.safe.model.event.ContentSafeEvent;

/**
 * 事件监听。core 在审核完成/风险命中/复审/降级/死信时发布。
 * 实现方处理异步落库、告警、统计等。
 *
 * @author L.David
 */
public interface AuditListener {

    /**
     * 消费事件。必须同步返回，异步处理请自行入队。
     *
     * @param event 内容审核事件，不为 null
     */
    void onEvent(ContentSafeEvent event);
}
