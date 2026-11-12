package io.github.wrqtec.content.safe.api;

import java.time.Duration;
import java.time.Instant;

import io.github.wrqtec.content.safe.api.exception.InvalidRequestException;
import io.github.wrqtec.content.safe.api.exception.ProviderCallException;
import io.github.wrqtec.content.safe.api.exception.UnsupportedFeatureException;
import io.github.wrqtec.content.safe.api.util.Asserts;

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
     * 提交异步任务并阻塞轮询至终态，轮询间隔与超时取实现配置（starter 接线
     * {@code content.safe.defaults.async.*}）。仅对支持查询的异步任务有意义；
     * 仅回调推送的供应商（如腾讯图片异步）会在首次查询时抛出
     * {@link io.github.wrqtec.content.safe.api.exception.ProviderNotAvailableException}，
     * 此类任务请以回调为准。超时抛 {@link ProviderCallException}，任务仍在，
     * 可后续继续 {@link #query(String)}
     *
     * <p>异步操作可通过能力声明的建议轮询参数（{@code AuditCapabilities.suggestedPollInterval}
     * / {@code suggestedPollTimeout}，如视频操作普遍声明 30s / 30min）覆盖模板默认值；
     * 操作未声明时使用模板配置。需要更精细控制请用三参重载
     * {@link #submitAndWait(AuditRequest, Duration, Duration)}</p>
     *
     * @param request 统一审核请求
     * @return 终态审核结果
     */
    default AuditResult submitAndWait(AuditRequest request) {
        throw new UnsupportedFeatureException("submitAndWait is not supported by this template");
    }

    /**
     * 提交异步任务并阻塞轮询至终态的通用实现：submitAsync → 按 pollInterval 周期 query，
     * 直到 finalResult=true 或超时
     *
     * @param request      统一审核请求
     * @param pollInterval 轮询间隔，非正数视为非法
     * @param timeout      总等待超时，非正数视为非法
     * @return 终态审核结果
     * @throws ProviderCallException 等待超时或线程中断；任务仍在，可后续 query
     */
    default AuditResult submitAndWait(AuditRequest request, Duration pollInterval, Duration timeout) {
        Asserts.notNull(request, "request");
        Asserts.notNull(pollInterval, "pollInterval");
        Asserts.notNull(timeout, "timeout");
        if (pollInterval.isNegative() || pollInterval.isZero()) {
            throw new InvalidRequestException("pollInterval must be positive: " + pollInterval);
        }
        if (timeout.isNegative() || timeout.isZero()) {
            throw new InvalidRequestException("timeout must be positive: " + timeout);
        }
        String taskId = submitAsync(request);
        return queryUntilFinal(taskId, pollInterval, timeout);
    }

    /**
     * 阻塞轮询既有任务直至终态：按 pollInterval 周期 {@link #query(String)}，
     * 直到 finalResult=true 或超时。手动 {@link #submitAsync(AuditRequest)} 后
     * 不想自己写轮询循环时可直接使用
     *
     * @param taskId       本库统一任务 ID
     * @param pollInterval 轮询间隔，须为正数
     * @param timeout      总等待超时，须为正数
     * @return 终态审核结果
     * @throws io.github.wrqtec.content.safe.api.exception.InvalidRequestException 参数非法
     * @throws ProviderCallException 等待超时或线程中断；任务仍在，可后续 query
     */
    default AuditResult queryUntilFinal(String taskId, Duration pollInterval, Duration timeout) {
        Asserts.notBlank(taskId, "taskId");
        Asserts.notNull(pollInterval, "pollInterval");
        Asserts.notNull(timeout, "timeout");
        if (pollInterval.isNegative() || pollInterval.isZero()) {
            throw new InvalidRequestException("pollInterval must be positive: " + pollInterval);
        }
        if (timeout.isNegative() || timeout.isZero()) {
            throw new InvalidRequestException("timeout must be positive: " + timeout);
        }
        Instant deadline = Instant.now().plus(timeout);
        while (true) {
            AuditResult result = query(taskId);
            if (result.finalResult()) {
                return result;
            }
            long remainingMillis = deadline.toEpochMilli() - System.currentTimeMillis();
            if (remainingMillis <= 0) {
                throw new ProviderCallException(
                        "submitAndWait timed out after " + timeout + " for taskId: " + taskId);
            }
            try {
                Thread.sleep(Math.min(pollInterval.toMillis(), remainingMillis));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ProviderCallException("submitAndWait interrupted for taskId: " + taskId, e);
            }
        }
    }

    /**
     * 查询异步任务结果；优先读任务存储，必要时回源供应商。
     * 任务未完成时返回中间态结果：{@code finalResult=false} 且
     * {@code suggestion} 为 {@code null}（消费方须以 {@code finalResult} 门控，
     * 详见 {@link AuditResult} 的可空契约）
     *
     * @param taskId 本库统一任务 ID
     * @return 归一化审核结果（中间态或终态）
     */
    AuditResult query(String taskId);

    /**
     * 处理供应商异步回调：验签 -> 幂等 -> 更新任务 -> 发事件；
     * 验签失败抛 {@link io.github.wrqtec.content.safe.api.exception.CallbackVerifyException}
     * （内部已记死信，由 web 层映射 401）；无法分发到供应商操作等拒收场景才以
     * {@link CallbackResult#accepted()} = false 返回
     *
     * @param callback 供应商回调报文
     * @return 回调处理结果
     */
    CallbackResult handleCallback(CallbackRequest callback);

    /**
     * 人审终态回写：把机审 {@code REVIEW} 结论归一为终态结果
     * （source=MANUAL、finalResult=true、suggestion=PASS/REJECT 对应决议）。
     * taskId 存在时同步迁移异步任务终态并发布 {@code AuditEvent}；
     * 注入 {@code ReviewResultSink} 时先回写后迁移
     *
     * @param resolution 人审决议
     * @return 归一化终态结果
     * @throws io.github.wrqtec.content.safe.api.exception.InvalidRequestException taskId 未知或任务已终态
     */
    default AuditResult resolveReview(ReviewResolution resolution) {
        throw new UnsupportedFeatureException("resolveReview is not supported by this template");
    }

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

    /**
     * 视频审核便捷方法（公网 URL）：提交异步审核任务并返回本库统一任务 ID。
     * 与文本/图片便捷方法不同，视频没有同步语义——用返回的 taskId 轮询
     * {@link #query(String)} 至 {@code finalResult=true}（或配置回调接收），
     * 也可用 {@link #submitAndWait(AuditRequest)} 阻塞等待终态
     *
     * @param scene 业务场景 code
     * @param url   公网可访问的视频 URL
     * @return 本库统一任务 ID
     */
    default String video(String scene, String url) {
        return submitAsync(AuditRequest.video(scene, ContentRef.url(url)));
    }
}
