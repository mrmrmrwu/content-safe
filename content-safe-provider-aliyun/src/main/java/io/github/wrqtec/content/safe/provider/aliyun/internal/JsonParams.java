package io.github.wrqtec.content.safe.provider.aliyun.internal;

import java.util.Map;

/**
 * 极简 JSON 序列化：仅用于组装 serviceParameters（扁平的 String 键值对），
 * 避免为此引入 JSON 依赖（CLAUDE.md §3：新依赖先问是否进入使用方编译期传递依赖）
 *
 * @author L.David
 */
final class JsonParams {

    private JsonParams() {
    }

    /**
     * 将扁平 String 键值对序列化为 JSON 对象字符串；null 值跳过
     *
     * @param params 参数表
     * @return JSON 字符串
     */
    static String toJson(Map<String, String> params) {
        StringBuilder sb = new StringBuilder(64);
        sb.append('{');
        if (params == null || params.isEmpty()) {
            return sb.append('}').toString();
        }
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            escape(entry.getKey(), sb);
            sb.append(':');
            escape(entry.getValue(), sb);
        }
        sb.append('}');
        return sb.toString();
    }

    private static void escape(String value, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
