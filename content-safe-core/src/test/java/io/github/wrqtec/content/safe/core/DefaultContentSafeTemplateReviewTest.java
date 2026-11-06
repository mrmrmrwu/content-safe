package io.github.wrqtec.content.safe.core;

import java.util.ArrayList;
import java.util.List;

import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.ContentSafeTemplate;
import io.github.wrqtec.content.safe.api.ReviewResolution;
import io.github.wrqtec.content.safe.api.constant.Scenes;
import io.github.wrqtec.content.safe.api.enums.AuditSource;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.enums.ReviewDecision;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.api.exception.InvalidRequestException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedFeatureException;
import io.github.wrqtec.content.safe.model.AsyncTaskRef;
import io.github.wrqtec.content.safe.model.AuditCapabilities;
import io.github.wrqtec.content.safe.model.AuditTask;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.model.SceneDefinition;
import io.github.wrqtec.content.safe.model.enums.TaskStatus;
import io.github.wrqtec.content.safe.model.event.ContentSafeEvent;
import io.github.wrqtec.content.safe.core.degrade.CircuitBreakerRegistry;
import io.github.wrqtec.content.safe.core.limiter.RateLimiterRegistry;
import io.github.wrqtec.content.safe.core.listener.CompositeAuditListener;
import io.github.wrqtec.content.safe.core.router.PriorityAuditRouter;
import io.github.wrqtec.content.safe.core.task.InMemoryAuditTaskStore;
import io.github.wrqtec.content.safe.spi.AuditListener;
import io.github.wrqtec.content.safe.spi.AuditOperation;
import io.github.wrqtec.content.safe.spi.AuditOperationProvider;
import io.github.wrqtec.content.safe.spi.AuditTaskStore;
import io.github.wrqtec.content.safe.spi.ReviewResultSink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 人审闭环：REVIEW 事件发布、决议终态归一、任务终态迁移与回写出口时序
 *
 * @author L.David
 */
class DefaultContentSafeTemplateReviewTest {

    /** 返回指定 suggestion 的假操作 */
    static class FixedSuggestionOperation implements AuditOperation {

        private final String provider;
        private final RiskSuggestion suggestion;

        FixedSuggestionOperation(String provider, RiskSuggestion suggestion) {
            this.provider = provider;
            this.suggestion = suggestion;
        }

        @Override
        public ProviderPlan plan() {
            return new ProviderPlan(provider, "p", "1", "textScan", null, null, null, null);
        }

        @Override
        public AuditCapabilities capabilities() {
            return AuditCapabilities.builder().contentTypes(ContentType.TEXT).sync(true).build();
        }

        @Override
        public int order() {
            return 0;
        }

        @Override
        public AuditResult audit(AuditRequest request) {
            return AuditResult.builder()
                    .ok(true)
                    .suggestion(suggestion)
                    .dataId(request.dataId())
                    .provider(provider)
                    .build();
        }
    }

    /** 记录回写调用的假出口 */
    static class RecordingSink implements ReviewResultSink {

        final List<ReviewResolution> resolutions = new ArrayList<>();
        RuntimeException failure;

        @Override
        public void resolve(ReviewResolution resolution) {
            resolutions.add(resolution);
            if (failure != null) {
                throw failure;
            }
        }
    }

    static AuditOperationProvider providerOf(AuditOperation op) {
        return new AuditOperationProvider() {
            @Override
            public String provider() {
                return op.plan().provider();
            }

            @Override
            public List<ContentType> supportedTypes() {
                return List.of(ContentType.TEXT);
            }

            @Override
            public List<AuditOperation> operations(SceneDefinition scene) {
                return List.of(op);
            }
        };
    }

    private List<ContentSafeEvent> events;
    private InMemoryAuditTaskStore taskStore;

    @BeforeEach
    void setUp() {
        events = new ArrayList<>();
        taskStore = new InMemoryAuditTaskStore();
    }

    private ContentSafeTemplate templateOf(ReviewResultSink sink, AuditOperationProvider provider) {
        AuditListener recording = events::add;
        return new DefaultContentSafeTemplate(new SceneRegistry(List.of()), List.of(provider),
                new PriorityAuditRouter(), new CircuitBreakerRegistry(),
                new RateLimiterRegistry(),
                new CompositeAuditListener(List.of(recording)), List.of(), taskStore, sink, null);
    }

    @Test
    void machineReviewResultPublishesReviewEvent() {
        ContentSafeTemplate template = templateOf(null,
                providerOf(new FixedSuggestionOperation("fake", RiskSuggestion.REVIEW)));

        AuditResult result = template.text(Scenes.NICKNAME, "你好");

        assertEquals(RiskSuggestion.REVIEW, result.suggestion());
        assertTrue(events.stream().anyMatch(e -> "REVIEW".equals(e.eventType())));
        assertTrue(events.stream().anyMatch(e -> "AUDIT".equals(e.eventType())));
    }

    @Test
    void resolveReviewApproveNormalizesToTerminalPass() {
        ContentSafeTemplate template = templateOf(null,
                providerOf(new FixedSuggestionOperation("fake", RiskSuggestion.REVIEW)));

        ReviewResolution resolution = ReviewResolution.of(Scenes.NICKNAME, "d-1", null,
                ReviewDecision.APPROVE, "alice", null);
        AuditResult manual = template.resolveReview(resolution);

        assertEquals(RiskSuggestion.PASS, manual.suggestion());
        assertEquals(AuditSource.MANUAL, manual.source());
        assertEquals("d-1", manual.dataId());
        assertNotNull(manual.finalResult());
        assertTrue(manual.finalResult());
    }

