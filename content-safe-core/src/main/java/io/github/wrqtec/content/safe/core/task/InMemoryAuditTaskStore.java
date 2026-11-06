package io.github.wrqtec.content.safe.core.task;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.model.AuditTask;
import io.github.wrqtec.content.safe.model.enums.TaskStatus;
import io.github.wrqtec.content.safe.spi.AuditTaskStore;

/**
 * 默认内存任务存储。生产环境应替换为 DB/Redis 实现（对外 Bean 默认可被覆盖）。
 *
 * @author L.David
 */
public class InMemoryAuditTaskStore implements AuditTaskStore {

    private final ConcurrentMap<String, AuditTask> store = new ConcurrentHashMap<>();

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
        while (true) {
            AuditTask current = store.get(taskId);
            if (current == null) {
                return false;
            }
            AuditTask next = current.withTerminal(status, result);
            if (next == null) {
                // 已终态：重复回调/查询幂等放行
                return false;
            }
            if (store.replace(taskId, current, next)) {
                return true;
            }
        }
    }

    @Override
    public void delete(String taskId) {
        store.remove(taskId);
    }
}
