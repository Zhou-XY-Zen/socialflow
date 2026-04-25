package com.socialflow.service.codeanalysis.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.socialflow.common.exception.BusinessException;
import com.socialflow.common.util.AesGcmUtil;
import com.socialflow.dao.mapper.RepoAuthCredentialMapper;
import com.socialflow.dao.mapper.RepoCredentialProjectMapper;
import com.socialflow.model.dto.SaveCredentialDTO;
import com.socialflow.model.dto.SaveCredentialProjectDTO;
import com.socialflow.model.entity.RepoAuthCredential;
import com.socialflow.model.entity.RepoCredentialProject;
import com.socialflow.model.vo.RepoAuthCredentialVO;
import com.socialflow.model.vo.RepoCredentialProjectVO;
import com.socialflow.service.codeanalysis.CredentialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
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
    private final RepoCredentialProjectMapper projectMapper;

    @Value("${socialflow.ai.encryption-key}")
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
        // host 可由用户手填，也可从 defaultRepoUrl 自动提取（用户体验：只填完整 URL 即可）
        String host = normalizeHost(dto.getGitHost());
        if ((host == null || host.isBlank()) && dto.getDefaultRepoUrl() != null) {
            host = extractHost(dto.getDefaultRepoUrl());
        }
        if (host == null || host.isBlank()) {
            throw new BusinessException("请填 Git Host 或带 host 的完整仓库 URL");
        }
        e.setGitHost(host);
        // 认证类型：仅允许 TOKEN / PASSWORD，默认 TOKEN
        String authType = dto.getAuthType();
        if (authType == null || authType.isBlank()) authType = "TOKEN";
        authType = authType.toUpperCase();
        if (!"TOKEN".equals(authType) && !"PASSWORD".equals(authType)) {
            throw new BusinessException("认证类型必须是 TOKEN 或 PASSWORD");
        }
        e.setAuthType(authType);
        e.setUsername(dto.getUsername());
        // 常用仓库 URL（可选）
        e.setDefaultRepoUrl(dto.getDefaultRepoUrl() != null && !dto.getDefaultRepoUrl().isBlank()
                ? dto.getDefaultRepoUrl().trim() : null);
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
        String token = e.getPlainToken();  // 明文只在 transient 字段上，不会被日志/JSON 泄漏
        // 优先用户填的"默认仓库 URL"（精准验证），否则退回 host root
        boolean usingRealRepo = e.getDefaultRepoUrl() != null && !e.getDefaultRepoUrl().isBlank();
        String testUrl = usingRealRepo ? e.getDefaultRepoUrl() : ("https://" + e.getGitHost() + "/");
        String resultStatus = "FAILED";
        String resultMsg;
        try {
            Git.lsRemoteRepository()
                    .setRemote(testUrl)
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(e.getUsername(), token))
                    .setTimeout(10)
                    .call();
            resultStatus = "SUCCESS";
            resultMsg = usingRealRepo
                    ? "✅ 精准验证成功：凭证能访问 " + e.getDefaultRepoUrl()
                    : "✅ Host 可达（建议填一个具体仓库 URL 做精准验证）";
        } catch (GitAPIException | RuntimeException ex) {
            // GitAPIException：JGit 显式抛出的认证/不存在/超时
            // RuntimeException：JGit 底层把 IOException 包成 TransportException 等运行时异常
            // —— InterruptedException 不在此分支，会顺利向上传播
            String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            String lower = msg.toLowerCase();
            // 无默认仓库测 host root 时，not-found 代表"能到 host 但不是 git 服务"，视为可达
            if (!usingRealRepo && (lower.contains("not found") || lower.contains("not a git repository")
                    || lower.contains("doesn't exist") || lower.contains("not authorized to access"))) {
                resultStatus = "SUCCESS";
                resultMsg = "Host 可达（建议填一个具体仓库 URL 做精准验证）";
            } else if (lower.contains("auth") || lower.contains("401") || lower.contains("403")
                    || lower.contains("unauthorized")) {
                resultMsg = "认证失败：用户名或 Token/密码不正确";
            } else if (lower.contains("not found") || lower.contains("doesn't exist")) {
                resultMsg = "仓库不存在或你的账号无访问权限：" + truncate(msg, 140);
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
            // 解密后的明文只放 plainToken（transient/JsonIgnore），不回写 tokenEncrypted 字段。
            // 这样即使 picked 被误打印/序列化，密文仍是密文，明文不泄漏。
            picked.setPlainToken(AesGcmUtil.decrypt(picked.getTokenEncrypted(), encryptionKey));
        } catch (IllegalStateException | IllegalArgumentException ex) {
            // AesGcmUtil 在 GCM tag 不匹配 / 密钥格式错时抛 IllegalStateException
            // 长度校验失败抛 IllegalArgumentException
            // 任何一种都意味着此凭证无法用，跳过即可（不挂主流程）
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
            // 明文只放 transient 字段；调用方通过 getPlainToken() 读取，用完应立即让局部变量离开作用域。
            e.setPlainToken(AesGcmUtil.decrypt(e.getTokenEncrypted(), encryptionKey));
        } catch (IllegalStateException | IllegalArgumentException ex) {
            // 解密失败的两种典型原因：
            //   1) encryption-key 变更（IllegalStateException —— GCM tag 校验失败）
            //   2) 密钥格式错（IllegalArgumentException —— 来自 AesGcmUtil.hexToBytes）
            // 业务异常友好提示，不把底层堆栈泄给前端
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
        } catch (IllegalArgumentException e) {
            // URI.create 在格式非法时抛 IllegalArgumentException（包装了 URISyntaxException）
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

    // ==================== 子级：凭证下的仓库项目 ====================

    @Override
    public List<RepoCredentialProjectVO> listProjects(Long userId, Long credentialId) {
        ensureOwnership(userId, credentialId);
        List<RepoCredentialProject> rows = projectMapper.selectList(
                new LambdaQueryWrapper<RepoCredentialProject>()
                        .eq(RepoCredentialProject::getCredentialId, credentialId)
                        .orderByDesc(RepoCredentialProject::getLastUsedAt)
                        .orderByDesc(RepoCredentialProject::getCreateTime));
        return rows.stream().map(this::toProjectVo).toList();
    }

    @Override
    public RepoCredentialProjectVO saveProject(Long userId, Long credentialId, SaveCredentialProjectDTO dto) {
        ensureOwnership(userId, credentialId);
        RepoCredentialProject p;
        if (dto.getId() != null) {
            p = projectMapper.selectById(dto.getId());
            if (p == null || !p.getCredentialId().equals(credentialId)) {
                throw new BusinessException("项目不存在或不属于该凭证");
            }
        } else {
            p = new RepoCredentialProject();
            p.setCredentialId(credentialId);
        }
        String nickname = dto.getNickname();
        if (nickname == null || nickname.isBlank()) {
            nickname = repoNameFromUrl(dto.getGitUrl());
        }
        p.setNickname(nickname);
        p.setGitUrl(dto.getGitUrl().trim());
        p.setBranch(dto.getBranch() != null && !dto.getBranch().isBlank() ? dto.getBranch().trim() : "main");

        if (dto.getId() != null) projectMapper.updateById(p);
        else projectMapper.insert(p);

        return toProjectVo(projectMapper.selectById(p.getId()));
    }

    @Override
    public void deleteProject(Long userId, Long projectId) {
        RepoCredentialProject p = projectMapper.selectById(projectId);
        if (p == null) return;
        ensureOwnership(userId, p.getCredentialId());
        projectMapper.deleteById(projectId);
    }

    private void ensureOwnership(Long userId, Long credentialId) {
        RepoAuthCredential c = credentialMapper.selectById(credentialId);
        if (c == null || !c.getUserId().equals(userId)) {
            throw new BusinessException("凭证不存在");
        }
    }

    private RepoCredentialProjectVO toProjectVo(RepoCredentialProject p) {
        RepoCredentialProjectVO v = new RepoCredentialProjectVO();
        org.springframework.beans.BeanUtils.copyProperties(p, v);
        return v;
    }

    /** 从 URL 末尾提取仓库名：https://github.com/user/repo.git → repo */
    private static String repoNameFromUrl(String url) {
        if (url == null) return "";
        String s = url.trim();
        if (s.endsWith(".git")) s = s.substring(0, s.length() - 4);
        int slash = s.lastIndexOf('/');
        int colon = s.lastIndexOf(':');  // git@github.com:user/repo 形式
        int cut = Math.max(slash, colon);
        return cut >= 0 && cut + 1 < s.length() ? s.substring(cut + 1) : s;
    }
}
