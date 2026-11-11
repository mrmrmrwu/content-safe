package io.github.wrqtec.content.safe.api;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.github.wrqtec.content.safe.api.enums.AuditSource;
import io.github.wrqtec.content.safe.api.enums.RiskSuggestion;

/**
 * 统一审核结果，不可变
 *
 * <p>{@code ok} 仅表示"审核流程成功执行"，业务放行与否看 {@code suggestion}；
 * {@code suggestion == ERROR} 时不得当作 PASS 处理</p>
 *
 * <p><b>{@code suggestion} 可空契约</b>：异步任务的中间态结果（{@code finalResult == false}，
 * 见 {@code query}）{@code suggestion} 为 {@code null}；消费方必须判空，或以
 * {@code finalResult} 门控后再取用（终态恒非 null）。直接对中间态结果
 * {@code switch}/解引用 {@code suggestion} 会 NPE</p>
 *
 * @param ok          审核流程是否成功执行（与内容是否合规无关）
 * @param suggestion  归一化审核建议（异步中间态为 null，终态恒非 null）
 * @param finalResult 是否为终态（异步任务中间态为 false）
 * @param source      结果来源
 * @param hits        命中详情，未命中为空列表
 * @param requestId   供应商侧请求 ID，用于对账排查
 * @param dataId      业务侧数据唯一标识
 * @param provider    实际执行审核的供应商
 * @param product     供应商产品线
 * @param service     供应商 service/场景 ID（归一化前的配置值）
 * @param cost        本次调用耗时
 * @param raw         供应商原始响应（默认不序列化、不进日志）
 *
 * @author L.David
 */
public record AuditResult(
        boolean ok,
        RiskSuggestion suggestion,
        boolean finalResult,
        AuditSource source,
        List<RiskHit> hits,
        String requestId,
        String dataId,
        String provider,
        String product,
        String service,
        Duration cost,
        Map<String, Object> raw) {

    public AuditResult {
        hits = hits == null ? List.of() : List.copyOf(hits);
        raw = raw == null ? Map.of() : Map.copyOf(raw);
    }

    /**
     * 是否业务放行：流程成功且建议为 {@link RiskSuggestion#PASS}
     *
     * @return 可放行返回 {@code true}
     */
    public boolean pass() {
        return ok && RiskSuggestion.PASS == suggestion;
    }

    /** 派生副本：仅替换来源标记，其余字段原样保留（仲裁合并结果标 ARBITRATION 等） */
    public AuditResult withSource(AuditSource source) {
        return new AuditResult(ok, suggestion, finalResult, source, hits, requestId,
                dataId, provider, product, service, cost, raw);
    }

    /** 派生副本：仅替换审核建议，其余字段原样保留 */
    public AuditResult withSuggestion(RiskSuggestion suggestion) {
        return new AuditResult(ok, suggestion, finalResult, source, hits, requestId,
                dataId, provider, product, service, cost, raw);
    }

    /** 派生副本：仅替换命中详情，其余字段原样保留（证据脱敏后重建） */
    public AuditResult withHits(List<RiskHit> hits) {
        return new AuditResult(ok, suggestion, finalResult, source, hits, requestId,
                dataId, provider, product, service, cost, raw);
    }

    /** 派生副本：仅替换供应商原始响应，其余字段原样保留（命中截断后追加标记） */
    public AuditResult withRaw(Map<String, Object> raw) {
        return new AuditResult(ok, suggestion, finalResult, source, hits, requestId,
                dataId, provider, product, service, cost, raw);
    }

    /**
     * 创建构建器，默认 ok=true、suggestion=PASS、finalResult=true、source=SYNC
     *
     * @return 新构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * {@link AuditResult} 构建器
     */
    public static final class Builder {

        private boolean ok = true;
        private RiskSuggestion suggestion = RiskSuggestion.PASS;
        private boolean finalResult = true;
        private AuditSource source = AuditSource.SYNC;
        private List<RiskHit> hits;
        private String requestId;
        private String dataId;
        private String provider;
        private String product;
        private String service;
        private Duration cost;
        private Map<String, Object> raw;

        private Builder() {
        }

        /**
         * 设置审核流程是否成功执行
         *
         * @param ok 流程是否成功
         * @return 本构建器
         */
        public Builder ok(boolean ok) {
            this.ok = ok;
            return this;
        }

        /**
         * 设置归一化审核建议
         *
         * @param suggestion 审核建议
         * @return 本构建器
         */
        public Builder suggestion(RiskSuggestion suggestion) {
            this.suggestion = suggestion;
            return this;
        }

        /**
         * 设置是否为终态
         *
         * @param finalResult 是否终态
         * @return 本构建器
         */
        public Builder finalResult(boolean finalResult) {
            this.finalResult = finalResult;
            return this;
        }

        /**
         * 设置结果来源
         *
         * @param source 结果来源
         * @return 本构建器
         */
        public Builder source(AuditSource source) {
            this.source = source;
            return this;
        }

        /**
         * 设置命中详情列表
         *
         * @param hits 命中详情
         * @return 本构建器
         */
        public Builder hits(List<RiskHit> hits) {
            this.hits = hits;
            return this;
        }

        /**
         * 设置供应商侧请求 ID
         *
         * @param requestId 供应商请求 ID
         * @return 本构建器
         */
        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        /**
         * 设置业务侧数据唯一标识
         *
         * @param dataId 数据标识
         * @return 本构建器
         */
        public Builder dataId(String dataId) {
            this.dataId = dataId;
            return this;
        }

        /**
         * 设置实际执行审核的供应商
         *
         * @param provider 供应商 key
         * @return 本构建器
         */
        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }

        /**
         * 设置供应商产品线
         *
         * @param product 产品线
         * @return 本构建器
         */
        public Builder product(String product) {
            this.product = product;
            return this;
        }

        /**
         * 设置供应商 service/场景 ID
         *
         * @param service 供应商场景 ID
         * @return 本构建器
         */
        public Builder service(String service) {
            this.service = service;
            return this;
        }

        /**
         * 设置本次调用耗时
         *
         * @param cost 调用耗时
         * @return 本构建器
         */
        public Builder cost(Duration cost) {
            this.cost = cost;
            return this;
        }

        /**
         * 设置供应商原始响应
         *
         * @param raw 原始响应
         * @return 本构建器
         */
        public Builder raw(Map<String, Object> raw) {
            this.raw = raw;
            return this;
        }

        /**
         * 构建不可变审核结果
         *
         * @return 审核结果
         */
        public AuditResult build() {
            return new AuditResult(ok, suggestion, finalResult, source, hits,
                    requestId, dataId, provider, product, service, cost, raw);
        }
    }
}
