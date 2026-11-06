package io.github.wrqtec.content.safe.api.enums;

/**
 * 人审终态决议：把机审 {@code REVIEW} 结论改判为业务终态
 *
 * @author L.David
 */
public enum ReviewDecision {

    /** 人审通过：机审存疑内容确认无风险 */
    APPROVE,

    /** 人审驳回：机审存疑内容确认违规 */
    REJECT
}
