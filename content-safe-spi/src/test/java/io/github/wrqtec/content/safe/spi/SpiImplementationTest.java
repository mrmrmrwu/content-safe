package io.github.wrqtec.content.safe.spi;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.CallbackRequest;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.api.exception.UnsupportedFeatureException;
import io.github.wrqtec.content.safe.model.AsyncTaskRef;
import io.github.wrqtec.content.safe.model.AuditCapabilities;
import io.github.wrqtec.content.safe.model.AuditTask;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.model.SceneDefinition;
import io.github.wrqtec.content.safe.model.enums.TaskStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用一个最小"自定义 provider"验证 SPI 可完整实现
 *
 * @author L.David
 */
class SpiImplementationTest {

    /** 最小自定义操作实现 */
    static class FakeOperation implements AuditOperation {

        private final ProviderPlan plan = new ProviderPlan("fake", "p", "1", "textScan", null, null, null, null);
        private final AuditCapabilities caps = AuditCapabilities.builder()
                .contentTypes(ContentType.TEXT)
                .sync(true)
                .build();

        @Override
        public ProviderPlan plan() {
            return plan;
        }

        @Override
        public AuditCapabilities capabilities() {
            return caps;
        }

        @Override
        public AuditResult audit(AuditRequest request) {
            return AuditResult.builder()
                    .dataId(request.dataId())
                    .suggestion(RiskSuggestion.PASS)
                    .build();
        }
    }

    /** 最小自定义 provider 实现 */
    static class FakeProvider implements AuditOperationProvider {

        @Override
        public String provider() {
            return "fake";
        }

        @Override
        public List<ContentType> supportedTypes() {
            return List.of(ContentType.TEXT);
        }

        @Override
        public List<AuditOperation> operations(SceneDefinition scene) {
            return List.of(new FakeOperation());
        }
    }

    /** 最小任务存储实现（内存版雏形，验证 SPI 可实现） */
    static class InMemoryTaskStore implements AuditTaskStore {

        private final Map<String, AuditTask> store = new ConcurrentHashMap<>();

        @Override
        public void save(AuditTask task) {
            store.put(task.taskId(), task);
        }

        @Override
        public Optional<AuditTask> find(String taskId) {
            return Optional.ofNullable(store.get(taskId));
        }

        @Override
        public Optional<AuditTask> findByRef(String providerTaskId, String dataId) {
            return store.values().stream()
                    .filter(t -> t.ref().providerTaskId().equals(providerTaskId))
                    .filter(t -> dataId == null || dataId.equals(t.ref().dataId()))
                    .findFirst();
        }

        @Override
        public boolean markTerminal(String taskId, TaskStatus status, AuditResult result) {
            AuditTask current = store.get(taskId);
            if (current == null) {
                return false;
            }
            AuditTask next = current.withTerminal(status, result);
            if (next == null) {
                return false;
            }
            return store.replace(taskId, current, next);
        }

        @Override
        public void delete(String taskId) {
            store.remove(taskId);
        }
    }

    @Test
    void customProviderCompilesAndSupports() {
        FakeProvider provider = new FakeProvider();
        SceneDefinition scene = SceneDefinition.of("nickname", ContentType.TEXT, null);
        AuditRequest request = AuditRequest.text("nickname", "你好");

        assertTrue(provider.supports(request, scene));
        assertEquals("fake", provider.provider());
    }

    @Test
    void defaultAsyncMethodsThrowUnsupportedFeature() {
        FakeOperation op = new FakeOperation();
        assertThrows(UnsupportedFeatureException.class, () -> op.submitAsync(AuditRequest.text("s", "t")));
        assertThrows(UnsupportedFeatureException.class,
                () -> op.query(new AsyncTaskRef("p", "d")));
        assertThrows(UnsupportedFeatureException.class, () -> op.batchAudit(List.of()));
        assertEquals("not supported", op.handleCallback(new CallbackRequest("p", Map.of(), "")).message());
        assertFalse(op.handleCallback(new CallbackRequest("p", Map.of(), "")).accepted());
    }

    @Test
    void taskStoreTerminalIsIdempotent() {
        InMemoryTaskStore store = new InMemoryTaskStore();
        AuditTask task = AuditTask.pending("t1", new AsyncTaskRef("p1", "d1"), "s", "fake");
        store.save(task);

        assertTrue(store.markTerminal("t1", TaskStatus.SUCCEEDED, null));
        assertFalse(store.markTerminal("t1", TaskStatus.FAILED, null));
        assertEquals(TaskStatus.SUCCEEDED, store.find("t1").orElseThrow().status());
    }
}
