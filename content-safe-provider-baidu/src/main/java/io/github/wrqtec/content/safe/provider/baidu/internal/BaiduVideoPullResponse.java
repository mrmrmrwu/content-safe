package io.github.wrqtec.content.safe.provider.baidu.internal;

import java.util.List;

import com.google.gson.annotations.SerializedName;
import io.github.wrqtec.content.safe.provider.baidu.BaiduProviderConstants;
import lombok.Data;

/**
 * 百度长视频审核结果拉取响应（video_censor/v1/video/pull）
 *
 * <p>处理中语义：ret="0" 且 {@code data.conclusionType} 缺省（官方文档未明示处理中
 * 报文形态，按此实现并记录于设计文档实现勘误）。审核失败时 data 携带
 * error_code/error_msg，conclusionType=4（官方文档 Pksy7jxr2，查阅 2026-11-11）。
 * frames[].frameUrl/frameThumbnailUrl 与 audios[].audioUrl 是签名 BOS 临时地址、
 * audios[].audioAuditResult.data[].auditData[].text 是语音转写原文——均属脱敏红线，
 * 不透传到统一结果
 *
 * @author L.David
 */
@Data
public class BaiduVideoPullResponse {

    /** 日志 ID */
    private Long logId;

    /** 返回码，"0" 成功 */
    private String ret;

    /** 提示信息 */
    private String msg;

    /** 数据体 */
    private Data data;

    /**
     * 拉取是否成功
     */
    public boolean isSuccess() {
        return BaiduProviderConstants.CODE_VIDEO_SUCCESS.equals(ret);
    }

    /**
     * 拉取数据体
     */
    @lombok.Data
    public static class Data {

        /** 供应商侧任务 ID */
        private Long taskId;

        /** 视频时长（秒） */
        private Long taskDuration;

        /** 提交时透传的扩展信息 */
        private String extraInfo;

        /** 错误码（仅审核失败返回） */
        @SerializedName("error_code")
        private Integer errorCode;

        /** 错误描述（仅审核失败返回） */
        @SerializedName("error_msg")
        private String errorMsg;

        /** 汇总结论：合规/不合规/疑似 */
        private String conclusion;

        /** 结论类型：1 合规、2 不合规、3 疑似、4 审核失败；处理中缺省 */
        private Integer conclusionType;

        /** 语音片段审核明细 */
        private List<Audio> audios;

        /** 截帧审核明细 */
        private List<Frame> frames;

        /**
         * 是否已产出结论（conclusionType 非空）
         */
        public boolean hasConclusion() {
            return conclusionType != null;
        }
    }

    /**
     * 截帧审核明细
     */
    @lombok.Data
    public static class Frame {

        /** 帧时间戳（秒） */
        @SerializedName("frameTimeStamp")
        private Long frameTimeStamp;

        /** 帧级结论：合规/不合规/疑似 */
        private String conclusion;

        /** 帧级结论类型（1/2/3） */
        private Integer conclusionType;

        /** 截帧图 URL（签名临时地址，不透传） */
        private String frameUrl;

        /** 缩略图 URL（签名临时地址，不透传） */
        private String frameThumbnailUrl;

        /** 命中明细 */
        private List<Detail> data;
    }

    /**
     * 截帧命中明细
     */
    @lombok.Data
    public static class Detail {

        /** 风险类型（如 43 涉黄新版） */
        private Integer type;

        /** 子类型 */
        private Integer subType;

        /** 风险描述 */
        private String msg;

        /** 置信度 0~1 */
        private Double probability;

        /** 命中数据集名称 */
        private String datasetName;

        /** 命中词明细 */
        private List<Hit> hits;
    }

    /**
     * 命中词明细
     */
    @lombok.Data
    public static class Hit {

        /** 命中词（用户内容，经 core EvidenceRedactor 统一脱敏） */
        private List<String> words;

        /** 置信度 0~1 */
        private Double probability;

        private String datasetName;
    }

    /**
     * 语音片段审核明细
     */
    @lombok.Data
    public static class Audio {

        /** 片段起始（毫秒） */
        private Integer startTime;

        /** 片段结束（毫秒） */
        private Integer endTime;

        /** 音频片段 URL（签名临时地址，不透传） */
        private String audioUrl;

        private AudioAuditResult audioAuditResult;
    }

    /**
     * 语音片段审核结果
     */
    @lombok.Data
    public static class AudioAuditResult {

        private String conclusion;

        private Integer conclusionType;

        /** 分段明细 */
        private List<AudioData> data;
    }

    /**
     * 语音分段明细
     */
    @lombok.Data
    public static class AudioData {

        private String conclusion;

        private Integer conclusionType;

        /** 分段起始（毫秒） */
        private Integer startTime;

        /** 分段结束（毫秒） */
        private Integer endTime;

        /** 语音转写原文（用户内容，不透传） */
        private String text;

        /** 命中明细 */
        private List<AudioDetail> auditData;
    }

    /**
     * 语音命中明细
     */
    @lombok.Data
    public static class AudioDetail {

        /** 风险类型 */
        private Integer type;

        /** 子类型 */
        private Integer subType;

        /** 风险描述 */
        private String msg;

        /** 命中词明细 */
        private List<AudioHit> hits;
    }

    /**
     * 语音命中词
     */
    @lombok.Data
    public static class AudioHit {

        /** 命中词（用户内容，经 core EvidenceRedactor 统一脱敏） */
        private List<String> words;

        /** 置信度 0~1 */
        private Double probability;
    }
}
