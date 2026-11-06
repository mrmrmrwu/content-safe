package io.github.wrqtec.content.safe.samples.basic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.github.wrqtec.content.safe.api.ReviewResolution;
import io.github.wrqtec.content.safe.spi.ReviewResultSink;

/**
 * 人审终态回写出口演示：starter 按 0..1 装配，resolveReview 先回写后迁移任务终态
 * 真实业务中在此持久化复核台账/通知下游
 *
 * @author L.David
 */
@Component
public class LoggingReviewResultSink implements ReviewResultSink {

    private static final Logger log = LoggerFactory.getLogger(LoggingReviewResultSink.class);

    @Override
    public void resolve(ReviewResolution resolution) {
        log.info("review sink resolved -> scene={}, dataId={}, taskId={}, decision={}, reviewer={}",
                resolution.scene(), resolution.dataId(), resolution.taskId(),
                resolution.decision(), resolution.reviewer());
    }
}
