package io.github.wrqtec.content.safe.provider.volcengine.internal;

import com.volcengine.model.request.RiskDetectionRequest;
import com.volcengine.model.response.ImageRiskDetectionResponse;
import com.volcengine.model.response.TextRiskResponse;

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
}
