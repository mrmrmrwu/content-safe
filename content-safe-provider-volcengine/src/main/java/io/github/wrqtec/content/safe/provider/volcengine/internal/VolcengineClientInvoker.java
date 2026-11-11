package io.github.wrqtec.content.safe.provider.volcengine.internal;

import com.volcengine.model.request.ImageRiskResultRequest;
import com.volcengine.model.request.RiskDetectionRequest;
import com.volcengine.model.response.AsyncRiskDetectionResponse;
import com.volcengine.model.response.ImageRiskDetectionResponse;
import com.volcengine.model.response.TextRiskResponse;
import com.volcengine.model.response.VideoRiskResultResponse;

/**
 * 火山引擎内容安全 SDK 调用接缝：唯一允许触碰 SDK 客户端的抽象，
 * 便于契约/单元测试替换实现。所有 SDK 类型不得出现在本包之外的签名中
 * （铁律 §3.4）
 *
 * @author L.David
 */
public interface VolcengineClientInvoker {

    /**
     * 文本同步检测（TextRisk）
     *
     * @param request SDK 请求（appId + service + parameters）
     * @return SDK 响应
     * @throws Exception SDK/网络异常（由 {@link VolcengineErrorMapper} 统一映射）
     */
    TextRiskResponse textRisk(RiskDetectionRequest request) throws Exception;

    /**
     * 图片同步检测（ImageContentRiskV2）
     *
     * @param request SDK 请求（appId + service + parameters）
     * @return SDK 响应
     * @throws Exception SDK/网络异常（由 {@link VolcengineErrorMapper} 统一映射）
     */
    ImageRiskDetectionResponse imageContentRiskV2(RiskDetectionRequest request) throws Exception;

    /**
     * 异步视频提交（AsyncVideoRisk）。提交仅回执（requestId + data.passThrough），
     * 无任务句柄；任务关联键为提交时随 parameters 携带的业务 dataId
     *
     * @param request SDK 请求（appId + service + parameters）
     * @return SDK 回执
     * @throws Exception SDK/网络异常（由 {@link VolcengineErrorMapper} 统一映射）
     */
    AsyncRiskDetectionResponse asyncVideoRisk(RiskDetectionRequest request) throws Exception;

    /**
     * 异步视频结果查询（VideoResult，按 dataId 查询）
     *
     * @param request SDK 请求（appId + service + dataId）
     * @return SDK 响应
     * @throws Exception SDK/网络异常（由 {@link VolcengineErrorMapper} 统一映射）
     */
    VideoRiskResultResponse videoResult(ImageRiskResultRequest request) throws Exception;
}
