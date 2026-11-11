package io.github.wrqtec.content.safe.samples.videoasync;

import java.util.Optional;

import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.ContentRef;
import io.github.wrqtec.content.safe.api.ContentSafeTemplate;
import io.github.wrqtec.content.safe.api.constant.Scenes;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.model.AuditTask;
import io.github.wrqtec.content.safe.model.enums.TaskStatus;
import io.github.wrqtec.content.safe.samples.videoasync.listener.AuditEventRecorder;
import io.github.wrqtec.content.safe.spi.AuditTaskStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 视频异步审核全流程验证：提交 → 处理中 → 终态（事件发布）
 *
 * @author L.David
 */
@SpringBootTest
class VideoAsyncSampleTest {

    @Autowired
    private ContentSafeTemplate template;

    @Autowired
    private AuditTaskStore taskStore;

    @Autowired
    private AuditEventRecorder recorder;

    @Test
    void pollReachesTerminalWithHits() {
        String taskId = template.submitAsync(videoRequest("sample-poll-1"));

        AuditResult pending = template.query(taskId);
        assertThat(pending.finalResult()).isFalse();
        assertThat(pending.suggestion()).isNull();

        AuditResult terminal = template.query(taskId);
        assertThat(terminal.finalResult()).isTrue();
        assertThat(terminal.suggestion()).isEqualTo(RiskSuggestion.REJECT);
        assertThat(terminal.hits()).hasSize(2);

        Optional<AuditTask> task = taskStore.find(taskId);
        assertThat(task).isPresent();
        assertThat(task.get().status()).isEqualTo(TaskStatus.SUCCEEDED);
    }

    @Test
    void convenienceVideoMethodSubmitsPendingTask() {
        String taskId = template.video(Scenes.SHORT_VIDEO, "https://example.com/videos/sample-2.mp4");
        assertThat(taskId).isNotBlank();

        Optional<AuditTask> task = taskStore.find(taskId);
        assertThat(task).isPresent();
        assertThat(task.get().status()).isEqualTo(TaskStatus.PENDING);
    }

    @Test
    void eventsPublishedOnTerminalQuery() {
        String taskId = template.submitAsync(videoRequest("sample-events-1"));
        template.query(taskId); // 第 1 次：处理中
        AuditResult terminal = template.query(taskId); // 第 2 次：终态
        assertThat(terminal.finalResult()).isTrue();

        assertThat(recorder.auditEvents())
                .anySatisfy(event -> {
                    assertThat(event.scene()).isEqualTo(Scenes.SHORT_VIDEO);
                    assertThat(event.result().suggestion()).isEqualTo(RiskSuggestion.REJECT);
                });
    }

    private AuditRequest videoRequest(String dataId) {
        return AuditRequest.builder()
                .scene(Scenes.SHORT_VIDEO)
                .type(ContentType.VIDEO)
                .content(ContentRef.url("https://example.com/videos/sample.mp4"))
                .dataId(dataId)
                .build();
    }
}
