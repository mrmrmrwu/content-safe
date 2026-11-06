package io.github.wrqtec.content.safe.provider.shumei.internal;

import java.util.List;

import com.google.gson.JsonObject;

/**
 * 数美 v4 同步检测响应（text/v4 与 image/v4 同构）
 *
 * <p>字段名与官方文档一致（智能文本/图片识别产品 API 文档 V4，
 * github.com/ishumei/api-doc，2026-11-06 调研）。仅声明归一化所需字段；
 * auxInfo（filteredText/contactResult 等）与 riskSegments 属用户内容原文，整体不建模、不透传。
 *
 * @author L.David
 */
public class ShumeiSyncResponse {

    /** 返回码：1100 成功，1901 QPS 超限，1902 参数不合法，1903 服务失败，
     * 1905 文本字数超限，1911 图片下载失败（仅图片），9101 无权限 */
    private Integer code;

    /** 返回码描述 */
    private String message;

    /** 请求标识 */
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

    /** 映射后风险详情（matchedLists/riskSource/faces 等，按需提取） */
    private JsonObject riskDetail;

    /** 全部风险标签列表 */
    private List<LabelEntry> allLabels;

    /** 是否为最终审核结果：1 最终，0 非最终（机审后还需数美人审回传） */
    private Integer finalResult;

    /** 当前结果类型：0 机审，1 人审 */
    private Integer resultType;

    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
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

    public JsonObject getRiskDetail() {
        return riskDetail;
    }

    public List<LabelEntry> getAllLabels() {
        return allLabels;
    }

    public Integer getFinalResult() {
        return finalResult;
    }

    public Integer getResultType() {
        return resultType;
    }

    /**
     * 业务成功（返回码 1100）
     */
    public boolean isSuccess() {
        return code != null && code == 1100;
    }

    /**
     * allLabels 单项
     */
    public static class LabelEntry {

        /** 置信度 0~1 */
        private Double probability;

        /** 该标签项自身的处置建议：PASS / REVIEW / REJECT */
        private String riskLevel;

        /** 一级风险标签 */
        private String riskLabel1;

        /** 二级风险标签 */
        private String riskLabel2;

        /** 三级风险标签 */
        private String riskLabel3;

        /** 风险原因 */
        private String riskDescription;

        /** 该标签项的风险详情 */
        private JsonObject riskDetail;

        public Double getProbability() {
            return probability;
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

        public JsonObject getRiskDetail() {
            return riskDetail;
        }
    }
}
