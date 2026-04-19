package com.socialflow.service.codeanalysis;

import com.socialflow.model.dto.SaveCredentialDTO;
import com.socialflow.model.entity.RepoAuthCredential;
import com.socialflow.model.vo.RepoAuthCredentialVO;

import java.util.List;
import java.util.Optional;

/**
 * Git 仓库凭证服务。
 *
 * 负责凭证的 CRUD、按 host 匹配、测试连接；token 明文只在调用
 * {@link #resolveForUrl(Long, String)} 时短暂解密返回给 Git 客户端，
 * 其余返回都只包含掩码。
 */
public interface CredentialService {

    /** 列出当前用户所有凭证（token 已掩码） */
    List<RepoAuthCredentialVO> list(Long userId);

    /** 新增 / 更新凭证。token 留空代表不修改 */
    RepoAuthCredentialVO save(Long userId, SaveCredentialDTO dto);

    void delete(Long userId, Long id);

    /**
     * 测试连接：调用 JGit 的 lsRemote 验证凭证是否有效。
     * 会更新 testStatus / testMessage / lastUsedAt。
     */
    RepoAuthCredentialVO test(Long userId, Long id);

    /**
     * 按 Git URL 的 host 自动匹配用户的凭证。
     * 优先返回 is_default=1 的；没有默认就取最近创建的一条。
     *
     * @return 包含**解密后明文 token** 的 entity（仅内部 GitRepoService 使用）
     */
    Optional<RepoAuthCredential> resolveForUrl(Long userId, String gitUrl);

    /** 根据 id 取凭证（含解密后 token），校验归属 */
    RepoAuthCredential getDecrypted(Long userId, Long id);
}
