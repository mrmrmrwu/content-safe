package io.github.wrqtec.content.safe.starter.config;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import io.github.wrqtec.content.safe.api.util.Asserts;
import io.github.wrqtec.content.safe.spi.AuditArbitrationPolicy;

/**
 * 仲裁策略注册表：按场景解析生效的仲裁策略
 * 优先级：scenes.&lt;code&gt;.defaultArbitration &gt; defaults.arbitration
 * 构造时即校验所有配置的策略名可解析，非法配置启动期抛出 {@link ConfigInvalidException}
 *
 * @author L.David
 */
public class ArbitrationPolicyRegistry {

    /** 关闭仲裁的策略名（大小写不敏感）：全部场景走主备 failover */
    public static final String OFF = "OFF";

    /** 关闭仲裁的策略别名，与 {@link #OFF} 等价 */
    public static final String NONE = "NONE";

    private final Map<String, AuditArbitrationPolicy> byName = new HashMap<>();

    private final ContentSafeProperties properties;

    public ArbitrationPolicyRegistry(List<AuditArbitrationPolicy> policies, ContentSafeProperties properties) {
        Asserts.notNull(policies, "policies");
        this.properties = Asserts.notNull(properties, "properties");
        for (AuditArbitrationPolicy policy : policies) {
            byName.put(normalize(policy.name()), policy);
        }
        validate();
    }

    /**
     * 解析指定场景生效的仲裁策略
     *
     * @return 生效策略；返回 null 表示该场景不仲裁（主备 failover）
     */
    public AuditArbitrationPolicy forScene(String scene) {
        return resolve(policyNameFor(scene));
    }

    /** 解析指定场景生效的仲裁策略名（含覆盖规则） */
    public String policyNameFor(String scene) {
        ContentSafeProperties.SceneProperties sceneProps = properties.getScenes().get(scene);
        String override = sceneProps == null ? null : sceneProps.getDefaultArbitration();
        if (hasText(override)) {
            return override;
        }
        return properties.getDefaults().getArbitration();
    }

    private void validate() {
        resolve(properties.getDefaults().getArbitration());
        properties.getScenes().forEach((code, sceneProps) -> {
            if (hasText(sceneProps.getDefaultArbitration())) {
                resolve(sceneProps.getDefaultArbitration());
            }
        });
    }

    /**
     * 按名解析策略；空白或 OFF/NONE 返回 null（表示不仲裁），
     * 其余未知名称启动期抛 {@link ConfigInvalidException}
     */
    private AuditArbitrationPolicy resolve(String name) {
        if (!hasText(name)) {
            return null;
        }
        String normalized = normalize(name);
        if (OFF.equals(normalized) || NONE.equals(normalized)) {
            return null;
        }
        AuditArbitrationPolicy policy = byName.get(normalized);
        if (policy == null) {
            throw new ConfigInvalidException("unknown arbitration policy: " + name
                    + " (available: " + byName.keySet() + ")");
        }
        return policy;
    }

    /** 归一化策略名：大小写不敏感，连字符/空格视为下划线 */
    static String normalize(String name) {
        Asserts.notBlank(name, "arbitration policy name");
        return name.replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
