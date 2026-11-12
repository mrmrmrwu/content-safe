package io.github.wrqtec.content.safe.provider.tencent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * {@link TencentProviderProperties} 校验规则测试。
 * validate() 只做结构校验（当前为空实现），凭证存在性由装配链
 * resolveCredentials 统一 fail-fast——env / 自定义 CredentialsProvider
 * 方式下 yml 无凭证是合法配置，不能在 validate() 被误拒
 *
 * @author L.David
 */
class TencentProviderPropertiesTest {

    @Test
    void validateNeverRejectsCredentialFreeConfig() {
        // enabled + 无 yml 凭证：合法（凭证可来自 env 或自定义 CredentialsProvider）
        TencentProviderProperties properties = new TencentProviderProperties();
        properties.setEnabled(true);
        assertThatCode(properties::validate).doesNotThrowAnyException();
    }
}
