package io.github.wrqtec.content.safe.api.enums;

/**
 * 命中来源。
 *
 * @author L.David
 */
public enum HitSource {

    /** 机器模型识别 */
    MACHINE,
    /** 自定义名单（黑/白名单、词库）命中 */
    CUSTOM_LIST,
    /** 人工审核结论 */
    MANUAL
}
