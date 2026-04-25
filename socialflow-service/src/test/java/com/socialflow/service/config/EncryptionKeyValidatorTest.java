package com.socialflow.service.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link EncryptionKeyValidator} 的单元测试。
 *
 * <p>这里的"启动失败"是指在容器启动期 {@code @PostConstruct} 抛出
 * {@link IllegalStateException}，导致 ApplicationContext 拒绝就绪 ——
 * 比让用户用弱密钥跑起来再到运行期才出问题安全得多。</p>
 */
class EncryptionKeyValidatorTest {

    private EncryptionKeyValidator validator(String key) {
        EncryptionKeyValidator v = new EncryptionKeyValidator();
        ReflectionTestUtils.setField(v, "encryptionKey", key);
        return v;
    }

    @Test
    @DisplayName("空字符串 / null / 空白 → 启动失败")
    void rejects_blankKey() {
        assertThatThrownBy(() -> validator(null).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未配置");
        assertThatThrownBy(() -> validator("").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未配置");
        assertThatThrownBy(() -> validator("   ").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未配置");
    }

    @Test
    @DisplayName("长度不是 64 → 启动失败（提示当前长度）")
    void rejects_wrongLength() {
        String tooShort = "abcdef0123456789"; // 16 chars
        assertThatThrownBy(() -> validator(tooShort).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("当前长度：" + tooShort.length());

        String tooLong = "a".repeat(128);
        assertThatThrownBy(() -> validator(tooLong).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("64");
    }

    @Test
    @DisplayName("长度对但包含非十六进制字符 → 启动失败")
    void rejects_nonHexChars() {
        String fakeHex = "g".repeat(64); // 全 g，不是 hex
        assertThatThrownBy(() -> validator(fakeHex).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("64 位十六进制");
    }

    @Test
    @DisplayName("命中已知弱密钥黑名单（如示例值 0123456789...）→ 启动失败")
    void rejects_knownWeakKey_exampleValue() {
        String exampleKey = "***REDACTED-EXAMPLE-AES-KEY***";
        assertThatThrownBy(() -> validator(exampleKey).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("弱密钥黑名单");
    }

    @Test
    @DisplayName("命中已知弱密钥黑名单（全 0）→ 启动失败")
    void rejects_knownWeakKey_allZeros() {
        String allZeros = "0".repeat(64);
        assertThatThrownBy(() -> validator(allZeros).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("弱密钥黑名单");
    }

    @Test
    @DisplayName("命中已知弱密钥黑名单（全 f）→ 启动失败")
    void rejects_knownWeakKey_allFs() {
        String allFs = "f".repeat(64);
        assertThatThrownBy(() -> validator(allFs).validate())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("合法的强密钥（64 位 hex 且不在黑名单）→ 通过")
    void accepts_validKey() {
        // openssl rand -hex 32 风格的强密钥示例
        String strong = "9f3a4e2b8c1d5e7a6b9c0d2e4f1a3b5c7d9e1f0a2b4c6d8e0f1a3b5c7d9e0f1a";
        assertThatNoException().isThrownBy(() -> validator(strong).validate());
    }

    @Test
    @DisplayName("大小写混合的合法 hex 也接受（内部统一 toLowerCase 后判断）")
    void accepts_mixedCaseHex() {
        String mixed = "9F3a4E2B8c1D5e7A6b9C0d2E4f1A3b5C7d9E1f0A2b4C6d8E0f1A3b5C7d9E0f1A";
        assertThatNoException().isThrownBy(() -> validator(mixed).validate());
    }

    @Test
    @DisplayName("校验通过后，validator 持有的 key 不被改写（lowercase 仅在内部判断时使用）")
    void doesNotMutateOriginalKey() {
        String original = "9F3a4E2B8c1D5e7A6b9C0d2E4f1A3b5C7d9E1f0A2b4C6d8E0f1A3b5C7d9E0f1A";
        EncryptionKeyValidator v = validator(original);

        assertThatNoException().isThrownBy(v::validate);

        String stored = (String) ReflectionTestUtils.getField(v, "encryptionKey");
        assertThat(stored).isEqualTo(original);
    }
}
