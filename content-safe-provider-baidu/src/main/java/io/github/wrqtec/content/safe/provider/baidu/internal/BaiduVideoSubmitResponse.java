package io.github.wrqtec.content.safe.provider.baidu.internal;

import io.github.wrqtec.content.safe.provider.baidu.BaiduProviderConstants;
import lombok.Data;

/**
 * 百度长视频审核提交响应（video_censor/v1/video/submit）
 *
 * <p>成功示例：{@code {"logId":123,"msg":"success","ret":"0","data":{"taskId":456,"queueSize":1}}}；
 * 失败（如 appId+extId 组合重复提交）返回 {@code ret="282005"} 等
 * （官方文档 jksy7j3jv/Pksy7jxr2，查阅 2026-11-11）
 *
 * @author L.David
 */
@Data
public class BaiduVideoSubmitResponse {

    /** 日志 ID */
    private Long logId;

    /** 提示信息，"success" 表示成功 */
    private String msg;

    /** 返回码，"0" 成功；失败时为错误码字符串（如 282005） */
    private String ret;

    /** 数据体，失败时可能缺省 */
    private Data data;

    /**
     * 提交是否成功
     */
    public boolean isSuccess() {
        return BaiduProviderConstants.CODE_VIDEO_SUCCESS.equals(ret);
    }

    /**
     * 提交数据体
     */
    @lombok.Data
    public static class Data {

        /** 供应商侧任务 ID */
        private Long taskId;

        /** 当前任务排队数量 */
        private Integer queueSize;
    }
}
