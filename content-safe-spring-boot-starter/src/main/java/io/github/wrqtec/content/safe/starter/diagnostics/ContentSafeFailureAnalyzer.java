package io.github.wrqtec.content.safe.starter.diagnostics;

import io.github.wrqtec.content.safe.api.exception.ContentSafeException;
import io.github.wrqtec.content.safe.api.exception.ErrorCodes;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * 启动失败诊断：把装配期抛出的 {@link ContentSafeException}（含子类，常被
 * {@link BeanCreationException} 包装）翻译为可读的 FailureAnalysis 报告
 * （覆盖：配置缺失、EOL provider、feature 未实现、scene 未映射）
 *
 * <p>安全约束：只输出错误码、原因与修复动作；message 本身已按约定不含
 * accessKey/secret/签名 URL/用户内容原文，本类只做转述，不新增敏感信息
 *
 * @author L.David
 */
public class ContentSafeFailureAnalyzer extends AbstractFailureAnalyzer<ContentSafeException> {

    /** 凭据缺失原因片段（CredentialsResolver 约定文案） */
    private static final String REASON_MISSING_CREDENTIALS = "missing credentials for provider";

    /** 仲裁策略未知原因片段（ArbitrationPolicyRegistry 约定文案） */
    private static final String REASON_UNKNOWN_ARBITRATION = "unknown arbitration policy";

    /** scenes.json schema 版本原因片段（JsonSceneDefinitionProvider 约定文案） */
    private static final String REASON_SCENES_SCHEMA = "scenes.json schemaVersion";

    /** 全局配置 schema 版本原因片段（ContentSafeConfigValidator 约定文案） */
    private static final String REASON_SCHEMA_VERSION = "content.safe.schema-version";

    /** 修复动作：全局配置 schema 版本不支持 */
    private static final String ACTION_SCHEMA_VERSION = "Set content.safe.schema-version to the supported version "
            + "stated in the reason, or upgrade/downgrade content-safe-spring-boot-starter to a release "
            + "that supports your configuration schema.";

    /** 修复动作：scenes.json schema 版本不支持 */
    private static final String ACTION_SCENES_SCHEMA = "Align classpath:content-safe/scenes.json schemaVersion "
            + "with the supported version stated in the reason; breaking schema changes ship with a "
            + "migration note in the release notes.";

    /** 修复动作：仲裁策略名无法解析 */
    private static final String ACTION_UNKNOWN_ARBITRATION = "Set content.safe.defaults.arbitration (or "
            + "scenes.<code>.defaultArbitration) to a registered policy such as FIRST_NON_PASS, or register "
            + "an AuditArbitrationPolicy bean whose name() matches the configured value.";

    /** 修复动作：凭据缺失 */
    private static final String ACTION_MISSING_CREDENTIALS = "Configure the credentials as stated in the reason "
            + "(yml ak/sk or the listed environment variables), or register a custom CredentialsProvider bean "
            + "for the provider; never commit secrets to version control.";

    /** 修复动作：其他配置非法 */
    private static final String ACTION_CONFIG_INVALID = "Fix the content.safe.* configuration stated in the reason; "
            + "see docs/content-safe-starter-guide.md for the configuration reference.";

    /** 修复动作：场景未注册 */
    private static final String ACTION_SCENE_NOT_CONFIGURED = "Register the scene code via "
            + "classpath:content-safe/scenes.json or a SceneDefinitionProvider bean, or use a built-in scene "
            + "(nickname/avatar/comment/post/product_image/im_message).";

    /** 修复动作：特性超出 v1 范围或 provider 未实现 */
    private static final String ACTION_UNSUPPORTED_FEATURE = "The requested feature is outside v1 scope or not "
            + "implemented by the chosen provider; check docs/provider-feature-matrix.md before enabling it.";

    /** 修复动作：内容形态不被候选 provider 支持 */
    private static final String ACTION_UNSUPPORTED_CONTENT = "The candidate providers do not support this content "
            + "type/reference form; adjust the content or route the scene to a capable provider "
            + "(docs/provider-feature-matrix.md).";

    /** 修复动作：请求/回调报文非法 */
    private static final String ACTION_INVALID_REQUEST = "Fix the request or callback payload stated in the reason.";

    /** 修复动作：无可用 provider */
    private static final String ACTION_PROVIDER_NOT_AVAILABLE = "All candidates were circuit-open, throttled or "
            + "disabled; check provider health status and content.safe.providers.<code>.enabled, then retry.";

