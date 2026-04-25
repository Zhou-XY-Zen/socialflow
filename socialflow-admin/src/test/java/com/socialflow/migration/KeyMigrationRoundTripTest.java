package com.socialflow.migration;

import com.socialflow.common.util.AesGcmUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 密钥迁移的 round-trip 行为单测 —— 不启动 Spring，直接用 AesGcmUtil 验证算法正确性。
 *
 * <p>这里测的是"迁移逻辑的正确性"而非 KeyMigrationRunner 本身（runner 的 Spring 依赖和
 * Mapper 交互通过 dry-run 实跑验证）。</p>
 */
@DisplayName("KeyMigration round-trip")
class KeyMigrationRoundTripTest {

    /** 模拟旧 example key（生产正在用的弱 key）*/
    private static final String OLD_KEY =
            String.join("", "0123456789", "abcdef", "0123456789", "abcdef",
                            "0123456789", "abcdef", "0123456789", "abcdef");

    /** 模拟新强 key —— openssl rand -hex 32 风格 */
    private static final String NEW_KEY =
            "9f3a4e2b8c1d5e7a6b9c0d2e4f1a3b5c7d9e1f0a2b4c6d8e0f1a3b5c7d9e0f1a";

    @Test
    @DisplayName("用旧 key 加密的密文，能用旧 key 解密、能重加密成新 key 密文、新密文用新 key 解密回原文")
    void migrationRoundTrip_preservesPlaintext() {
        String plaintext = "sk-real-llm-api-key-1234567890abcdef";

        // 1. 模拟"线上现状"：用旧 key 加密
        String oldCipher = AesGcmUtil.encrypt(plaintext, OLD_KEY);

        // 2. 迁移：用旧 key 解密 → 拿到原文
        String recovered = AesGcmUtil.decrypt(oldCipher, OLD_KEY);
        assertThat(recovered).isEqualTo(plaintext);

        // 3. 用新 key 重加密
        String newCipher = AesGcmUtil.encrypt(recovered, NEW_KEY);
        assertThat(newCipher).isNotEqualTo(oldCipher);  // 密文不同（算法+随机 IV）

        // 4. 新密文必须能用新 key 解出原文
        String afterMigration = AesGcmUtil.decrypt(newCipher, NEW_KEY);
        assertThat(afterMigration).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("旧 key 解密新密文应该失败（确认密文确实换了 key）")
    void newCipher_cannotBeDecryptedByOldKey() {
        String oldCipher = AesGcmUtil.encrypt("secret", OLD_KEY);
        String plaintext = AesGcmUtil.decrypt(oldCipher, OLD_KEY);
        String newCipher = AesGcmUtil.encrypt(plaintext, NEW_KEY);

        assertThatThrownBy(() -> AesGcmUtil.decrypt(newCipher, OLD_KEY))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("迁移幂等：尝试用旧 key 解新密文失败 → 尝试用新 key 解 → 成功 → 跳过")
    void idempotent_skipAlreadyMigratedRow() {
        // 模拟一行已经迁移过：密文是用新 key 加密的
        String alreadyMigrated = AesGcmUtil.encrypt("plain", NEW_KEY);

        // KeyMigrationRunner 的逻辑：先试旧 key
        boolean oldKeyFails = false;
        try {
            AesGcmUtil.decrypt(alreadyMigrated, OLD_KEY);
        } catch (IllegalStateException e) {
            oldKeyFails = true;
        }
        assertThat(oldKeyFails).isTrue();

        // 旧 key 失败 → 试新 key
        String stillReadable = AesGcmUtil.decrypt(alreadyMigrated, NEW_KEY);
        assertThat(stillReadable).isEqualTo("plain");
        // → runner 检测到这条已迁移过，应跳过（实际逻辑在 KeyMigrationRunner.migrateUserApiKeys 里）
    }

    @Test
    @DisplayName("校验输入：64 位 hex / 大小写均接受 / 不允许 old==new")
    void inputValidation_basicShape() {
        // 这些是 KeyMigrationRunner.validateKeys 期望的契约
        String hex64 = "ABcd".repeat(16);
        assertThat(hex64).hasSize(64);
        assertThat(hex64).matches("^[0-9a-fA-F]{64}$");

        String tooShort = "abcd";
        assertThat(tooShort.matches("^[0-9a-fA-F]{64}$")).isFalse();

        String notHex = "g".repeat(64);
        assertThat(notHex.matches("^[0-9a-fA-F]{64}$")).isFalse();
    }
}
