package com.socialflow.service.codeanalysis;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.socialflow.model.dto.AnalyzeRepoDTO;
import com.socialflow.model.dto.FindingStatusDTO;
import com.socialflow.model.dto.SaveBookmarkDTO;
import com.socialflow.model.vo.AnalysisStatsVO;
import com.socialflow.model.vo.CodeAnalysisVO;
import com.socialflow.model.vo.LlmCallLogVO;
import com.socialflow.model.vo.RepoBookmarkVO;
import com.socialflow.model.vo.RepoCommitVO;

import java.util.List;

/**
 * 代码分析门面服务 —— Controller 唯一依赖点。
 *
 * 三种核心分析（项目概览 / 提交审查 / 对比分析）+ 附属能力（历史 / 收藏 / 规约 / 分享）。
 * 所有 trigger 方法立即返回 analysisId，真实执行走 {@code @Async}。前端通过
 * {@link #getAnalysis(Long, Long)} 轮询。
 */
public interface CodeAnalysisService {

    /** 触发项目概览分析（异步），立刻返回记录 ID */
    Long triggerProjectOverview(Long userId, AnalyzeRepoDTO dto);

    /** 触发提交审查分析（异步） */
    Long triggerCommitReview(Long userId, AnalyzeRepoDTO dto);

    /** 触发对比分析（异步） */
    Long triggerDiffReview(Long userId, AnalyzeRepoDTO dto);

    /** 获取分析结果（含进度） */
    CodeAnalysisVO getAnalysis(Long userId, Long analysisId);

    /** 通过 shareToken 公开访问（无需登录） */
    CodeAnalysisVO getByShareToken(String shareToken);

    /** 生成/返回分享 token */
    String generateShareToken(Long userId, Long analysisId);

    /** 历史分页查询 */
    Page<CodeAnalysisVO> history(Long userId, Integer current, Integer size,
                                 String analysisType, String keyword);

    /** 删除记录（逻辑删除） */
    void delete(Long userId, Long analysisId);

    /** 收藏/取消 */
    void toggleFavorite(Long userId, Long analysisId);

    /** 标注单条 finding 状态 */
    void updateFindingStatus(Long userId, Long findingId, FindingStatusDTO dto);

    /** 拉取仓库最近 commit 列表（同步，用于选 commit）。
     *  会按 userId 匹配凭证以访问私有仓库。 */
    List<RepoCommitVO> listCommits(Long userId, String gitUrl, String branch, Integer limit);

    /** 仪表盘聚合统计 */
    AnalysisStatsVO dashboardStats(Long userId);

    /** 查某次分析的所有 LLM 调用日志（按时间升序） */
    List<LlmCallLogVO> listLlmCalls(Long userId, Long analysisId);

    /* ------ 书签 ------ */

    List<RepoBookmarkVO> listBookmarks(Long userId);

    RepoBookmarkVO saveBookmark(Long userId, SaveBookmarkDTO dto);

    void deleteBookmark(Long userId, Long bookmarkId);
}
