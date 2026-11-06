package io.github.wrqtec.content.safe.core.task;

import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;
import io.github.wrqtec.content.safe.model.AsyncTaskRef;
import io.github.wrqtec.content.safe.model.AuditTask;
import io.github.wrqtec.content.safe.model.enums.TaskStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
