package io.github.wrqtec.content.safe.core.task;

import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.model.AsyncTaskRef;
import io.github.wrqtec.content.safe.model.AuditTask;
import io.github.wrqtec.content.safe.model.enums.TaskStatus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
    void findActiveByDataIdMatchesOnlyPendingInSameScene() {
        store.save(AuditTask.pending("t1", new AsyncTaskRef("p1", "d1"), "nickname", "provider"));

        assertTrue(store.findActiveByDataId("nickname", "d1").isPresent());
        assertEquals("t1", store.findActiveByDataId("nickname", "d1").orElseThrow().taskId());
        // 场景不同不复用
        assertTrue(store.findActiveByDataId("comment", "d1").isEmpty());
    }

    @Test
    void findActiveByDataIdSkipsTerminalTasks() {
        store.save(AuditTask.pending("t1", new AsyncTaskRef("p1", "d1"), "nickname", "provider"));
        store.markTerminal("t1", TaskStatus.SUCCEEDED, AuditResult.builder().ok(true).build());

        // 已终态任务不参与幂等复用：业务重新提交应创建新任务
        assertTrue(store.findActiveByDataId("nickname", "d1").isEmpty());
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

    @Test
    void deleteRemovesZombieFromInsertionOrder() {
        store.save(AuditTask.pending("t1", new AsyncTaskRef("p1", "d1"), "scene", "provider"));
        store.save(AuditTask.pending("t2", new AsyncTaskRef("p2", "d2"), "scene", "provider"));

        store.delete("t1");

        // 删除必须同步清理插入顺序索引，否则僵尸 taskId 在队列中只增不减（慢泄漏）
        assertEquals(1, insertionOrderSize(store));
    }

    @Test
    void lazyExpiryRemovesZombieFromInsertionOrder() throws InterruptedException {
        InMemoryAuditTaskStore ttlStore = new InMemoryAuditTaskStore(10, Duration.ofMillis(50));
        ttlStore.save(AuditTask.pending("t1", new AsyncTaskRef("p1", "d1"), "scene", "provider"));
        ttlStore.save(AuditTask.pending("t2", new AsyncTaskRef("p2", "d2"), "scene", "provider"));

        Thread.sleep(80);
        assertTrue(ttlStore.find("t1").isEmpty());

        assertEquals(1, insertionOrderSize(ttlStore));
    }

    /** 反射读私有插入顺序队列长度，断言无僵尸条目 */
    private static int insertionOrderSize(InMemoryAuditTaskStore target) {
        try {
            Field field = InMemoryAuditTaskStore.class.getDeclaredField("insertionOrder");
            field.setAccessible(true);
            return ((Deque<?>) field.get(target)).size();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void concurrentMarkTerminalMigratesExactlyOnce() throws Exception {
        InMemoryAuditTaskStore store = new InMemoryAuditTaskStore();
        store.save(AuditTask.pending("t1", new AsyncTaskRef("p-1", "d-1"), "nickname", "fake"));
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        List<Future<Boolean>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    barrier.await();
                    return store.markTerminal("t1", TaskStatus.SUCCEEDED, null);
                }));
            }
            long migrated = futures.stream().filter(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    return false;
                }
            }).count();
            // CAS 契约：并发竞争同一任务时仅首个迁移成功
            assertEquals(1, migrated);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(TaskStatus.SUCCEEDED, store.find("t1").orElseThrow().status());
    }
}