    @Test
    void resolveReviewRejectNormalizesToTerminalReject() {
        ContentSafeTemplate template = templateOf(null,
                providerOf(new FixedSuggestionOperation("fake", RiskSuggestion.REVIEW)));

        AuditResult manual = template.resolveReview(ReviewResolution.of(Scenes.NICKNAME, "d-2", null,
                ReviewDecision.REJECT, "bob", "确认违规"));

        assertEquals(RiskSuggestion.REJECT, manual.suggestion());
        assertTrue(manual.finalResult());
    }

    @Test
    void resolveReviewWithTaskIdMigratesTaskTerminal() {
        ContentSafeTemplate template = templateOf(null,
                providerOf(new FixedSuggestionOperation("fake", RiskSuggestion.PASS)));
        taskStore.save(AuditTask.pending("task-1", new AsyncTaskRef("p-1", "d-1"),
                Scenes.NICKNAME, "fake"));

        AuditResult manual = template.resolveReview(ReviewResolution.of(Scenes.NICKNAME, "d-1",
                "task-1", ReviewDecision.REJECT, "bob", null));

        assertEquals(TaskStatus.SUCCEEDED, taskStore.find("task-1").orElseThrow().status());
        assertEquals(manual, taskStore.find("task-1").orElseThrow().result());
        assertTrue(events.stream().anyMatch(e -> "AUDIT".equals(e.eventType())));
    }

    @Test
    void resolveReviewUnknownTaskIdThrows() {
        ContentSafeTemplate template = templateOf(null,
                providerOf(new FixedSuggestionOperation("fake", RiskSuggestion.PASS)));

        assertThrows(InvalidRequestException.class, () -> template.resolveReview(
                ReviewResolution.of(Scenes.NICKNAME, "d-1", "no-such-task",
                        ReviewDecision.APPROVE, "alice", null)));
    }

    @Test
    void resolveReviewAlreadyTerminalTaskThrows() {
        ContentSafeTemplate template = templateOf(null,
                providerOf(new FixedSuggestionOperation("fake", RiskSuggestion.REVIEW)));
        taskStore.save(AuditTask.pending("task-1", new AsyncTaskRef("p-1", "d-1"),
                Scenes.NICKNAME, "fake"));
        taskStore.markTerminal("task-1", TaskStatus.FAILED,
                AuditResult.builder().ok(false).suggestion(RiskSuggestion.ERROR).build());

        assertThrows(InvalidRequestException.class, () -> template.resolveReview(
                ReviewResolution.of(Scenes.NICKNAME, "d-1", "task-1",
                        ReviewDecision.APPROVE, "alice", null)));
    }

    @Test
    void sinkFailureLeavesTaskPendingForWholeRetry() {
        RecordingSink sink = new RecordingSink();
        sink.failure = new IllegalStateException("downstream down");
        ContentSafeTemplate template = templateOf(sink,
                providerOf(new FixedSuggestionOperation("fake", RiskSuggestion.PASS)));
        taskStore.save(AuditTask.pending("task-1", new AsyncTaskRef("p-1", "d-1"),
                Scenes.NICKNAME, "fake"));

        assertThrows(IllegalStateException.class, () -> template.resolveReview(
                ReviewResolution.of(Scenes.NICKNAME, "d-1", "task-1",
                        ReviewDecision.APPROVE, "alice", null)));

        assertEquals(TaskStatus.PENDING, taskStore.find("task-1").orElseThrow().status());
        assertEquals(1, sink.resolutions.size());
    }

    @Test
    void sinkReceivesResolutionBeforeMigration() {
        RecordingSink sink = new RecordingSink();
        ContentSafeTemplate template = templateOf(sink,
                providerOf(new FixedSuggestionOperation("fake", RiskSuggestion.PASS)));
        taskStore.save(AuditTask.pending("task-1", new AsyncTaskRef("p-1", "d-1"),
                Scenes.NICKNAME, "fake"));

        template.resolveReview(ReviewResolution.of(Scenes.NICKNAME, "d-1", "task-1",
                ReviewDecision.APPROVE, "alice", null));

        assertEquals(1, sink.resolutions.size());
        assertEquals("task-1", sink.resolutions.get(0).taskId());
        assertEquals("alice", sink.resolutions.get(0).reviewer());
    }

    @Test
    void blankReviewerAndNoteNormalizeToNull() {
        ReviewResolution resolution = ReviewResolution.of(Scenes.NICKNAME, "d-1", " ",
                ReviewDecision.APPROVE, "  ", "");

        assertNull(resolution.taskId());
        assertNull(resolution.reviewer());
        assertNull(resolution.note());
        assertNotNull(resolution.resolvedAt());
    }

    @Test
    void resolutionRequiresSceneDataIdAndDecision() {
        assertThrows(InvalidRequestException.class,
                () -> ReviewResolution.of(" ", "d-1", null, ReviewDecision.APPROVE, null, null));
        assertThrows(InvalidRequestException.class,
                () -> ReviewResolution.of(Scenes.NICKNAME, " ", null, ReviewDecision.APPROVE, null, null));
        assertThrows(InvalidRequestException.class,
                () -> ReviewResolution.of(Scenes.NICKNAME, "d-1", null, null, null, null));
    }

    @Test
    void defaultTemplateMethodThrowsUnsupported() {
        assertThrows(UnsupportedFeatureException.class,
                () -> new ContentSafeTemplate() {
                    @Override
                    public AuditResult audit(AuditRequest request) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public String submitAsync(AuditRequest request) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public AuditResult query(String taskId) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public io.github.wrqtec.content.safe.api.CallbackResult handleCallback(
                            io.github.wrqtec.content.safe.api.CallbackRequest callback) {
                        throw new UnsupportedOperationException();
                    }
                }.resolveReview(ReviewResolution.of(Scenes.NICKNAME, "d-1", null,
                        ReviewDecision.APPROVE, null, null)));
    }
}
