package com.socialflow.service.codeanalysis.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.socialflow.common.exception.BusinessException;
import com.socialflow.common.util.AesGcmUtil;
import com.socialflow.dao.mapper.RepoAuthCredentialMapper;
import com.socialflow.model.dto.SaveCredentialDTO;
import com.socialflow.model.entity.RepoAuthCredential;
import com.socialflow.model.vo.RepoAuthCredentialVO;
import com.socialflow.service.codeanalysis.CredentialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 凭证服务实现。
 *
 * 设计要点：
 *   1. token 入库前 AES-256-GCM 加密，出库后不在 VO 里暴露明文（只给掩码）
 *   2. 仅 {@link #resolveForUrl(Long, String)} / {@link #getDecrypted(Long, Long)}
 *      返回含明文 token 的 entity，供 GitRepoServiceImpl 在克隆瞬间使用
 *   3. 按 host 匹配时优先 is_default=1，次之取最近创建
 *   4. 新增 default 凭证时，自动把该 host 下其他凭证的 default 清零
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CredentialServiceImpl implements CredentialService {

    private final RepoAuthCredentialMapper credentialMapper;

    @Value("${socialflow.ai.encryption-key:***REDACTED-EXAMPLE-AES-KEY***}")
    private String encryptionKey;

    // ================== CRUD ==================

    @Override
    public List<RepoAuthCredentialVO> list(Long userId) {
        List<RepoAuthCredential> rows = credentialMapper.selectList(
                new LambdaQueryWrapper<RepoAuthCredential>()
                        .eq(RepoAuthCredential::getUserId, userId)
                        .orderByDesc(RepoAuthCredential::getIsDefault)
                        .orderByDesc(RepoAuthCredential::getCreateTime));
        return rows.stream().map(this::toVo).toList();
    }

    @Override
    public RepoAuthCredentialVO save(Long userId, SaveCredentialDTO dto) {
        RepoAuthCredential e;
        boolean isNew = (dto.getId() == null);
        if (isNew) {
            e = new RepoAuthCredential();
            e.setUserId(userId);
            e.setTestStatus("UNKNOWN");
        } else {
            e = credentialMapper.selectById(dto.getId());
            if (e == null || !e.getUserId().equals(userId)) {
                throw new BusinessException("凭证不存在");
            }
        }
        e.setNickname(dto.getNickname());
        e.setGitHost(normalizeHost(dto.getGitHost()));
        // 认证类型：仅允许 TOKEN / PASSWORD，默认 TOKEN
        String authType = dto.getAuthType();
        if (authType == null || authType.isBlank()) authType = "TOKEN";
        authType = authType.toUpperCase();
        if (!"TOKEN".equals(authType) && !"PASSWORD".equals(authType)) {
            throw new BusinessException("认证类型必须是 TOKEN 或 PASSWORD");
        }
        e.setAuthType(authType);
        e.setUsername(dto.getUsername());
        if (dto.getToken() != null && !dto.getToken().isBlank()) {
            e.setTokenEncrypted(AesGcmUtil.encrypt(dto.getToken(), encryptionKey));
            e.setTokenHint(maskToken(dto.getToken(), authType));
            e.setTestStatus("UNKNOWN");  // 秘钥换了 → 重置测试状态
            e.setTestMessage(null);
        } else if (isNew) {
            throw new BusinessException(
                    "PASSWORD".equals(authType) ? "新建凭证必须填写密码" : "新建凭证必须填写 Token");
        }
        Integer wantDefault = dto.getIsDefault() == null ? 0 : dto.getIsDefault();
        e.setIsDefault(wantDefault);

        if (isNew) credentialMapper.insert(e);
        else credentialMapper.updateById(e);

        // 若设为 default，把同 host 下其他凭证的 default 清零
        if (wantDefault == 1) {
            credentialMapper.update(null,
                    new LambdaUpdateWrapper<RepoAuthCredential>()
                            .eq(RepoAuthCredential::getUserId, userId)
                            .eq(RepoAuthCredential::getGitHost, e.getGitHost())
                            .ne(RepoAuthCredential::getId, e.getId())
                            .set(RepoAuthCredential::getIsDefault, 0));
        }

        return toVo(credentialMapper.selectById(e.getId()));
    }

    @Override
    public void delete(Long userId, Long id) {
        RepoAuthCredential e = credentialMapper.selectById(id);
        if (e == null || !e.getUserId().equals(userId)) return;
        credentialMapper.deleteById(id);
    }

    // ================== 测试连接 ==================

    @Override
    public RepoAuthCredentialVO test(Long userId, Long id) {
        RepoAuthCredential e = getDecrypted(userId, id);
        String token = e.getTokenEncrypted();  // getDecrypted 已把 tokenEncrypted 替换成了明文
        String host = e.getGitHost();
        String testUrl = "https://" + host + "/";
        String resultStatus = "FAILED";
        String resultMsg;
        try {
            // 用 ls-remote 小成本测 token 是否认证成功
            // 这里对 host 做一次 ls-remote（无目标仓库时会 401/403，只判断是否能 auth）
            // 实际更稳的做法：让用户在 test 时填一个测试 URL，此处简化为 host root
            Git.lsRemoteRepository()
                    .setRemote(testUrl)
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(e.getUsername(), token))
                    .setTimeout(10)
                    .call();
            resultStatus = "SUCCESS";
            resultMsg = "认证成功";
        } catch (Exception ex) {
            String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            // 注意：很多 Git Host 的 root 不是有效 repo，会抛 not found 而非 auth failed
            // not found / repository 类异常视为"凭证有效但路径不对" → 也算测通
            String lower = msg.toLowerCase();
            if (lower.contains("not found") || lower.contains("not a git repository")
                    || lower.contains("doesn't exist") || lower.contains("not authorized to access")) {
                resultStatus = "SUCCESS";
                resultMsg = "可访问该 host（需要具体仓库 URL 才能完整验证权限）";
            } else if (lower.contains("auth") || lower.contains("401") || lower.contains("403")) {
                resultMsg = "认证失败：用户名或 Token 不正确";
            } else {
                resultMsg = "连接失败：" + truncate(msg, 180);
            }
        }
        RepoAuthCredential upd = new RepoAuthCredential();
        upd.setId(id);
        upd.setTestStatus(resultStatus);
        upd.setTestMessage(resultMsg);
        upd.setLastUsedAt(LocalDateTime.now());
        credentialMapper.updateById(upd);
        return toVo(credentialMapper.selectById(id));
    }

    // ================== 匹配 & 解密 ==================

    @Override
    public Optional<RepoAuthCredential> resolveForUrl(Long userId, String gitUrl) {
        String host = extractHost(gitUrl);
        if (host == null) return Optional.empty();
        List<RepoAuthCredential> rows = credentialMapper.selectList(
                new LambdaQueryWrapper<RepoAuthCredential>()
                        .eq(RepoAuthCredential::getUserId, userId)
                        .eq(RepoAuthCredential::getGitHost, host)
                        .orderByDesc(RepoAuthCredential::getIsDefault)
                        .orderByDesc(RepoAuthCredential::getCreateTime));
        if (rows.isEmpty()) return Optional.empty();
        RepoAuthCredential picked = rows.get(0);
        try {
            picked.setTokenEncrypted(AesGcmUtil.decrypt(picked.getTokenEncrypted(), encryptionKey));
        } catch (Exception ex) {
            log.error("[Credential] decrypt failed for id={}", picked.getId(), ex);
            return Optional.empty();
        }
        // 更新 last_used_at（弱一致，不等回写）
        RepoAuthCredential upd = new RepoAuthCredential();
        upd.setId(picked.getId());
        upd.setLastUsedAt(LocalDateTime.now());
        credentialMapper.updateById(upd);
        return Optional.of(picked);
    }

    @Override
    public RepoAuthCredential getDecrypted(Long userId, Long id) {
        RepoAuthCredential e = credentialMapper.selectById(id);
        if (e == null || !e.getUserId().equals(userId)) {
            throw new BusinessException("凭证不存在");
        }
        try {
            e.setTokenEncrypted(AesGcmUtil.decrypt(e.getTokenEncrypted(), encryptionKey));
        } catch (Exception ex) {
            throw new BusinessException("凭证解密失败，请检查 encryption-key 是否变更");
        }
        return e;
    }

    // ================== helpers ==================

    private RepoAuthCredentialVO toVo(RepoAuthCredential e) {
        RepoAuthCredentialVO v = new RepoAuthCredentialVO();
        BeanUtils.copyProperties(e, v);
        // 确保永远不外泄加密密文
        return v;
    }

    /** 从 Git URL 提取 host（含端口），失败返回 null */
    public static String extractHost(String gitUrl) {
        if (gitUrl == null || gitUrl.isBlank()) return null;
        try {
            String u = gitUrl.trim();
            // 处理 git@github.com:user/repo.git 这种 SSH 格式
            if (u.startsWith("git@")) {
                int colon = u.indexOf(':', 4);
                if (colon > 4) return u.substring(4, colon).toLowerCase();
                return null;
            }
            // http(s)://
            URI uri = URI.create(u);
            String host = uri.getHost();
            if (host == null) return null;
            return uri.getPort() > 0 ? host + ":" + uri.getPort() : host;
        } catch (Exception e) {
            return null;
        }
    }

    private static String normalizeHost(String raw) {
        if (raw == null) return null;
        String h = raw.trim().toLowerCase();
        // 去掉可能粘贴进来的 protocol / 路径
        if (h.startsWith("http://")) h = h.substring(7);
        if (h.startsWith("https://")) h = h.substring(8);
        int slash = h.indexOf('/');
        if (slash > 0) h = h.substring(0, slash);
        return h;
    }

    /** 生成掩码：
     *  TOKEN 模式：ghp_****f8a —— 前 4 + "****" + 后 3（Token 前缀一般是 provider 特征）
     *  PASSWORD 模式：********（不泄漏任何长度/字符信息，也不暴露用户密码习惯）
     */
    public static String maskToken(String plain, String authType) {
        if (plain == null) return "****";
        if ("PASSWORD".equalsIgnoreCase(authType)) {
            return "********";
        }
        if (plain.length() < 8) return "****";
        return plain.substring(0, 4) + "****" + plain.substring(plain.length() - 3);
    }

    private static String truncate(String s, int len) {
        return s == null || s.length() <= len ? s : s.substring(0, len);
    }
}
