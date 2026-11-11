package io.github.wrqtec.content.safe.api;

import java.util.Map;
import java.util.UUID;

import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.util.Asserts;

/**
 * 统一审核请求，不可变；业务代码只传业务场景 code（如 {@code nickname}），
 * 云厂商场景 ID 只允许出现在 yml 配置与供应商内部
 *
 * @author L.David
 */
public record AuditRequest(
        String scene,
        ContentType type,
        ContentRef content,
        String dataId,
        AuditContext context,
        AuditOptions options,
        Map<String, Object> passThrough) {

    public AuditRequest {
        Asserts.notBlank(scene, "scene");
        Asserts.notNull(type, "type");
        Asserts.notNull(content, "content");
        options = options == null ? AuditOptions.defaults() : options;
        passThrough = passThrough == null ? Map.of() : Map.copyOf(passThrough);
    }

    /**
     * 文本审核快捷构造。dataId 自动生成（UUID，用于异步任务与回调对账、人审闭环回链）；
     * 需要以业务 ID 对账时用 {@link #builder()} 显式传入
     */
    public static AuditRequest text(String scene, String text) {
        return builder().scene(scene).type(ContentType.TEXT)
                .content(ContentRef.text(text)).dataId(newDataId()).build();
    }

    /**
     * 图片审核快捷构造。dataId 自动生成（UUID，用于异步任务与回调对账、人审闭环回链）；
     * 需要以业务 ID 对账时用 {@link #builder()} 显式传入
     */
    public static AuditRequest image(String scene, ContentRef content) {
        return builder().scene(scene).type(ContentType.IMAGE)
                .content(content).dataId(newDataId()).build();
    }

    /**
     * 视频审核快捷构造。视频审核均为异步：提交用 {@code submitAsync}，结果通过
     * {@code query(taskId)} 轮询或回调获取，同步 {@code audit()} 对视频请求会抛
     * {@code ProviderNotAvailableException}（无同步候选）；内容应为公网可访问 URL
     * （{@link ContentRef#url(String)}），供应商按能力声明决定是否接受其他形态。
     * dataId 自动生成（UUID，用于异步任务与回调对账、人审闭环回链）；
     * 需要以业务 ID 对账时用 {@link #builder()} 显式传入
     */
    public static AuditRequest video(String scene, ContentRef content) {
        return builder().scene(scene).type(ContentType.VIDEO)
                .content(content).dataId(newDataId()).build();
    }

    /** 生成请求级 dataId（32 位无杠 UUID，满足各供应商 dataId 长度约束） */
    private static String newDataId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 创建构建器
     *
     * @return 新构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * {@link AuditRequest} 构建器
     */
    public static final class Builder {

        private String scene;
        private ContentType type;
        private ContentRef content;
        private String dataId;
        private AuditContext context;
        private AuditOptions options;
        private Map<String, Object> passThrough;

        private Builder() {
        }

        /**
         * 业务场景 code（如 {@code nickname}），必填
         *
         * @param scene 业务场景 code
         * @return 本构建器
         */
        public Builder scene(String scene) {
            this.scene = scene;
            return this;
        }

        /**
         * 内容类型，必填
         *
         * @param type 内容类型
         * @return 本构建器
         */
        public Builder type(ContentType type) {
            this.type = type;
            return this;
        }

        /**
         * 待审核内容引用，必填
         *
         * @param content 内容引用
         * @return 本构建器
         */
        public Builder content(ContentRef content) {
            this.content = content;
            return this;
        }

        /**
         * 业务侧数据唯一标识，用于异步任务与回调对账
         */
        public Builder dataId(String dataId) {
            this.dataId = dataId;
            return this;
        }

        /**
         * 发起方用户/设备上下文
         *
         * @param context 审核上下文
         * @return 本构建器
         */
        public Builder context(AuditContext context) {
            this.context = context;
            return this;
        }

        /**
         * 单次审核的可选开关
         *
         * @param options 审核选项
         * @return 本构建器
         */
        public Builder options(AuditOptions options) {
            this.options = options;
            return this;
        }

        /**
         * 透传给供应商的扩展参数，同时可作为多租户成本标签载体
         */
        public Builder passThrough(Map<String, Object> passThrough) {
            this.passThrough = passThrough;
            return this;
        }

        /**
         * 构建不可变请求；scene/type/content 缺失时抛出 {@code InvalidRequestException}
         *
         * @return 审核请求
         */
        public AuditRequest build() {
            return new AuditRequest(scene, type, content, dataId, context, options, passThrough);
        }
    }
}
