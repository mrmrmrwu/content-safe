package io.github.wrqtec.content.safe.provider.aliyun.internal;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link JsonParams} 单元测试：转义与空值处理
 *
 * @author L.David
 */
class JsonParamsTest {

    @Test
    void flatParamsToJson() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("content", "hello \"world\" \\ 换行\nend");
        params.put("dataId", "d-1");
        assertThat(JsonParams.toJson(params))
                .isEqualTo("{\"content\":\"hello \\\"world\\\" \\\\ 换行\\nend\",\"dataId\":\"d-1\"}");
    }

    @Test
    void nullValueSkipped() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("a", null);
        params.put("b", "1");
        assertThat(JsonParams.toJson(params)).isEqualTo("{\"b\":\"1\"}");
    }

    @Test
    void emptyAndNullParams() {
        assertThat(JsonParams.toJson(Map.of())).isEqualTo("{}");
        assertThat(JsonParams.toJson(null)).isEqualTo("{}");
    }
}
