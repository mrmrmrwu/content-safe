package io.github.wrqtec.content.safe.starter.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.wrqtec.content.safe.api.enums.ContentType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * content.safe 前缀的配置绑定。密钥（ak/sk/callbackSecret）仅用于装配供应商客户端，
 * 任何情况下不得进入日志或异常消息。
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

        /** 同步审核的供应商 code 列表，按优先级排序 */
        private List<String> syncProviders = new ArrayList<>();

        /** 异步审核的供应商 code 列表 */
        private List<String> asyncProviders = new ArrayList<>();

        /** 场景级扩展配置，透传给供应商 */
        private Map<String, String> ext = new LinkedHashMap<>();
    }

    @Data
    public static class Defaults {

        /** 默认仲裁策略名 */
        private String arbitration = "FIRST_NON_PASS";

        /** 主供应商失败是否自动切换下一个 */
        private boolean failover = true;

        /** 熔断器默认参数 */
        private CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();

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
    public static class AsyncProperties {

        /** 异步结果轮询间隔，默认 2 秒 */
        private Duration pollInterval = Duration.ofSeconds(2);

        /** 异步结果等待超时，默认 5 分钟 */
        private Duration timeout = Duration.ofMinutes(5);
    }

    @Data
    public static class ProviderProperties {

        /** 访问密钥 ID（敏感，禁止进入日志与异常消息） */
        private String ak;

        /** 访问密钥 Secret（敏感，禁止进入日志与异常消息） */
        private String sk;

        /** 供应商服务接入地址 */
        private String endpoint;

        /** 地域 ID */
        private String regionId;

        /** 供应商级扩展配置，透传给对应 CredentialsProvider / 客户端 */
        private Map<String, String> extra = new LinkedHashMap<>();
    }
}
