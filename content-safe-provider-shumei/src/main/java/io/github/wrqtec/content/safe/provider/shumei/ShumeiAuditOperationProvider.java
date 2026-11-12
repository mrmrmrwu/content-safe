package io.github.wrqtec.content.safe.provider.shumei;

import java.util.List;

import io.github.wrqtec.content.safe.api.enums.ContentType;
import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import io.github.wrqtec.content.safe.model.enums.AuditMode;
import io.github.wrqtec.content.safe.api.util.Asserts;
import io.github.wrqtec.content.safe.model.OperationLimits;
import io.github.wrqtec.content.safe.model.ProviderPlan;
import io.github.wrqtec.content.safe.model.SceneDefinition;
import io.github.wrqtec.content.safe.provider.shumei.internal.ShumeiAuditResultNormalizer;
import io.github.wrqtec.content.safe.provider.shumei.internal.ShumeiClientInvoker;
import io.github.wrqtec.content.safe.provider.shumei.internal.ShumeiImageAsyncOperation;
import io.github.wrqtec.content.safe.provider.shumei.internal.ShumeiImageDetectOperation;
import io.github.wrqtec.content.safe.provider.shumei.internal.ShumeiLimits;
import io.github.wrqtec.content.safe.provider.shumei.internal.ShumeiTextDetectOperation;
import io.github.wrqtec.content.safe.provider.shumei.internal.ShumeiVideoAsyncOperation;
import io.github.wrqtec.content.safe.spi.AuditOperation;
import io.github.wrqtec.content.safe.spi.AuditOperationProvider;

/**
 * 数美内容安全 {@link AuditOperationProvider}
 *
 * <p>按场景配置注册操作：scenes 映射值 = 与数美约定的 eventId（官方强约定）；
 * 未映射场景返回空列表，由路由跳过。检测类型经 extra 的 text-type/image-type 配置，
 * 缺省 TEXTRISK 与 POLITY_EROTIC_VIOLENT_QRCODE_ADVERT_IMGTEXTRISK。
 * 文本注册同步操作；图片注册同步 + 异步（提交/轮询/回调）两个操作；
 * 视频注册异步操作（提交/轮询/回调，仅 URL 通道）。
 *
 * @author L.David
 */
public class ShumeiAuditOperationProvider implements AuditOperationProvider {

    private final ShumeiProviderProperties properties;
    private final ShumeiClientInvoker client;
    private final ShumeiAuditResultNormalizer normalizer;

    public ShumeiAuditOperationProvider(ShumeiProviderProperties properties, ShumeiClientInvoker client) {
        this.properties = Asserts.notNull(properties, "properties");
        this.client = Asserts.notNull(client, "client");
        this.normalizer = new ShumeiAuditResultNormalizer();
    }

    @Override
    public String provider() {
        return ShumeiProviderConstants.PROVIDER;
    }

    @Override
    public List<ContentType> supportedTypes() {
        return List.of(ContentType.TEXT, ContentType.IMAGE, ContentType.VIDEO);
    }

    @Override
    public List<AuditOperation> operations(SceneDefinition scene) {
        Asserts.notNull(scene, "scene");
        String eventId = properties.getScenes().get(scene.code());
        if (eventId == null) {
            return List.of();
        }
        if (scene.type() == ContentType.TEXT) {
            return List.of(new ShumeiTextDetectOperation(
                    syncPlan(ShumeiProviderConstants.OPERATION_TEXT_DETECT, eventId, ShumeiLimits.textSync()),
                    client, normalizer, textTypeOf(), scene));
        }
        if (scene.type() == ContentType.IMAGE) {
            return List.of(new ShumeiImageDetectOperation(
                    syncPlan(ShumeiProviderConstants.OPERATION_IMAGE_DETECT, eventId, ShumeiLimits.imageSync()),
                    client, normalizer, imageTypeOf(), scene),
                    new ShumeiImageAsyncOperation(
                            asyncPlan(eventId), client, normalizer, imageTypeOf(), scene));
        }
        if (scene.type() == ContentType.VIDEO) {
            return List.of(new ShumeiVideoAsyncOperation(videoPlan(eventId), client, normalizer,
                    videoImgTypeOf(), videoAudioTypeOf(), screenshotTypeOf(), scene));
        }
        return List.of();
    }

    /**
     * 同步操作计划（测试亦可直接复用）
     */
    public static ProviderPlan syncPlan(String operation, String eventId, OperationLimits limits) {
        return new ProviderPlan(ShumeiProviderConstants.PROVIDER, ShumeiProviderConstants.PRODUCT,
                ShumeiProviderConstants.API_VERSION, operation, eventId, AuditMode.SYNC, limits, null);
    }

    /**
     * 图片异步操作计划：提交 + 轮询 + 回调三通道共用（回调地址在数美控制台配置）
     */
    public static ProviderPlan asyncPlan(String eventId) {
        return new ProviderPlan(ShumeiProviderConstants.PROVIDER, ShumeiProviderConstants.PRODUCT,
                ShumeiProviderConstants.API_VERSION, ShumeiProviderConstants.OPERATION_IMAGE_ASYNC_SUBMIT,
                eventId, AuditMode.ASYNC_QUERY, ShumeiLimits.imageAsync(), null);
    }

    /**
     * 视频异步操作计划：提交 + 轮询 + 回调三通道共用（提交仅回执，任务关联键 = data.btId）
     */
    public static ProviderPlan videoPlan(String eventId) {
        return new ProviderPlan(ShumeiProviderConstants.PROVIDER, ShumeiProviderConstants.PRODUCT,
                ShumeiProviderConstants.API_VERSION, ShumeiProviderConstants.OPERATION_VIDEO_SUBMIT,
                eventId, AuditMode.ASYNC_QUERY, ShumeiLimits.videoAsync(), null);
    }

    private String textTypeOf() {
        String type = properties.extraValue(ShumeiProviderConstants.EXTRA_TEXT_TYPE);
        return type == null ? ShumeiProviderConstants.DEFAULT_TEXT_TYPE : type;
    }

    private String imageTypeOf() {
        String type = properties.extraValue(ShumeiProviderConstants.EXTRA_IMAGE_TYPE);
        return type == null ? ShumeiProviderConstants.DEFAULT_IMAGE_TYPE : type;
    }

    private String videoImgTypeOf() {
        String type = properties.extraValue(ShumeiProviderConstants.EXTRA_VIDEO_IMG_TYPE);
        return type == null ? ShumeiProviderConstants.DEFAULT_VIDEO_IMG_TYPE : type;
    }

    private String videoAudioTypeOf() {
        String type = properties.extraValue(ShumeiProviderConstants.EXTRA_VIDEO_AUDIO_TYPE);
        return type == null ? ShumeiProviderConstants.DEFAULT_VIDEO_AUDIO_TYPE : type;
    }

    private int screenshotTypeOf() {
        String value = properties.extraValue(ShumeiProviderConstants.EXTRA_SCREENSHOT_TYPE);
        if (value == null) {
            return ShumeiProviderConstants.DEFAULT_SCREENSHOT_TYPE;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new ConfigInvalidException(
                    "shumei extra '" + ShumeiProviderConstants.EXTRA_SCREENSHOT_TYPE
                            + "' must be an integer, got: " + value);
        }
    }
}
