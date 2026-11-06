package io.github.wrqtec.content.safe.api;

/**
 * 内容安全统一门面，业务方注入后直接使用：
 *
 * <pre>{@code
 * AuditResult result = contentSafeTemplate.audit(AuditRequest.text("nickname", nickname));
 * }</pre>
 *
 * <p>实现由 core 提供，starter 自动装配；所有实现必须保证：
 * 审核前通过能力声明校验、调用异常归一为 {@code ContentSafeException} 体系、
 * 不泄露密钥与用户内容原文到日志</p>
 *
 * @author L.David
 */
public interface ContentSafeTemplate {

    /**
     * 同步审核（文本/图片）
     *
     * @param request 统一审核请求
     * @return 归一化审核结果
     */
    AuditResult audit(AuditRequest request);

    /**
     * 提交异步审核任务（如图片异步审核）
     *
     * @param request 统一审核请求
     * @return 本库统一任务 ID
     */
    String submitAsync(AuditRequest request);

    /**
     * 查询异步任务结果；优先读任务存储，必要时回源供应商
     *
     * @param taskId 本库统一任务 ID
     * @return 归一化审核结果
     */
    AuditResult query(String taskId);

    /**
     * 处理供应商异步回调：验签 -> 幂等 -> 更新任务 -> 发事件；
     * 验签失败/格式错误的回调进入死信，结果以 {@link CallbackResult#accepted()} = false 返回
     *
     * @param callback 供应商回调报文
     * @return 回调处理结果
     */
    CallbackResult handleCallback(CallbackRequest callback);

    /**
     * 文本审核便捷方法
     *
     * @param scene 业务场景 code
     * @param text  文本原文
     * @return 归一化审核结果
     */
    default AuditResult text(String scene, String text) {
        return audit(AuditRequest.text(scene, text));
    }

    /**
     * 图片审核便捷方法（公网 URL）
     *
     * @param scene 业务场景 code
     * @param url   公网可访问的图片 URL
     * @return 归一化审核结果
     */
    default AuditResult image(String scene, String url) {
        return audit(AuditRequest.image(scene, ContentRef.url(url)));
    }
}
