package io.github.wrqtec.content.safe.spi;

import io.github.wrqtec.content.safe.api.ContentRef;

/**
 * 内容引用归一化。用于将 provider 特定的 content 表达转换为统一 {@link ContentRef}，
 * 例如：把 byte[] 转 base64、把对象存储 key 转换为带签名的 URL
 *
 * <p>输入治理职责边界：公共形状校验（内容非空、URL scheme/host 白名单）由 core 编排层
 * 在路由前统一执行一次；供应商特有的大小/像素/格式限制由各 provider 自行预检。
 * 本接口与输入治理无关，也不是任何已接入的扩展点
 *
 * @author L.David
 * @deprecated 自 1.0.0 发布以来全仓库无任何调用方：core 编排层与全部 provider 实现
 *     均不读取本接口的实现 Bean，<b>实现它不会产生任何效果</b>，请勿接入。
 *     公共形状校验内聚于 core，provider 侧表达转换暂无真实插拔需求（YAGNI）。
 *     因 1.0.0 已发布无法物理删除（二进制兼容门禁），计划 2.0.0 移除；
 *     如有真实场景需要请提 issue 说明，超过零个真实需求则重新设计而非复活本接口
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
