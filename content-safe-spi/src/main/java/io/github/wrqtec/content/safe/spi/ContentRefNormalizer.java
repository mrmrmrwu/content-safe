package io.github.wrqtec.content.safe.spi;

import io.github.wrqtec.content.safe.api.ContentRef;

/**
 * 内容引用归一化。用于将 provider 特定的 content 表达转换为统一 {@link ContentRef}，
 * 例如：把 byte[] 转 base64、把对象存储 key 转换为带签名的 URL
 *
 * <p>输入治理职责边界：公共形状校验（内容非空、URL scheme/host 白名单）由 core 编排层
 * 在路由前统一执行一次；供应商特有的大小/像素/格式限制由各 provider 自行预检。
 * 本接口不是输入治理入口，而是 provider 侧到统一模型的<b>表达转换</b>预留扩展点。
 * 另注：签名 URL 由云服务商服务端拉取，core 与 provider 均不主动请求用户 URL
 *
 * @author L.David
 * @deprecated 自 1.0.0 发布以来无任何调用方：输入治理由 core 编排层内聚执行，
 *     provider 侧表达转换暂无真实插拔需求（YAGNI）。已在 1.1.0 标记移除意向，
 *     计划 2.0.0 删除；如有依赖请反馈 issue 说明场景
 */
@Deprecated(since = "1.1.0", forRemoval = true)
public interface ContentRefNormalizer {

    /**
     * 供应商 key
     *
     * @return 供应商 key
     */
    String provider();

    /**
     * 归一化
     *
     * @param ref 原始内容引用，不得为 null
     * @return 规范化后的统一内容引用
     */
    ContentRef normalize(ContentRef ref);
}
