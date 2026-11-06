package io.github.wrqtec.content.safe.provider.baidu.internal;

import java.util.List;

import com.google.gson.annotations.SerializedName;

/**
 * 百度内容审核统一响应模型（文本/图像 v2 同构）
 *
 * @author L.David
 */
public class BaiduCensorResponse {

    /** 请求唯一日志 ID */
    @SerializedName("log_id")
    private Long logId;

    /** 审核结论（文本描述） */
    private String conclusion;

    /** 结论类型：1=合规 2=不合规 3=疑似 4=审核失败 */
    private Integer conclusionType;

    /** 命中明细（未命中为空） */
    private List<Data> data;

    /** 错误码（请求成功时为空） */
    @SerializedName("error_code")
    private Integer errorCode;

    /** 错误描述 */
    @SerializedName("error_msg")
    private String errorMsg;

    public Long getLogId() {
        return logId;
    }

    public String getConclusion() {
        return conclusion;
    }

    public Integer getConclusionType() {
        return conclusionType;
    }

    public List<Data> getData() {
        return data;
    }

    public Integer getErrorCode() {
        return errorCode;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    /**
     * 是否业务成功（无 error_code）
     */
    public boolean isSuccess() {
        return errorCode == null;
    }

    /**
     * 命中明细
     */
    public static class Data {

        /** 审核类型（如 1=文本违规等） */
        private Integer type;

        /** 审核子类型 */
        private Integer subType;

        /** 审核结果描述 */
        private String msg;

        /** 置信度 0~1 */
        private Double probability;

        /** 违规关键词集合 */
        private List<Hit> hits;

        public Integer getType() {
            return type;
        }

        public Integer getSubType() {
            return subType;
        }

        public String getMsg() {
            return msg;
        }

        public Double getProbability() {
            return probability;
        }

        public List<Hit> getHits() {
            return hits;
        }
    }

    /**
     * 关键词命中
     */
    public static class Hit {

        /** 命中词 */
        private List<String> words;

        public List<String> getWords() {
            return words;
        }
    }
}
