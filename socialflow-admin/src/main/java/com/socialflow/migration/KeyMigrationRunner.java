package com.socialflow.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialflow.common.util.AesGcmUtil;
import com.socialflow.dao.mapper.RepoAuthCredentialMapper;
import com.socialflow.dao.mapper.UserApiKeyMapper;
import com.socialflow.model.entity.RepoAuthCredential;
import com.socialflow.model.entity.UserApiKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AES 主加密密钥迁移工具 —— 把已用旧 key 加密的 user_api_key / repo_auth_credential
 * 数据解密后用新 key 重加密回写。
 *
 * <p><b>背景：</b>生产环境长期使用 example hex（黑名单内）作为 socialflow.ai.encryption-key，
 * 因此用户 API Key 和 Git 凭证的密文都是用这个弱 key 加密的。直接换强 key 会导致历史
 * 数据无法解密；本脚本完成"老 key 解密 → 新 key 加密"的搬运，搬完后即可把
 * {@code socialflow.ai.encryption-key-validation.allow-known-weak} 改回 false 关闭兼容模式。</p>
 *
 * <p><b>启用方式：</b>本类用 {@code @Profile("migrate-keys")} 守门，平时不会被实例化。
 * 只有在用以下命令显式开启 profile 时才会跑：</p>
 *
 * <pre>{@code
 * java -jar socialflow.jar \
 *   --spring.profiles.active=prod,migrate-keys \
 *   --socialflow.key-migration.old-key=<旧 hex key> \
 *   --socialflow.key-migration.new-key=<新 hex key> \
 *   --socialflow.key-migration.dry-run=true \
 *   --socialflow.key-migration.backup-dir=/opt/socialflow/backups/keys
 * }</pre>
 *
 * <p><b>执行流程：</b></p>
 * <ol>
 *   <li>校验入参：old/new 都必须是 64 位 hex，且不能相同</li>
 *   <li><b>备份</b>所有待迁移行的当前密文到 {@code backup-dir}/key-migration-&lt;ts&gt;.json，
 *       这是回滚的最后保险</li>
 *   <li>遍历 user_api_key：解密 → 重加密 → 更新（每行单事务）</li>
 *   <li>遍历 repo_auth_credential：同上</li>
 *   <li>已用新 key 加密过的行（重跑场景）会被识别并跳过，保证脚本幂等</li>
 *   <li>跑完调 {@code System.exit(0)} 退出 JVM —— 不让 Spring 继续起 Web 服务</li>
 * </ol>
 *
 * <p><b>安全开关：</b></p>
 * <ul>
 *   <li>{@code dry-run=true}（默认）：只打印将要做什么，不写 DB</li>
 *   <li>确认 dry-run 输出无误后，再以 {@code dry-run=false} 重跑</li>
 *   <li>backup-dir 写入失败立即终止，不允许"无备份"地覆盖密文</li>
 * </ul>
 *
 * <p><b>幂等性保证：</b>对每行先尝试用旧 key 解密；失败则尝试用新 key 解密；
 * 若新 key 能解，说明这条已迁移过，跳过；都不能解则报错（数据可能损坏，需人工介入）。</p>
 */
@Slf4j
@Component
@Profile("migrate-keys")
@RequiredArgsConstructor
public class KeyMigrationRunner implements CommandLineRunner {

    private final UserApiKeyMapper userApiKeyMapper;
    private final RepoAuthCredentialMapper credentialMapper;

    @Value("${socialflow.key-migration.old-key:}")
    private String oldKey;

    @Value("${socialflow.key-migration.new-key:}")
    private String newKey;

    @Value("${socialflow.key-migration.dry-run:true}")
    private boolean dryRun;

    @Value("${socialflow.key-migration.backup-dir:/tmp}")
    private String backupDir;

    @Override
    public void run(String... args) {
        try {
            validateKeys();
            log.warn("================================================================");
            log.warn("⚠ 密钥迁移开始");
            log.warn("⚠   dry-run        = {}", dryRun);
            log.warn("⚠   old-key 指纹    = {}…", oldKey.substring(0, 8));
            log.warn("⚠   new-key 指纹    = {}…", newKey.substring(0, 8));
            log.warn("⚠   backup-dir     = {}", backupDir);
            log.warn("================================================================");

            int apiKeyCount = migrateUserApiKeys();
            int credCount = migrateRepoCredentials();

            log.warn("================================================================");
            log.warn("✓ 密钥迁移完成");
            log.warn("  user_api_key:           {} 条已迁移", apiKeyCount);
            log.warn("  repo_auth_credential:   {} 条已迁移", credCount);
            if (dryRun) {
                log.warn("  ⚠ DRY RUN 模式 —— DB 没有被实际写入");
                log.warn("  确认无误后用 --socialflow.key-migration.dry-run=false 重跑");
            } else {
                log.warn("  ✓ DB 已实际更新");
                log.warn("  下一步：把 socialflow.ai.encryption-key-validation.allow-known-weak 改回 false");
                log.warn("  并把 .env 里的 AI_ENCRYPTION_KEY 替换为新 key");
            }
            log.warn("================================================================");

            // 退出 JVM —— 防止 Spring 接着启 Web 服务（迁移工具不该承担正常应用职责）
            System.exit(0);
        } catch (Exception e) {
            log.error("密钥迁移失败：{}", e.getMessage(), e);
            System.exit(2);
        }
    }

