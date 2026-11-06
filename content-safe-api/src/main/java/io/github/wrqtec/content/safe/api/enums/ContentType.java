package io.github.wrqtec.content.safe.api.enums;

/**
 * 内容类型。v1 仅承诺 {@link #TEXT} 与 {@link #IMAGE}，其余为预留抽象。
 *
 * @author L.David
 */
public enum ContentType {

    /** 纯文本 */
    TEXT,
    /** 图片 */
    IMAGE,
    /** 音频（预留，v1 未实现） */
    AUDIO,
    /** 视频（预留，v1 未实现） */
    VIDEO,
    /** 文件（预留，v1 未实现） */
    FILE
}
