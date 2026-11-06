package io.github.wrqtec.content.safe.model;

import java.time.Instant;

import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.exception.InvalidRequestException;
import io.github.wrqtec.content.safe.model.enums.TaskStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link AuditTask} 单元测试。
 *
 * @author L.David
 */
class AuditTaskTest {

    @Test
    void pendingThenTerminal() {
        AuditTask task = AuditTask.pending("t1", new AsyncTaskRef("p1", "d1"), "scene", "provider");
        assertEquals(TaskStatus.PENDING, task.status());
        assertEquals(0L, task.version());

        AuditTask done = task.withTerminal(TaskStatus.SUCCEEDED, AuditResult.builder().dataId("d1").build());
        assertNotNull(done);
        assertEquals(TaskStatus.SUCCEEDED, done.status());
        assertEquals(1L, done.version());

        assertNull(done.withTerminal(TaskStatus.FAILED, null));
    }

    @Test
    void invalidStateRejected() {
        assertThrows(InvalidRequestException.class,
                () -> new AuditTask(null, new AsyncTaskRef("p", "d"), null, null, null, null, 0L, null, null));
    }

    @Test
    void timestampsDefaultToNow() {
        AuditTask task = AuditTask.pending("t1", new AsyncTaskRef("p1", "d1"), "scene", "provider");
        assertNotNull(task.createdAt());
        assertNotNull(task.updatedAt());
    }

    @Test
    void timestampsPreservedOnCopy() {
        Instant created = Instant.now().minusSeconds(60);
        AuditTask task = new AuditTask("t1", new AsyncTaskRef("p", "d"), "s", "p",
                TaskStatus.PENDING, null, 0L, created, created);
        assertEquals(created, task.createdAt());
    }
}
