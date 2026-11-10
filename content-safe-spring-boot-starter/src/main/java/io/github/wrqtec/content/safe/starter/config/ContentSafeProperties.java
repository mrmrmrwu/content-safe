package io.github.wrqtec.content.safe.starter.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.wrqtec.content.safe.api.enums.ContentType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * content.safe 前缀的配置属性绑定：密钥（ak/sk/callbackSecret）仅用于装配供应商客户端，
 * 任何情况下不得进入日志或异常消息
 *
 * @author L.David
 */
@Data
@ConfigurationProperties(prefix = ContentSafeProperties.PREFIX)
public class ContentSafeProperties {

    /** 配置前缀（用户契约，勿改） */
    public static final String PREFIX = "content.safe";

    /** 配置 schema 版本，当前仅支持 1 */
    private int schemaVersion = 1;

    /** 场景配置，key 为场景 code */
    private Map<String, SceneProperties> scenes = new LinkedHashMap<>();

    /** 全局默认 */
    private Defaults defaults = new Defaults();

    /** 回调验签密钥（可选；为空时由各 CallbackVerifier 自行决定校验策略） */
    private String callbackSecret;

    /** 内置回调接收端点配置 */
    private CallbackProperties callback = new CallbackProperties();

    /** 供应商配置，key 为供应商 code（如 aliyun） */
    private Map<String, ProviderProperties> providers = new LinkedHashMap<>();

    @Data
    public static class SceneProperties {

        /** 内容类型，v1 仅支持 TEXT / IMAGE */
        private ContentType type;

        /** 场景描述（仅文档用途，可为空） */
        private String description;

        /** 默认风险等级（可选，如 PASS / REVIEW / REJECT） */
        private String defaultSeverity;

        /** 仲裁策略名；为空时使用 defaults.arbitration */
        private String defaultArbitration;

        /** 场景级扩展配置，透传给供应商 */
        private Map<String, String> ext = new LinkedHashMap<>();
    }

    @Data
    public static class Defaults {

        /** 默认仲裁策略名：OFF（主备 failover，成本最优）或已注册策略名（如 FIRST_NON_PASS） */
        private String arbitration = "OFF";

        /** 主供应商失败是否自动切换下一个 */
        private boolean failover = true;

        /** 熔断器默认参数 */
        private CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();

        /** 限流器默认参数 */
        private RateLimiterProperties rateLimiter = new RateLimiterProperties();

        /** 异步审核默认参数 */
        private AsyncProperties async = new AsyncProperties();
    }

    @Data
    public static class CircuitBreakerProperties {

        /** 滑动窗口大小（请求数），默认 20 */
        private int windowSize = 20;

        /** 窗口内失败率阈值（0~1），达到即熔断，默认 0.5 */
        private double failureRateThreshold = 0.5D;

        /** 窗口内最小请求数，不足则不触发熔断，默认 10 */
        private int minCalls = 10;

        /** 熔断 OPEN 状态保持时长，默认 30 秒 */
        private Duration openDuration = Duration.ofSeconds(30);

        /** 半开状态允许的最大探测请求数，默认 3 */
        private int halfOpenMaxCalls = 3;
    }

    @Data
    public static class RateLimiterProperties {

        /** 是否启用本地限流（默认 true；关闭后全部放行） */
        private boolean enabled = true;

        /** 全局 QPS 覆盖；为空时使用 plan 声明额度 */
        private Double qps;

        /** 全局并发上限覆盖；为空时使用 plan 声明额度 */
        private Integer maxConcurrency;

        /** 按 planKey（provider:operation）的额度覆盖，优先级最高 */
        private Map<String, Limit> overrides = new LinkedHashMap<>();
    }

    @Data
    public static class Limit {

        /** QPS 上限；为空表示不限 */
        private Double qps;

        /** 并发上限；为空表示不限 */
        private Integer maxConcurrency;
    }

    @Data
    public static class AsyncProperties {

        /** 异步结果轮询间隔（submitAndWait 默认值），默认 2 秒 */
        private Duration pollInterval = Duration.ofSeconds(2);

        /** 异步结果等待超时（submitAndWait 默认值），默认 5 分钟 */
        private Duration timeout = Duration.ofMinutes(5);

        /** 内存任务存储容量上限（仅默认 InMemoryAuditTaskStore 生效），默认 10000 */
        private int maxTasks = 10_000;

        /** 内存任务过期时长（仅默认 InMemoryAuditTaskStore 生效），默认 1 小时 */
        private Duration expireAfter = Duration.ofHours(1);
    }

    @Data
    public static class CallbackProperties {

        /** 是否由 starter 内置回调接收端点（默认 false，需显式开启；需 Servlet Web 环境） */
        private boolean expose = false;

        /** 内置回调端点路径前缀，路径变量为供应商 code */
        private String path = "/content-safe/callback";

        /**
         * 每 provider 回调处理速率上限（令牌桶 QPS），防截获重放/重推风暴下的解析成本放大，
         * 默认 100；&le;0 或 null 表示不限流。限流拒绝返回非 2xx 且不发死信事件
         */
        private Double rateLimit = 100.0D;
    }

    @Data
    public static class ProviderProperties {

        /** 是否启用该供应商（默认 false，显式开启才装配） */
        private boolean enabled;

        /** 访问密钥 ID（敏感，禁止进入日志与异常消息） */
        private String ak;

        /** 访问密钥 Secret（敏感，禁止进入日志与异常消息） */
        private String sk;

        /** 供应商服务接入地址 */
        private String endpoint;

        /** 地域 ID */
        private String regionId;

        /** 业务场景 code → 供应商场景/服务标识的映射（如 nickname → nickname_detection） */
        private Map<String, String> scenes = new LinkedHashMap<>();

        /** 供应商级扩展配置，透传给对应 CredentialsProvider / 客户端 */
        private Map<String, String> extra = new LinkedHashMap<>();
    }
}
