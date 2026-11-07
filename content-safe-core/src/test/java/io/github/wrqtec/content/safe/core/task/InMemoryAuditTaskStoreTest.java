package io.github.wrqtec.content.safe.core.task;

import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.model.AsyncTaskRef;
import io.github.wrqtec.content.safe.model.AuditTask;
import io.github.wrqtec.content.safe.model.enums.TaskStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryAuditTaskStoreTest {

    private final InMemoryAuditTaskStore store = new InMemoryAuditTaskStore();

    @Test
    void saveFindByRefAndTerminal() {
        store.save(AuditTask.pending("t1", new AsyncTaskRef("p1", "d1"), "scene", "provider"));

        assertTrue(store.find("t1").isPresent());
        assertTrue(store.findByRef("p1", "d1").isPresent());
        assertTrue(store.findByRef("p1", null).isPresent());
        assertFalse(store.findByRef("other", null).isPresent());

        AuditResult result = AuditResult.builder().ok(true).suggestion(RiskSuggestion.PASS).build();
        assertTrue(store.markTerminal("t1", TaskStatus.SUCCEEDED, result));
        assertFalse(store.markTerminal("t1", TaskStatus.FAILED, null));
        assertEquals(TaskStatus.SUCCEEDED, store.find("t1").orElseThrow().status());
        assertEquals(1L, store.find("t1").orElseThrow().version());

        store.delete("t1");
        assertTrue(store.find("t1").isEmpty());
    }

    @Test
    void markTerminalOnMissingTaskReturnsFalse() {
        assertFalse(store.markTerminal("nope", TaskStatus.SUCCEEDED, null));
    }

    @Test
    void findByRefFallsBackToDataIdWhenTaskIdBlank() {
        // 部分供应商回调不回显任务 ID（如腾讯图片异步），需按 dataId 反查
        store.save(AuditTask.pending("t1", new AsyncTaskRef("p1", "d1"), "scene", "provider"));

        assertTrue(store.findByRef(null, "d1").isPresent());
        assertTrue(store.findByRef("  ", "d1").isPresent());
        assertTrue(store.findByRef(null, "other").isEmpty());
        assertEquals("t1", store.findByRef(null, "d1").orElseThrow().taskId());
    }

    @Test
    void findByRefWithBothBlankReturnsEmpty() {
        store.save(AuditTask.pending("t1", new AsyncTaskRef("p1", "d1"), "scene", "provider"));

        assertTrue(store.findByRef(null, null).isEmpty());
        assertTrue(store.findByRef("", " ").isEmpty());
    }

    @Test
    void boundedStoreEvictsOldestWhenCapacityReached() {
        InMemoryAuditTaskStore bounded = new InMemoryAuditTaskStore(2, Duration.ofHours(1));
        bounded.save(AuditTask.pending("t1", new AsyncTaskRef("p1", "d1"), "scene", "provider"));
        bounded.save(AuditTask.pending("t2", new AsyncTaskRef("p2", "d2"), "scene", "provider"));

        // 超出容量时逐出最早写入的任务，容量保持有界
        bounded.save(AuditTask.pending("t3", new AsyncTaskRef("p3", "d3"), "scene", "provider"));

        assertTrue(bounded.find("t1").isEmpty());
        assertTrue(bounded.find("t2").isPresent());
        assertTrue(bounded.find("t3").isPresent());
    }

    @Test
    void boundedStoreEvictsExpiredTasks() throws InterruptedException {
        InMemoryAuditTaskStore bounded = new InMemoryAuditTaskStore(10, Duration.ofMillis(50));
        bounded.save(AuditTask.pending("t1", new AsyncTaskRef("p1", "d1"), "scene", "provider"));

        Thread.sleep(80);

        assertTrue(bounded.find("t1").isEmpty());
    }

    @Test
    void invalidConstructorArgumentsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new InMemoryAuditTaskStore(0, Duration.ofHours(1)));
        assertThrows(IllegalArgumentException.class, () -> new InMemoryAuditTaskStore(10, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new InMemoryAuditTaskStore(10, null));
    }
}