    /** 修复动作：provider 调用失败 */
    private static final String ACTION_PROVIDER_CALL_ERROR = "Inspect the provider-side failure stated in the reason "
            + "(network/quota/auth); this error is retryable per docs/error-code-table.md.";

    /** 修复动作：全部候选 provider 失败 */
    private static final String ACTION_ALL_PROVIDER_FAILED = "Inspect the per-planKey reasons stated in the reason; "
            + "content.safe.defaults.failover controls automatic failover across candidates.";

    /** 修复动作：回调验签失败 */
    private static final String ACTION_CALLBACK_VERIFY_FAILED = "Check content.safe.callback-secret and the "
            + "provider-side callback signature settings; rejected callbacks are dead-lettered as "
            + "CallbackDeadLetterEvent.";

    /** 修复动作：兜底 */
    private static final String ACTION_GENERIC = "See docs/error-code-table.md for this error code and "
            + "docs/content-safe-starter-guide.md for configuration help.";

    @Override
    protected FailureAnalysis analyze(Throwable failure, ContentSafeException cause) {
        return new FailureAnalysis(describe(failure, cause), actionOf(cause), failure);
    }

    /**
     * 拼装描述：错误码 + 出错 Bean + 原因 message
     */
    private String describe(Throwable failure, ContentSafeException cause) {
        StringBuilder description = new StringBuilder(128);
        description.append("content-safe startup failed with code ").append(cause.getCode());
        String beanName = beanNameOf(failure);
        if (beanName != null) {
            description.append(" (while creating bean '").append(beanName).append("')");
        }
        description.append('\n').append(cause.getMessage());
        return description.toString();
    }

    /**
     * 沿异常链找首个携带 bean 名称的 {@link BeanCreationException}，便于定位是哪个 Bean 装配失败
     */
    private String beanNameOf(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof BeanCreationException creation && creation.getBeanName() != null) {
                return creation.getBeanName();
            }
            current = current.getCause();
        }
        return null;
    }

    /**
     * 按稳定错误码选择修复动作；CONFIG_INVALID 再按原因文案细分（凭据/仲裁策略/schema 版本）
     */
    private String actionOf(ContentSafeException cause) {
        String code = cause.getCode();
        if (ErrorCodes.CONFIG_INVALID.equals(code)) {
            return configInvalidAction(cause.getMessage());
        }
        if (ErrorCodes.SCENE_NOT_CONFIGURED.equals(code)) {
            return ACTION_SCENE_NOT_CONFIGURED;
        }
        if (ErrorCodes.UNSUPPORTED_FEATURE.equals(code)) {
            return ACTION_UNSUPPORTED_FEATURE;
        }
        if (ErrorCodes.UNSUPPORTED_CONTENT.equals(code)) {
            return ACTION_UNSUPPORTED_CONTENT;
        }
        if (ErrorCodes.INVALID_REQUEST.equals(code)) {
            return ACTION_INVALID_REQUEST;
        }
        if (ErrorCodes.PROVIDER_NOT_AVAILABLE.equals(code)) {
            return ACTION_PROVIDER_NOT_AVAILABLE;
        }
        if (ErrorCodes.PROVIDER_CALL_ERROR.equals(code)) {
            return ACTION_PROVIDER_CALL_ERROR;
        }
        if (ErrorCodes.ALL_PROVIDER_FAILED.equals(code)) {
            return ACTION_ALL_PROVIDER_FAILED;
        }
        if (ErrorCodes.CALLBACK_VERIFY_FAILED.equals(code)) {
            return ACTION_CALLBACK_VERIFY_FAILED;
        }
        return ACTION_GENERIC;
    }

    /**
     * CONFIG_INVALID 细分提示：原因文案片段与各装配类约定，勿随文案改动失配
     */
    private String configInvalidAction(String message) {
        if (message == null) {
            return ACTION_CONFIG_INVALID;
        }
        if (message.contains(REASON_SCHEMA_VERSION)) {
            return ACTION_SCHEMA_VERSION;
        }
        if (message.contains(REASON_SCENES_SCHEMA)) {
            return ACTION_SCENES_SCHEMA;
        }
        if (message.contains(REASON_UNKNOWN_ARBITRATION)) {
            return ACTION_UNKNOWN_ARBITRATION;
        }
        if (message.contains(REASON_MISSING_CREDENTIALS)) {
            return ACTION_MISSING_CREDENTIALS;
        }
        return ACTION_CONFIG_INVALID;
    }
}
