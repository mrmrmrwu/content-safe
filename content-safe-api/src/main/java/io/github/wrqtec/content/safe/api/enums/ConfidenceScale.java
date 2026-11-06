package io.github.wrqtec.content.safe.api.enums;

/**
 * 置信度取值范围，避免不同供应商分制混用
 *
 * @author L.David
 */
public enum ConfidenceScale {

    /** 0.0 ~ 1.0 */
    ZERO_TO_ONE,

    /** 0 ~ 100 */
    PERCENTAGE
}
