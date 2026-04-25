package com.socialflow.service.content;

import com.socialflow.common.result.PageResult;
import com.socialflow.model.dto.ContentBatchGenerateDTO;
import com.socialflow.model.dto.ContentGenerateDTO;
import com.socialflow.model.dto.ContentRewriteDTO;
import com.socialflow.model.dto.MultiAgentGenerateDTO;
import com.socialflow.model.vo.ContentVO;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 内容服务接口 —— 整个系统最核心的业务接口。
 *
 * 负责的业务领域：AI 文案生成、内容改写、标题/标签建议、相似内容检索，
 * 以及内容的基础增删改查（CRUD）操作。
 *
 * 对应的 Controller：{@code ContentController}，路由前缀为 {@code /api/v1/content/*}。
 * 前端所有跟内容生成和管理相关的请求，最终都会调用到这个接口的方法。
 *
 * 实现类：{@link com.socialflow.service.content.impl.ContentServiceImpl}
 *
 * @see com.socialflow.service.content.impl.ContentServiceImpl
 */
public interface ContentService {

    /**
     * 单平台同步生成文案。
     *
     * 这是最基础的内容生成方法。前端发送请求后会一直等待，直到 AI 完整生成内容后才返回结果。
     * 适用于内容较短或用户不介意等待的场景。
     *
     * @param userId 当前登录用户的 ID，用于权限校验和数据隔离
     * @param dto    生成请求参数，包含主题（topic）、关键词、目标平台、字数要求、模板 ID 等
     * @return 生成的内容视图对象，包含标题、正文、标签等完整信息
     */
    ContentVO generate(Long userId, ContentGenerateDTO dto);

    /**
     * 单平台流式生成文案（SSE 推送）。
     *
     * 与 {@link #generate} 的区别：返回的是一个 Flux 流，AI 每生成一个 token（词元）
     * 就会通过 SSE（Server-Sent Events）实时推送给前端，实现打字机效果。
     * 用户体验更好，不需要等待全部生成完成。
     *
     * @param userId 当前登录用户的 ID
     * @param dto    生成请求参数，与同步生成相同
     * @return Flux 字符串流，每个元素是一个 token 或一小段文本
     */
    Flux<String> generateStream(Long userId, ContentGenerateDTO dto);

    /**
     * 多平台批量生成文案（并行处理）。
     *
     * 一次请求同时为多个平台（如微博、小红书、公众号等）生成适配的文案。
     * 内部使用并行处理（fan-out 模式），提高效率。
     *
     * @param userId 当前登录用户的 ID
     * @param dto    批量生成参数，包含多个目标平台列表和共用的主题信息
     * @return Map，key 是平台名称（如 "weibo"），value 是该平台对应的生成内容
     */
    Map<String, ContentVO> generateBatch(Long userId, ContentBatchGenerateDTO dto);

    /**
     * 多 Agent 协作生成文案（SSE 推送阶段更新）。
     *
     * 这是最高级的生成模式。多个 AI Agent 分工协作（如：研究员负责调研、
     * 写手负责创作、编辑负责润色），每个阶段的进度通过 SSE 实时推送给前端。
     *
     * @param userId 当前登录用户的 ID
     * @param dto    多 Agent 生成参数，可指定工作流配置和各 Agent 的角色
     * @return Flux 字符串流，包含各阶段的进度更新和最终生成结果
     */
    Flux<String> generateMultiAgent(Long userId, MultiAgentGenerateDTO dto);

    /**
     * 改写已有内容（换风格或换平台）。
     *
     * 基于数据库中已有的一条内容记录，将其改写为不同的语气风格或适配另一个平台。
     * 例如：将一篇公众号文章改写为小红书笔记风格。
     *
     * @param userId 当前登录用户的 ID
     * @param dto    改写参数，包含原内容 ID、目标平台、目标语气等
     * @return 改写后的新内容视图对象
     */
    ContentVO rewrite(Long userId, ContentRewriteDTO dto);

    /**
     * 生成多个候选标题。
     *
     * 给定内容正文，AI 会生成若干个备选标题供用户选择，帮助用户提高标题的吸引力。
     *
     * @param userId   当前登录用户的 ID
     * @param body     内容正文，AI 根据正文内容来生成标题
     * @param platform 目标平台（不同平台的标题风格不同）
     * @param count    需要生成的标题数量
     * @return 候选标题列表
     */
    List<String> generateTitles(Long userId, String body, String platform, int count);

    /**
     * 智能推荐话题标签（Hashtag）。
     *
     * 根据内容正文和目标平台，AI 推荐合适的话题标签，
     * 帮助内容获得更好的曝光和搜索排名。
     *
     * @param userId   当前登录用户的 ID
     * @param body     内容正文
     * @param platform 目标平台（如微博用 #标签#，小红书用 #标签）
     * @param count    推荐标签数量
     * @return 推荐的标签列表
     */
    List<String> suggestHashtags(Long userId, String body, String platform, int count);

    /**
     * 相似内容检索（基于向量搜索）。
     *
     * 通过向量数据库进行语义搜索，找到与给定文本内容最相似的历史文案。
     * 可用于参考灵感、避免重复创作等场景。
     *
     * @param userId 当前登录用户的 ID
     * @param text   用于检索的文本（会被转换为向量进行相似度匹配）
     * @param topK   返回最相似的前 K 条记录
     * @return 相似内容列表，按相似度降序排列
     */
    List<ContentVO> similar(Long userId, String text, int topK);

    // ==================== 基础 CRUD 操作 ====================

    /**
     * 根据 ID 获取单条内容详情。
     *
     * @param userId 当前登录用户的 ID（用于校验内容归属）
     * @param id     内容记录的主键 ID
     * @return 内容视图对象
     */
    ContentVO get(Long userId, Long id);

    /**
     * 更新内容的标题、正文和标签。
     *
     * @param userId 当前登录用户的 ID（用于校验内容归属）
     * @param id     要更新的内容 ID
     * @param title  新标题
     * @param body   新正文
     * @param tags   新标签（逗号分隔的字符串）
     * @return 更新后的内容视图对象
     */
    ContentVO update(Long userId, Long id, String title, String body, String tags);

    /**
     * 删除一条内容记录。
     *
     * @param userId 当前登录用户的 ID（用于校验内容归属）
     * @param id     要删除的内容 ID
     */
    void delete(Long userId, Long id);

    /**
     * 分页查询内容列表（支持多条件筛选）。
     *
     * @param userId   当前登录用户的 ID（只能查自己的内容）
     * @param pageNum  页码，从 1 开始
     * @param pageSize 每页数量
     * @param platform 平台筛选条件（可选）
     * @param status   状态筛选条件（可选，如 DRAFT / PUBLISHED）
     * @param keyword  关键词模糊搜索（可选，匹配标题和正文）
     * @param tags     标签筛选条件（可选）
     * @return 分页结果，包含当前页数据和总记录数
     */
    PageResult<ContentVO> list(Long userId, Integer pageNum, Integer pageSize,
                                String platform, String status, String keyword, String tags);

    // ==================== Wave 4.3: 内容库 UX ====================

    /**
     * Autosave 草稿（Wave 4.3）—— 仅更新 body/title/tags，不改 status，不写版本快照。
     *
     * <p>前端可以每隔 N 秒在用户编辑时自动调用，避免崩溃丢失。MyBatis-Plus
     * 的 @Version 自动校验防止并发覆盖。</p>
     *
     * @return 更新后的 ContentVO（包含新 version 号）
     */
    ContentVO saveDraft(Long userId, Long id, String title, String body, String tags);

    /**
     * 批量软删（Wave 4.3）—— 按 id 列表批量软删，返回实际删除的条数。
     */
    int bulkDelete(Long userId, List<Long> ids);

    /**
     * 批量改状态（Wave 4.3）—— 给一组 content 设置同一个 status（DRAFT/SCHEDULED/PUBLISHED 等）。
     * 返回实际更新的条数。
     */
    int bulkUpdateStatus(Long userId, List<Long> ids, String status);

    /**
     * 克隆内容（Wave 4.3）—— 按现有 content 的快照创建一份新的 DRAFT，
     * 不复制版本历史/发布任务/媒体关联（仅头表数据）。
     */
    ContentVO clone(Long userId, Long id);

    /**
     * 绑定一组媒体素材到文案。先清除旧关联再按顺序插入新关联。
     * 前端在生成配图后让用户勾选保存时调用。
     */
    void bindMedia(Long userId, Long contentId, List<Long> mediaIds);

    /**
     * 查询文案绑定的媒体素材列表（按 sortOrder 升序）。
     */
    List<com.socialflow.model.entity.MediaAsset> listBoundMedia(Long userId, Long contentId);

    /**
     * 查询内容的版本历史列表（按 versionNum 倒序，最新版在前）。
     */
    List<com.socialflow.model.entity.ContentVersion> listVersions(Long userId, Long contentId);

    /**
     * 计算两个版本之间的字段级差异（V22）。
     *
     * <p>对 title / body / tags / status 四个用户可见字段做 before/after 对比，
     * 仅返回发生变化的字段。便于前端"版本对比"页面以红绿色块展示差异。</p>
     *
     * @param userId       请求者 ID（用于权限校验）
     * @param contentId    内容 ID
     * @param fromVersion  较早的版本号（base）
     * @param toVersion    较晚的版本号（head）
     * @return 差异视图；若两版本字段完全一致，{@code changes} 列表为空
     */
    com.socialflow.model.vo.ContentVersionDiffVO diffVersions(
            Long userId, Long contentId, Integer fromVersion, Integer toVersion);
}
