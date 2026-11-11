package io.github.wrqtec.content.safe.provider.tencent.internal;

import com.tencentcloudapi.ims.v20201229.models.CreateImageModerationAsyncTaskRequest;
import com.tencentcloudapi.ims.v20201229.models.CreateImageModerationAsyncTaskResponse;
import com.tencentcloudapi.ims.v20201229.models.ImageModerationRequest;
import com.tencentcloudapi.ims.v20201229.models.ImageModerationResponse;
import com.tencentcloudapi.tms.v20201229.models.TextModerationRequest;
import com.tencentcloudapi.tms.v20201229.models.TextModerationResponse;
import com.tencentcloudapi.vm.v20210922.models.CreateVideoModerationTaskRequest;
import com.tencentcloudapi.vm.v20210922.models.CreateVideoModerationTaskResponse;
import com.tencentcloudapi.vm.v20210922.models.DescribeTaskDetailRequest;
import com.tencentcloudapi.vm.v20210922.models.DescribeTaskDetailResponse;

/**
 * 腾讯云内容安全 SDK 调用接缝：唯一允许触碰 SDK 客户端的抽象，
 * 便于契约/单元测试替换实现。所有 SDK 类型不得出现在本包之外的签名中
 * （铁律 §3.4）
 *
 * @author L.David
 */
public interface TencentClientInvoker {

    /**
     * TMS 文本同步审核
     *
     * @param request SDK 请求
     * @return SDK 响应
     * @throws Exception SDK/网络异常（由 {@link TencentErrorMapper} 统一映射）
     */
    TextModerationResponse textModeration(TextModerationRequest request) throws Exception;

    /**
     * IMS 图片同步审核
     *
     * @param request SDK 请求
     * @return SDK 响应
     * @throws Exception SDK/网络异常（由 {@link TencentErrorMapper} 统一映射）
     */
    ImageModerationResponse imageModeration(ImageModerationRequest request) throws Exception;

    /**
     * IMS 图片异步审核任务创建（结果经 CallbackUrl 推送，无主动查询接口）
     *
     * @param request SDK 请求
     * @return SDK 响应
     * @throws Exception SDK/网络异常（由 {@link TencentErrorMapper} 统一映射）
     */
    CreateImageModerationAsyncTaskResponse createImageModerationAsyncTask(
            CreateImageModerationAsyncTaskRequest request) throws Exception;

    /**
     * VM 视频异步审核任务创建（支持轮询 DescribeTaskDetail 与可选回调）
     *
     * @param request SDK 请求
     * @return SDK 响应
     * @throws Exception SDK/网络异常（由 {@link TencentErrorMapper} 统一映射）
     */
    CreateVideoModerationTaskResponse createVideoModerationTask(
            CreateVideoModerationTaskRequest request) throws Exception;

    /**
     * VM 视频审核任务结果查询
     *
     * @param request SDK 请求
     * @return SDK 响应
     * @throws Exception SDK/网络异常（由 {@link TencentErrorMapper} 统一映射）
     */
    DescribeTaskDetailResponse describeTaskDetail(DescribeTaskDetailRequest request) throws Exception;
}
