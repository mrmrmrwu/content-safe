package io.github.wrqtec.content.safe.model;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;

import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.util.Asserts;

/**
 * 供应商操作能力声明。任何供应商调用前必须先通过 {@link #supports(AuditRequest)} 校验，
 * 不支持则抛出 {@code UnsupportedContentException}，禁止未通过能力校验直接调用。不可变
 *
 * <p>容量字段以 long（字节）表达，
 * 因为 model 模块不允许依赖 Spring</p>
 *
 * @author L.David
 */
public record AuditCapabilities(
        Set<ContentType> contentTypes,
        boolean sync,
        boolean asyncQuery,
        boolean asyncCallback,
        boolean batch,
        boolean url,
        boolean base64,
        boolean bytes,
        boolean objectKey,
        boolean ocr,
        boolean aigcDetect,
        boolean faceDetect,
        boolean customList,
        boolean manualReview,
        boolean maskResult,
        int maxBatchSize,
        long maxUrlSize,
        long maxBase64Size,
        Duration suggestedPollInterval,
        Duration suggestedPollTimeout) {

    public AuditCapabilities {
        contentTypes = contentTypes == null ? Set.of() : Set.copyOf(contentTypes);
    }

    /**
     * 1.1.x 形状的兼容构造器：不声明轮询建议（等价于两个建议字段为 null）。
     * 历史调用方（含 1.0.0/1.1.0 直接使用规范构造器的代码）无需改动
     */
    public AuditCapabilities(Set<ContentType> contentTypes,
                             boolean sync,
                             boolean asyncQuery,
                             boolean asyncCallback,
                             boolean batch,
                             boolean url,
                             boolean base64,
                             boolean bytes,
                             boolean objectKey,
                             boolean ocr,
                             boolean aigcDetect,
                             boolean faceDetect,
                             boolean customList,
                             boolean manualReview,
                             boolean maskResult,
                             int maxBatchSize,
                             long maxUrlSize,
                             long maxBase64Size) {
        this(contentTypes, sync, asyncQuery, asyncCallback, batch, url, base64, bytes,
                objectKey, ocr, aigcDetect, faceDetect, customList, manualReview, maskResult,
                maxBatchSize, maxUrlSize, maxBase64Size, null, null);
    }

    /**
     * 能力校验：内容类型 + 内容引用方式 + 审核选项。选项校验为单向门控：
     * 请求显式开启 AIGC 识别（{@code options.enableAigcDetect == TRUE}）时，
     * 仅声明了 {@code aigcDetect} 能力的操作可路由；开关关闭或缺省不影响任何操作
     *
     * @param request 审核请求，不得为 null
     * @return true 表示该能力组合支持处理此请求
     * @throws io.github.wrqtec.content.safe.api.exception.InvalidRequestException 若 request 为 null
     */
    public boolean supports(AuditRequest request) {
        Asserts.notNull(request, "request");
        if (!contentTypes.contains(request.type())) {
            return false;
        }
        if (request.options().enableAigcDetect() == Boolean.TRUE && !aigcDetect) {
            return false;
        }
        return switch (request.content().type()) {
            case TEXT -> true;
            case URL -> url;
            case BASE64 -> base64;
            case BYTES -> bytes;
            case OBJECT_KEY -> objectKey;
        };
    }

    /**
     * 创建 {@link AuditCapabilities} 的构建器
     *
     * @return 新的构建器实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * {@link AuditCapabilities} 的构建器
     *
     * @author L.David
     */
    public static final class Builder {

        private Set<ContentType> contentTypes = Set.of();
        private boolean sync;
        private boolean asyncQuery;
        private boolean asyncCallback;
        private boolean batch;
        private boolean url;
        private boolean base64;
        private boolean bytes;
        private boolean objectKey;
        private boolean ocr;
        private boolean aigcDetect;
        private boolean faceDetect;
        private boolean customList;
        private boolean manualReview;
        private boolean maskResult;
        private int maxBatchSize = 1;
        private long maxUrlSize;
        private long maxBase64Size;
        private Duration suggestedPollInterval;
        private Duration suggestedPollTimeout;

        private Builder() {
        }

        public Builder contentTypes(ContentType first, ContentType... rest) {
            this.contentTypes = EnumSet.of(first, rest);
            return this;
        }

        public Builder contentTypes(Set<ContentType> contentTypes) {
            this.contentTypes = contentTypes;
            return this;
        }

        public Builder sync(boolean sync) {
            this.sync = sync;
            return this;
        }

        public Builder asyncQuery(boolean asyncQuery) {
            this.asyncQuery = asyncQuery;
            return this;
        }

        public Builder asyncCallback(boolean asyncCallback) {
            this.asyncCallback = asyncCallback;
            return this;
        }

        public Builder batch(boolean batch) {
            this.batch = batch;
            return this;
        }

        public Builder url(boolean url) {
            this.url = url;
            return this;
        }

        public Builder base64(boolean base64) {
            this.base64 = base64;
            return this;
        }

        public Builder bytes(boolean bytes) {
            this.bytes = bytes;
            return this;
        }

        public Builder objectKey(boolean objectKey) {
            this.objectKey = objectKey;
            return this;
        }

        public Builder ocr(boolean ocr) {
            this.ocr = ocr;
            return this;
        }

        public Builder aigcDetect(boolean aigcDetect) {
            this.aigcDetect = aigcDetect;
            return this;
        }

        public Builder faceDetect(boolean faceDetect) {
            this.faceDetect = faceDetect;
            return this;
        }

        public Builder customList(boolean customList) {
            this.customList = customList;
            return this;
        }

        public Builder manualReview(boolean manualReview) {
            this.manualReview = manualReview;
            return this;
        }

        public Builder maskResult(boolean maskResult) {
            this.maskResult = maskResult;
            return this;
        }

        public Builder maxBatchSize(int maxBatchSize) {
            this.maxBatchSize = maxBatchSize;
            return this;
        }

        public Builder maxUrlSize(long maxUrlSize) {
            this.maxUrlSize = maxUrlSize;
            return this;
        }

        public Builder maxBase64Size(long maxBase64Size) {
            this.maxBase64Size = maxBase64Size;
            return this;
        }

        /**
         * 声明异步操作的建议轮询间隔：操作最懂自己云侧的排队与检测节奏（如视频普遍
         * 分钟级、官方建议低频轮询）。非 null 且为正数时，模板单参
         * {@code submitAndWait(request)} 会优先采用，仅异步操作有意义，同步操作声明无效
         *
         * @param suggestedPollInterval 建议轮询间隔，null 表示不声明（用模板默认值）
         * @return 构建器
         */
        public Builder suggestedPollInterval(Duration suggestedPollInterval) {
            this.suggestedPollInterval = suggestedPollInterval;
            return this;
        }

        /**
         * 声明异步操作的建议等待超时：应与操作的结果保留期和典型完成时长匹配
         * （超时后任务仍在，可继续 {@code query}）。非 null 且为正数时，
         * 模板单参 {@code submitAndWait(request)} 会优先采用，仅异步操作有意义
         *
         * @param suggestedPollTimeout 建议等待超时，null 表示不声明（用模板默认值）
         * @return 构建器
         */
        public Builder suggestedPollTimeout(Duration suggestedPollTimeout) {
            this.suggestedPollTimeout = suggestedPollTimeout;
            return this;
        }

        /**
         * 构建不可变的 {@link AuditCapabilities}
         *
         * @return 能力声明实例
         */
        public AuditCapabilities build() {
            return new AuditCapabilities(contentTypes, sync, asyncQuery, asyncCallback, batch,
                    url, base64, bytes, objectKey, ocr, aigcDetect, faceDetect, customList,
                    manualReview, maskResult, maxBatchSize, maxUrlSize, maxBase64Size,
                    suggestedPollInterval, suggestedPollTimeout);
        }
    }
}
