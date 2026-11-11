package io.github.wrqtec.content.safe.provider.baidu.internal;

import java.util.List;

import lombok.Data;

/**
 * 百度长视频审核结果推送报文（POST 到提交时指定的 noticeUrl）
 *
 * <p>结构（官方文档 8ksy7dc4b，查阅 2026-11-11）：
 * {@code {currentTime, appId, taskId, taskDuration, extId, extraInfo,
 * result:{errorCode, errorMsg, conclusion, conclusionType, audios, frames}}}。
 * frames/audios 与拉取响应同构，直接复用 {@link BaiduVideoPullResponse} 的模型。
 * taskId/extId 是任务关联键；extId 即提交时的业务侧 dataId
 *
 * @author L.David
 */
@Data
public class BaiduVideoCallbackPayload {

    /** 推送时间 */
    private String currentTime;

    /** 应用 ID */
    private String appId;

    /** 供应商侧任务 ID */
    private Long taskId;

    /** 视频时长（秒） */
    private Long taskDuration;

    /** 用户侧唯一标识（提交时 extId，即业务侧 dataId） */
    private String extId;

    /** 提交时透传的扩展信息 */
    private String extraInfo;

    /** 审核结果 */
    private Result result;

    /**
     * 审核结果（结构同拉取响应 data 的结论文部分）
     */
    @Data
    public static class Result {

        /** 错误码（仅审核失败返回） */
        private Integer errorCode;

        /** 错误描述（仅审核失败返回） */
        private String errorMsg;

        /** 汇总结论：合规/不合规/疑似 */
        private String conclusion;

        /** 结论类型：1 合规、2 不合规、3 疑似、4 审核失败 */
        private Integer conclusionType;

        private List<BaiduVideoPullResponse.Audio> audios;

        private List<BaiduVideoPullResponse.Frame> frames;
    }
}