    private void validateKeys() {
        if (oldKey == null || oldKey.length() != 64 || !oldKey.matches("^[0-9a-fA-F]{64}$")) {
            throw new IllegalStateException("--socialflow.key-migration.old-key 缺失或不是 64 位 hex");
        }
        if (newKey == null || newKey.length() != 64 || !newKey.matches("^[0-9a-fA-F]{64}$")) {
            throw new IllegalStateException("--socialflow.key-migration.new-key 缺失或不是 64 位 hex");
        }
        if (oldKey.equalsIgnoreCase(newKey)) {
            throw new IllegalStateException("old-key 与 new-key 相同，无需迁移");
        }
    }

    /**
     * 备份所有待迁移行的当前密文到 JSON 文件，回滚的最后保险。
     * 任何写失败都终止程序——不允许在没有备份的情况下覆盖密文。
     */
    private Path writeBackup(String tableName, List<Map<String, Object>> rows) throws Exception {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path dir = Paths.get(backupDir);
        Files.createDirectories(dir);
        Path file = dir.resolve("key-migration-" + tableName + "-" + ts + ".json");
        ObjectMapper mapper = new ObjectMapper();
        Files.writeString(file,
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(rows),
                StandardCharsets.UTF_8);
        log.info("[backup] {} 行 → {}", rows.size(), file);
        return file;
    }

    // ============================================================
    // user_api_key
    // ============================================================
    @Transactional(rollbackFor = Exception.class)
    protected int migrateUserApiKeys() throws Exception {
        List<UserApiKey> all = userApiKeyMapper.selectList(null);
        log.info("[user_api_key] 共 {} 条记录", all.size());
        if (all.isEmpty()) return 0;

        // 先备份
        List<Map<String, Object>> backup = new ArrayList<>();
        for (UserApiKey r : all) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("userId", r.getUserId());
            m.put("provider", r.getProvider());
            m.put("apiKeyEncrypted", r.getApiKeyEncrypted());
            backup.add(m);
        }
        writeBackup("user_api_key", backup);

        int migrated = 0, skipped = 0;
        for (UserApiKey row : all) {
            String currentCipher = row.getApiKeyEncrypted();
            String plaintext;
            try {
                plaintext = AesGcmUtil.decrypt(currentCipher, oldKey);
            } catch (RuntimeException tryOldFailed) {
                // 旧 key 解密不了 —— 是不是已经被新 key 加密过了？
                try {
                    AesGcmUtil.decrypt(currentCipher, newKey);
                    log.info("[user_api_key id={}] 已是新 key 密文，跳过", row.getId());
                    skipped++;
                    continue;
                } catch (RuntimeException tryNewFailed) {
                    log.error("[user_api_key id={}] 旧/新 key 都无法解密 —— 数据可能损坏，请人工检查",
                            row.getId());
                    throw tryOldFailed;
                }
            }
            String newCipher = AesGcmUtil.encrypt(plaintext, newKey);
            if (!dryRun) {
                UserApiKey upd = new UserApiKey();
                upd.setId(row.getId());
                upd.setApiKeyEncrypted(newCipher);
                userApiKeyMapper.updateById(upd);
            }
            migrated++;
        }
        log.info("[user_api_key] 迁移 {} 条，跳过（已迁移）{} 条", migrated, skipped);
        return migrated;
    }

    // ============================================================
    // repo_auth_credential
    // ============================================================
    @Transactional(rollbackFor = Exception.class)
    protected int migrateRepoCredentials() throws Exception {
        List<RepoAuthCredential> all = credentialMapper.selectList(null);
        log.info("[repo_auth_credential] 共 {} 条记录", all.size());
        if (all.isEmpty()) return 0;

        List<Map<String, Object>> backup = new ArrayList<>();
        for (RepoAuthCredential r : all) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("userId", r.getUserId());
            m.put("gitHost", r.getGitHost());
            m.put("tokenEncrypted", r.getTokenEncrypted());
            backup.add(m);
        }
        writeBackup("repo_auth_credential", backup);

        int migrated = 0, skipped = 0;
        for (RepoAuthCredential row : all) {
            String currentCipher = row.getTokenEncrypted();
            String plaintext;
            try {
                plaintext = AesGcmUtil.decrypt(currentCipher, oldKey);
            } catch (RuntimeException tryOldFailed) {
                try {
                    AesGcmUtil.decrypt(currentCipher, newKey);
                    log.info("[repo_auth_credential id={}] 已是新 key 密文，跳过", row.getId());
                    skipped++;
                    continue;
                } catch (RuntimeException tryNewFailed) {
                    log.error("[repo_auth_credential id={}] 旧/新 key 都无法解密", row.getId());
                    throw tryOldFailed;
                }
            }
            String newCipher = AesGcmUtil.encrypt(plaintext, newKey);
            if (!dryRun) {
                RepoAuthCredential upd = new RepoAuthCredential();
                upd.setId(row.getId());
                upd.setTokenEncrypted(newCipher);
                credentialMapper.updateById(upd);
            }
            migrated++;
        }
        log.info("[repo_auth_credential] 迁移 {} 条，跳过（已迁移）{} 条", migrated, skipped);
        return migrated;
    }
}
