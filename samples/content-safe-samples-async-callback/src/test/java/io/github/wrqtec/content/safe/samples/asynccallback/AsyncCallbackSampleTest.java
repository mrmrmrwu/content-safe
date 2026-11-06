package io.github.wrqtec.content.safe.samples.asynccallback;

import java.util.Map;

import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.CallbackRequest;
import io.github.wrqtec.content.safe.api.CallbackResult;
import io.github.wrqtec.content.safe.api.ContentRef;
import io.github.wrqtec.content.safe.api.ContentSafeTemplate;
import io.github.wrqtec.content.safe.api.constant.Scenes;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.api.exception.CallbackVerifyException;
import io.github.wrqtec.content.safe.model.AuditTask;
import io.github.wrqtec.content.safe.model.enums.TaskStatus;
import io.github.wrqtec.content.safe.samples.asynccallback.callback.FakeCallbackVerifier;
import io.github.wrqtec.content.safe.samples.asynccallback.listener.AuditEventRecorder;
import io.github.wrqtec.content.safe.spi.AuditTaskStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AsyncCallbackSampleTest {

    @Autowired
    private ContentSafeTemplate template;

    @Autowired
    private AuditTaskStore taskStore;

    @Autowired
    private AuditEventRecorder recorder;

    @Test
    void fullAsyncCallbackFlow() {
        String dataId = "avatar-flow";
        String taskId = template.submitAsync(imageRequest(dataId));
        assertThat(taskStore.find(taskId)).get()
                .extracting(AuditTask::status).isEqualTo(TaskStatus.PENDING);

        AuditResult pending = template.query(taskId);
        assertThat(pending.finalResult()).isFalse();
        assertThat(taskStore.find(taskId)).get()
                .extracting(AuditTask::status).isEqualTo(TaskStatus.PENDING);

        CallbackResult accepted = template.handleCallback(validCallback(dataId));
        assertThat(accepted.accepted()).isTrue();
        assertThat(taskStore.find(taskId)).get()
                .extracting(AuditTask::status).isEqualTo(TaskStatus.SUCCEEDED);

        AuditResult terminal = template.query(taskId);
        assertThat(terminal.finalResult()).isTrue();
        assertThat(terminal.suggestion()).isEqualTo(RiskSuggestion.PASS);
    }

    @Test
    void duplicateCallbackIsIdempotent() {
        String dataId = "avatar-dup";
        String taskId = template.submitAsync(imageRequest(dataId));
        template.handleCallback(validCallback(dataId));
        int eventsAfterFirst = recorder.auditEvents().size();

        CallbackResult second = template.handleCallback(validCallback(dataId));
        assertThat(second.accepted()).isTrue();
        assertThat(recorder.auditEvents()).hasSize(eventsAfterFirst);
        assertThat(taskStore.find(taskId)).get()
                .extracting(AuditTask::status).isEqualTo(TaskStatus.SUCCEEDED);
    }

    @Test
    void verifyFailureGoesDeadLetter() {
        String dataId = "avatar-forged";
        template.submitAsync(imageRequest(dataId));
        int deadLettersBefore = recorder.deadLetterEvents().size();

        CallbackRequest forged = new CallbackRequest("fake-async",
                Map.of(FakeCallbackVerifier.TOKEN_HEADER, "wrong-secret"),
                "taskId=fake-task-" + dataId + "&suggestion=PASS");
        assertThatThrownBy(() -> template.handleCallback(forged))
                .isInstanceOf(CallbackVerifyException.class);
        assertThat(recorder.deadLetterEvents()).hasSize(deadLettersBefore + 1);
        assertThat(recorder.deadLetterEvents().get(deadLettersBefore).provider())
                .isEqualTo("fake-async");
    }

    private static AuditRequest imageRequest(String dataId) {
        return AuditRequest.builder()
                .scene(Scenes.AVATAR)
                .type(ContentType.IMAGE)
                .content(ContentRef.url("https://example.com/avatar/1.jpg"))
                .dataId(dataId)
                .build();
    }

    private static CallbackRequest validCallback(String dataId) {
        return new CallbackRequest("fake-async",
                Map.of(FakeCallbackVerifier.TOKEN_HEADER, "demo-callback-secret"),
                "taskId=fake-task-" + dataId + "&suggestion=PASS");
    }
}
