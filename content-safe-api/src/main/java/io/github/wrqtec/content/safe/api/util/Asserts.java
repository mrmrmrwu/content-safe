package io.github.wrqtec.content.safe.api.util;

import io.github.wrqtec.content.safe.api.exception.InvalidRequestException;

/**
 * 对外 API 入参校验，校验失败抛出 {@link InvalidRequestException}
 *
 * @author L.David
 */
public final class Asserts {

    private Asserts() {
    }

    /**
     * 校验参数非空
     *
     * @param value 待校验值
     * @param name  参数名，用于异常信息
     * @param <T>   参数类型
     * @return 原值
     * @throws InvalidRequestException 值为 {@code null} 时抛出
     */
    public static <T> T notNull(T value, String name) {
        if (value == null) {
            throw new InvalidRequestException(name + " must not be null");
        }
        return value;
    }

    /**
     * 校验字符串非空白
     *
     * @param value 待校验字符串
     * @param name  参数名，用于异常信息
     * @return 原字符串
     * @throws InvalidRequestException 值为 {@code null} 或空白时抛出
     */
    public static String notBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException(name + " must not be blank");
        }
        return value;
    }
}
