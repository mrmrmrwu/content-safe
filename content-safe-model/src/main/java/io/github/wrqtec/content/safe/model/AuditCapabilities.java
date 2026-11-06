package io.github.wrqtec.content.safe.model;

import java.util.EnumSet;
import java.util.Set;

import io.github.wrqtec.content.safe.api.AuditRequest;
import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.util.Asserts;

/**
 * 供应商操作能力声明。任何供应商调用前必须先通过 {@link #supports(AuditRequest)} 校验，
 * 不支持则抛出 {@code UnsupportedContentException}，禁止未通过能力校验直接调用。不可变
 *
 * <p>设计文档中的 {@code DataSize} 字段在此以 long（字节）表达，
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
        long maxBase64Size) {

    public AuditCapabilities {
        contentTypes = contentTypes == null ? Set.of() : Set.copyOf(contentTypes);
    }

    /**
     * 能力校验：内容类型 + 内容引用方式
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
         * 构建不可变的 {@link AuditCapabilities}
         *
         * @return 能力声明实例
         */
        public AuditCapabilities build() {
            return new AuditCapabilities(contentTypes, sync, asyncQuery, asyncCallback, batch,
                    url, base64, bytes, objectKey, ocr, aigcDetect, faceDetect, customList,
                    manualReview, maskResult, maxBatchSize, maxUrlSize, maxBase64Size);
        }
    }
}
