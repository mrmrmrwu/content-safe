package io.github.wrqtec.content.safe.core.task;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;

import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.util.Asserts;
import io.github.wrqtec.content.safe.model.AuditTask;
import io.github.wrqtec.content.safe.model.enums.TaskStatus;
import io.github.wrqtec.content.safe.spi.AuditTaskStore;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认内存任务存储，生产环境应替换为 DB/Redis 实现（对外 Bean 默认可被覆盖）。
 * 有界：容量超限时按插入顺序驱逐最旧任务；TTL 惰性过期（读取/迁移时淘汰），
 * 防止回调丢失的 PENDING 任务无限累积占满内存。
 * 任务从 store 移除的所有路径（delete/惰性过期/容量驱逐）都会同步清理
 * {@code insertionOrder} 索引，避免僵尸 taskId 在队列中累积
 *
 * @author L.David
 */
@Slf4j
public class InMemoryAuditTaskStore implements AuditTaskStore {

    /** 默认容量上限 */
    public static final int DEFAULT_MAX_SIZE = 10_000;

    /** 默认过期时长 */
    public static final Duration DEFAULT_EXPIRE_AFTER = Duration.ofHours(1);

    private final ConcurrentMap<String, AuditTask> store = new ConcurrentHashMap<>();

    /** 插入顺序索引，用于容量超限时驱逐最旧任务 */
    private final ConcurrentLinkedDeque<String> insertionOrder = new ConcurrentLinkedDeque<>();

    private final int maxSize;

    private final Duration expireAfter;

    public InMemoryAuditTaskStore() {
        this(DEFAULT_MAX_SIZE, DEFAULT_EXPIRE_AFTER);
    }

    /**
     * @param maxSize     容量上限，必须为正数
     * @param expireAfter 任务过期时长（自创建起），必须为正数
     */
    public InMemoryAuditTaskStore(int maxSize, Duration expireAfter) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive: " + maxSize);
        }
        if (expireAfter == null || expireAfter.isNegative() || expireAfter.isZero()) {
            throw new IllegalArgumentException("expireAfter must be positive: " + expireAfter);
        }
        this.maxSize = maxSize;
        this.expireAfter = expireAfter;
    }

    @Override
    public void save(AuditTask task) {
        if (store.size() >= maxSize) {
            // 容量吃紧才做全量过期清理，常规路径 O(1)
            evictExpired();
            while (store.size() >= maxSize) {
                if (!evictOldest()) {
                    break;
                }
            }
        }
        if (store.put(task.taskId(), task) == null) {
            insertionOrder.addLast(task.taskId());
        }
    }

    @Override
    public Optional<AuditTask> find(String taskId) {
        return Optional.ofNullable(live(taskId));
    }

    @Override
    public Optional<AuditTask> findByRef(String providerTaskId, String dataId) {
        boolean byRef = providerTaskId != null && !providerTaskId.isBlank();
        boolean byDataId = dataId != null && !dataId.isBlank();
        if (!byRef && !byDataId) {
            // 双空反查无意义，避免随机命中任意任务
            return Optional.empty();
        }
        return store.values().stream()
                .filter(this::notExpired)
                .filter(t -> !byRef || t.ref().providerTaskId().equals(providerTaskId))
                .filter(t -> !byDataId || dataId.equals(t.ref().dataId()))
                .findFirst();
    }

    @Override
    public Optional<AuditTask> findActiveByDataId(String sceneCode, String dataId) {
        return store.values().stream()
                .filter(this::notExpired)
                // 只复用 PENDING 任务：已终态说明内容已审完，业务重新提交应创建新任务
                .filter(t -> t.status() == TaskStatus.PENDING)
                .filter(t -> t.scene().equals(sceneCode))
                .filter(t -> dataId.equals(t.ref().dataId()))
                .findFirst();
    }

    @Override
    public boolean markTerminal(String taskId, TaskStatus status, AuditResult result) {
        while (true) {
            AuditTask current = live(taskId);
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
        if (store.remove(taskId) != null) {
            insertionOrder.remove(taskId);
        }
    }

    /** 读取仍存活（存在且未过期）的任务；过期任务顺带淘汰 */
    private AuditTask live(String taskId) {
        AuditTask task = store.get(taskId);
        if (task == null) {
            return null;
        }
        return notExpired(task) ? task : null;
    }

    /** 过期判断 + 惰性淘汰；已过期返回 false 并移除 */
    private boolean notExpired(AuditTask task) {
        if (task.createdAt().plus(expireAfter).isAfter(Instant.now())) {
            return true;
        }
        if (store.remove(task.taskId(), task)) {
            insertionOrder.remove(task.taskId());
            log.debug("audit task expired: taskId={}, createdAt={}", task.taskId(), task.createdAt());
        }
        return false;
    }

    /** 淘汰全部过期任务，释放容量 */
    private void evictExpired() {
        Instant now = Instant.now();
        List<String> expiredIds = new ArrayList<>();
        for (AuditTask task : store.values()) {
            if (!task.createdAt().plus(expireAfter).isAfter(now)) {
                expiredIds.add(task.taskId());
            }
        }
        for (String id : expiredIds) {
            if (store.remove(id) != null) {
                insertionOrder.remove(id);
                log.debug("audit task expired: taskId={}", id);
            }
        }
    }

    /** 按插入顺序驱逐最旧任务；队列耗尽（并发竞态）返回 false */
    private boolean evictOldest() {
        Iterator<String> iterator = insertionOrder.iterator();
        while (iterator.hasNext()) {
            String oldest = iterator.next();
            if (store.remove(oldest) != null) {
                iterator.remove();
                return true;
            }
            iterator.remove();
        }
        return false;
    }
}
