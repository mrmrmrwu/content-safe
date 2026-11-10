package io.github.wrqtec.content.safe.provider.shumei.internal;

import java.util.List;

import io.github.wrqtec.content.safe.provider.shumei.ShumeiProviderConstants;

/**
 * 数美图片异步主动查询响应（/v4/image/query）
 *
 * <p>contents 与同步 image/v4 响应同构，按请求 requestIds 顺序对应；
 * 字段名与官方文档一致（智能图片识别产品 API 文档 V4，
 * github.com/ishumei/api-doc，2026-11-07 调研）。仅声明归一化所需字段。
 *
 * @author L.David
 */
public class ShumeiAsyncQueryResponse {

    /** 返回码：1100 成功 */
    private Integer code;

    /** 返回码描述 */
    private String message;

    /** 请求标识 */
    private String requestId;

    /** 查询结果列表（与同步 image/v4 响应同构，按下标对应 requestIds） */
    private List<ShumeiSyncResponse> contents;

    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getRequestId() {
        return requestId;
    }

    public List<ShumeiSyncResponse> getContents() {
        return contents;
    }

    /**
     * 业务成功（返回码 1100）
     */
    public boolean isSuccess() {
        return code != null && code == ShumeiProviderConstants.CODE_SUCCESS;
    }
}
