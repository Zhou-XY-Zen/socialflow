package com.socialflow.service.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 启动时校验 AES-256-GCM 主加密密钥的合法性。
 *
 * 校验失败会让 Spring 容器启动失败，避免应用以"看似正常"的姿态运行却携带弱密钥 ——
 * 后果是用户存储的 LLM API Key、Git 凭证等敏感数据可被任何拿到代码仓库的人解密。
 *
 * 校验规则：
 *   1. 不能为空
 *   2. 必须是 64 位十六进制字符串（= 32 字节 = 256 位）
 *   3. 不能命中已知弱密钥 / 示例密钥黑名单
 *
 * 生成强密钥：openssl rand -hex 32
 */
@Slf4j
@Component
public class EncryptionKeyValidator {

    /**
     * 已知弱密钥 / 示例密钥黑名单 —— 这些值曾出现在 .env.example、application.yml 等仓库文件里，
     * 任何能访问 git history 的人都拿得到，绝不允许用作生产密钥。
     */
    private static final Set<String> KNOWN_WEAK_KEYS = Set.of(
            // 此前 .env.example / application.yml 长期使用的示例 hex 串。
            // 用 String.join 拆分构造，避免被 git filter-repo 这类工具误判为秘钥再次替换。
            String.join("", "0123456789", "abcdef", "0123456789", "abcdef",
                            "0123456789", "abcdef", "0123456789", "abcdef"),
            "0000000000000000000000000000000000000000000000000000000000000000",
            "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
    );

    @Value("${socialflow.ai.encryption-key:}")
    private String encryptionKey;

    /**
     * 兼容开关：true 时允许使用黑名单内的"已知弱密钥"启动（仅长度/格式仍校验）。
     *
     * <p>设这个开关是为了兜住"老服务器一直用 example key 加密了大量数据"这种场景：
     * 直接换密钥会导致老数据无法解密，必须先迁移再换。在迁移完成前，需要服务能继续启动。</p>
     *
     * <p>开关打开时启动日志会打 ERROR 警告，提醒尽快做密钥迁移。默认 false（严格）。</p>
     */
    @Value("${socialflow.ai.encryption-key-validation.allow-known-weak:false}")
    private boolean allowKnownWeak;

    @PostConstruct
    public void validate() {
        if (encryptionKey == null || encryptionKey.isBlank()) {
            throw new IllegalStateException(
                    "socialflow.ai.encryption-key 未配置。\n" +
                    "  - 本地开发：在 IDEA Run Configuration 的 Environment 设置 AI_ENCRYPTION_KEY=...\n" +
                    "  - 生产环境：通过 Nacos / Vault / 服务器 .env 文件注入\n" +
                    "  - 生成强密钥：openssl rand -hex 32");
        }

        String normalized = encryptionKey.trim().toLowerCase();
        if (normalized.length() != 64 || !normalized.matches("^[0-9a-f]{64}$")) {
            throw new IllegalStateException(
                    "socialflow.ai.encryption-key 必须是 64 位十六进制字符串（= 32 字节 AES-256 密钥）。\n" +
                    "  - 当前长度：" + encryptionKey.length() + "\n" +
                    "  - 生成强密钥：openssl rand -hex 32");
        }

        if (KNOWN_WEAK_KEYS.contains(normalized)) {
            if (allowKnownWeak) {
                // 用大字号 ERROR 日志强提醒：当前是兜底模式，该尽快迁移
                log.error("================================================================");
                log.error("⚠ [EncryptionKey] 当前使用已知弱密钥（黑名单内）启动！");
                log.error("⚠ 仅因为 socialflow.ai.encryption-key-validation.allow-known-weak=true");
                log.error("⚠ 该模式仅为兼容历史加密数据，不应长期使用。");
                log.error("⚠ 迁移步骤：");
                log.error("⚠   1) 用旧 key 解密 user_api_key / repo_auth_credential 等表");
                log.error("⚠   2) openssl rand -hex 32 生成新强密钥");
                log.error("⚠   3) 用新 key 重新加密并写回");
                log.error("⚠   4) 把 allow-known-weak 改回 false");
                log.error("================================================================");
                return;
            }
            throw new IllegalStateException(
                    "socialflow.ai.encryption-key 命中已知弱密钥黑名单（如 .env.example 中的示例值）。\n" +
                    "  这些值曾在公开仓库或文档中出现，使用它们等于不加密。\n" +
                    "  请立即用强密钥替换：openssl rand -hex 32\n" +
                    "  历史数据兼容场景可临时设置 socialflow.ai.encryption-key-validation.allow-known-weak=true，\n" +
                    "  但仅作为迁移过渡，不要长期保留。");
        }

        // 仅打印密钥指纹（前 8 位），不记录完整密钥
        log.info("[EncryptionKey] 主加密密钥校验通过，指纹: {}…", normalized.substring(0, 8));
    }
}
