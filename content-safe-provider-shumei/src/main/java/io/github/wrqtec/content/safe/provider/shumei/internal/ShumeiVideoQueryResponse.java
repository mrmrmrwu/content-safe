package io.github.wrqtec.content.safe.provider.shumei.internal;

import java.util.List;

import com.google.gson.JsonObject;
import io.github.wrqtec.content.safe.provider.shumei.ShumeiProviderConstants;

/**
 * 数美视频文件识别响应（提交回执 / 主动查询 / 异步回调三种报文共用模型）
 *
 * <p>字段名与官方文档一致（智能视频文件识别产品 API 文档 V4，
 * help.ishumei.com/docs/tj/video/versionV4，2026-11-11 调研）：
 * 提交响应仅回执 {@code code/message/requestId/btId}（无 taskId，结果靠回调或查询）；
 * 查询与回调报文在回执字段之外追加 {@code riskLevel/auxInfo}，两者同构。
 * 仅声明归一化所需字段；{@code frameDetail[].imgUrl}（签名截帧地址）、
 * {@code audioDetail[].audioUrl}（签名音频地址）、{@code audioDetail[].audioText}（转写原文）
 * 建模仅为完整性标注，归一化整体不透传。
 *
 * @author L.David
 */
public class ShumeiVideoQueryResponse {

    /** 返回码：1100 成功，1101 视频处理中，1901 QPS 超限，1902 参数不合法，
     * 1903 服务失败，1905 识别内容不规范，9101 无权限操作 */
    private Integer code;

    /** 返回码描述 */
    private String message;

    /** 请求标识 */
    private String requestId;

    /** 客户侧请求唯一标识（提交时 data.data.btId 的回显，任务关联键） */
    private String btId;

    /** 处置建议：PASS / REVIEW / REJECT（查询与回调报文返回） */
    private String riskLevel;

    /** 辅助信息（截帧/音频片段明细与计费统计） */
    private AuxInfo auxInfo;

    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getBtId() {
        return btId;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public AuxInfo getAuxInfo() {
        return auxInfo;
    }

    /**
     * 业务成功（返回码 1100）
     */
    public boolean isSuccess() {
        return code != null && code == ShumeiProviderConstants.CODE_SUCCESS;
    }

    /**
     * 视频处理中（返回码 1101，查询接口专属中间态）
     */
    public boolean isProcessing() {
        return code != null && code == ShumeiProviderConstants.CODE_VIDEO_PROCESSING;
    }

    /**
     * auxInfo：视频辅助信息
     */
    public static class AuxInfo {

        /** 返回的视频截帧数量（returnAllImg=0 时为风险数量） */
        private Integer frameCount;

        /** 截帧图片风险详情（有风险片段或 returnAllImg=1 时返回） */
        private List<FrameDetail> frameDetail;

        /** 音频片段风险详情（有风险片段或 returnAllAudio=1 时返回） */
        private List<AudioDetail> audioDetail;

        public Integer getFrameCount() {
            return frameCount;
        }

        public List<FrameDetail> getFrameDetail() {
            return frameDetail;
        }

        public List<AudioDetail> getAudioDetail() {
            return audioDetail;
        }
    }

    /**
     * 截帧图片风险详情（imgUrl 为签名地址，归一化不透传）
     */
    public static class FrameDetail {

        /** 截帧图片地址（签名 URL，用户内容衍生，不透传） */
        private String imgUrl;

        /** 数美请求唯一标识 */
        private String requestId;

        /** 处置建议：PASS / REVIEW / REJECT */
        private String riskLevel;

        /** 一级风险标签 */
        private String riskLabel1;

        /** 二级风险标签 */
        private String riskLabel2;

        /** 三级风险标签 */
        private String riskLabel3;

        /** 风险原因（官方注明仅供人参考，程序勿依赖） */
        private String riskDescription;

        /** 全部风险标签列表 */
        private List<ShumeiSyncResponse.LabelEntry> allLabels;

        /** 风险详情（riskSource/matchedLists 等，按需提取） */
        private JsonObject riskDetail;

        public String getImgUrl() {
            return imgUrl;
        }

        public String getRequestId() {
            return requestId;
        }

        public String getRiskLevel() {
            return riskLevel;
        }

        public String getRiskLabel1() {
            return riskLabel1;
        }

        public String getRiskLabel2() {
            return riskLabel2;
        }

        public String getRiskLabel3() {
            return riskLabel3;
        }

        public String getRiskDescription() {
            return riskDescription;
        }

        public List<ShumeiSyncResponse.LabelEntry> getAllLabels() {
            return allLabels;
        }

        public JsonObject getRiskDetail() {
            return riskDetail;
        }
    }

    /**
     * 音频片段风险详情（audioUrl 签名地址与 audioText 转写原文均不透传）
     */
    public static class AudioDetail {

        /** 音频片段地址（签名 URL，用户内容衍生，不透传） */
        private String audioUrl;

        /** 数美请求唯一标识 */
        private String requestId;

        /** 处置建议：PASS / REVIEW / REJECT */
        private String riskLevel;

        /** 一级风险标签 */
        private String riskLabel1;

        /** 二级风险标签 */
        private String riskLabel2;

        /** 三级风险标签 */
        private String riskLabel3;

        /** 风险原因（官方注明仅供人参考，程序勿依赖） */
        private String riskDescription;

        /** 全部风险标签列表 */
        private List<ShumeiSyncResponse.LabelEntry> allLabels;

        /** 风险详情（riskSource 等，按需提取） */
        private JsonObject riskDetail;

        /** 该片段识别的文字内容（转写原文，不透传） */
        private String audioText;

        public String getAudioUrl() {
            return audioUrl;
        }

        public String getRequestId() {
            return requestId;
        }

        public String getRiskLevel() {
            return riskLevel;
        }

        public String getRiskLabel1() {
            return riskLabel1;
        }

        public String getRiskLabel2() {
            return riskLabel2;
        }

        public String getRiskLabel3() {
            return riskLabel3;
        }

        public String getRiskDescription() {
            return riskDescription;
        }

        public List<ShumeiSyncResponse.LabelEntry> getAllLabels() {
            return allLabels;
        }

        public JsonObject getRiskDetail() {
            return riskDetail;
        }

        public String getAudioText() {
            return audioText;
        }
    }
}
