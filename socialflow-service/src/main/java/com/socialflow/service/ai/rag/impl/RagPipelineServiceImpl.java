package com.socialflow.service.ai.rag.impl;

import com.socialflow.common.constant.CommonConstants;
import com.socialflow.dao.mapper.KnowledgeChunkMapper;
import com.socialflow.model.entity.KnowledgeChunk;
import com.socialflow.model.vo.ChunkSearchVO;
import com.socialflow.service.ai.embedding.EmbeddingService;
import com.socialflow.service.ai.embedding.VectorStoreService;
import com.socialflow.service.ai.embedding.VectorStoreService.SearchHit;
import com.socialflow.service.ai.llm.ChatMessage;
import com.socialflow.service.ai.llm.LlmConfig;
import com.socialflow.service.ai.llm.LlmResponse;
import com.socialflow.service.ai.llm.LlmRouter;
import com.socialflow.service.ai.rag.RagPipelineService;
import com.socialflow.service.ai.rag.RerankerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAG 检索管道的默认实现类。
 *
 * 【概述】
 *
 * 本类实现了完整的 RAG（检索增强生成）8 步流程，是系统中最核心的 AI 检索组件。
 * 当用户需要基于知识库生成内容时，本类负责从海量文档中精准找到最相关的片段。
 *
 * 【8 步处理流程】
 *     - HyDE 查询重写（可选）——让 LLM 先生成一个"假设性回答"用于检索
 *     - 向量嵌入——将查询文本转为向量
 *     - 向量语义搜索——在向量数据库中按语义相似度检索
 *     - BM25 关键词搜索——通过 MySQL 全文索引按关键词匹配检索
 *     - RRF 融合去重——将两种搜索结果合并排序
 *     - 补全数据——从 MySQL 加载完整的片段内容
 *     - 重排序——使用 Reranker 模型对候选片段精排
 *     - 转换为 VO——将实体对象转为前端可用的视图对象
 *
 * 【依赖组件】
 *     - {@link EmbeddingService}：文本向量嵌入服务
 *     - {@link VectorStoreService}：向量数据库操作服务
 *     - {@link RerankerService}：重排序服务
 *     - {@link KnowledgeChunkMapper}：知识片段数据库访问层
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagPipelineServiceImpl implements RagPipelineService {

    /** 文本向量嵌入服务，用于将查询文本转换为向量 */
    private final EmbeddingService embeddingService;

    /** 向量数据库操作服务，用于执行向量相似度搜索 */
    private final VectorStoreService vectorStoreService;

    /** 重排序服务，用于对候选片段进行精细化排序 */
    private final RerankerService rerankerService;

    /** 知识片段 MyBatis Mapper，用于从 MySQL 中查询片段数据 */
    private final KnowledgeChunkMapper knowledgeChunkMapper;

    /** LLM 请求路由器，用于 HyDE 查询重写时调用轻量级 LLM */
    private final LlmRouter llmRouter;

    /** 默认 LLM 提供者名称 */
    @Value("${socialflow.ai.default-provider:deepseek}")
    private String defaultProvider;

    /** 系统级 API 密钥 */
    @Value("${socialflow.ai.system-api-key:}")
    private String systemApiKey;

    /** 向量搜索和 BM25 搜索各自返回的候选数量（粗排阶段），默认 10 */
    @Value("${socialflow.rag.top-k:10}")
    private int defaultTopK;

    /** 重排序后保留的最终片段数量（精排阶段），默认 5 */
    @Value("${socialflow.rag.rerank-top-k:5}")
    private int defaultRerankTopK;

    /** 是否启用 HyDE（假设性文档嵌入）查询重写，默认启用 */
    @Value("${socialflow.rag.enable-hyde:true}")
    private boolean enableHyde;

    /** 是否启用混合搜索（向量搜索 + BM25 关键词搜索），默认启用 */
    @Value("${socialflow.rag.enable-hybrid-search:true}")
    private boolean enableHybrid;

    /** 是否启用重排序，默认启用 */
    @Value("${socialflow.rag.enable-rerank:true}")
    private boolean enableRerank;

    /**
     * 执行完整的 RAG 检索管道，返回排序后的知识片段列表。
     *
     * 这是 RAG 管道的核心方法，按 8 个步骤依次执行。
     */
    @Override
    public List<ChunkSearchVO> retrieve(Long userId, Long kbId, String query, int topK) {
        // ============ 第 1 步：HyDE 查询重写 ============
        // HyDE（Hypothetical Document Embeddings）是一种检索优化技巧：
        // 先让 LLM 根据用户问题生成一个"假设性回答"，然后用这个回答（而非原始问题）
        // 去做向量检索。因为假设性回答的措辞更接近知识库中的实际文档，所以往往能
        // 检索到更相关的结果。如果关闭此功能，则直接使用原始查询。
        String effectiveQuery = enableHyde ? hydeRewrite(query) : query;

        // ============ 第 2 步：向量嵌入 ============
        // 将查询文本（可能是经过 HyDE 重写后的）转换为数学向量，
        // 以便在向量数据库中进行相似度搜索。
        float[] vector = embeddingService.embed(effectiveQuery);

        // ============ 第 3 步：向量语义搜索 ============
        // 在向量数据库的 kb_chunks 集合中搜索与查询向量最相似的片段。
        // filter 参数确保只在指定知识库（kb_id）范围内搜索，实现数据隔离。
        Map<String, Object> filter = new HashMap<>();
        filter.put("kb_id", kbId);
        List<SearchHit> vectorHits = vectorStoreService.search(
                CommonConstants.VC_KB_CHUNKS, vector, filter, defaultTopK);

        // ============ 第 4 步：BM25 关键词搜索 ============
        // BM25 是一种经典的文本检索算法，基于词频统计（TF-IDF 的改进版）。
        // 向量搜索擅长捕捉语义相似性（如"汽车"能匹配到"轿车"），
        // 而 BM25 擅长精确的关键词匹配（如专有名词、型号等）。
        // 两种方式互补，能提高整体召回率。如果关闭混合搜索则跳过此步。
        List<KnowledgeChunk> bm25Hits = enableHybrid
                ? knowledgeChunkMapper.fulltextSearch(kbId, query, defaultTopK)
                : List.of();

        // ============ 第 5 步：RRF 融合去重 ============
        // 将向量搜索和 BM25 搜索的结果通过 RRF（倒数排名融合）算法合并。
        // RRF 不依赖各搜索引擎的绝对分数（不同搜索引擎分数范围不同），
        // 而是只利用排名位置来计算综合得分，天然解决了分数不可比的问题。
        List<Long> fusedChunkIds = fuseRrf(vectorHits, bm25Hits);

        // ============ 第 6 步：补全片段数据 ============
        // 向量数据库中只存储了向量和少量元数据（如 chunk_id），
        // 完整的片段文本和其他字段（文档 ID、片段序号等）需要从 MySQL 中加载。
        List<KnowledgeChunk> fused = hydrate(fusedChunkIds);

        // ============ 第 7 步：重排序 ============
        // 使用 Cross-Encoder Reranker 模型对候选片段进行精细化排序。
        // 粗排阶段可能返回 10-20 个片段，精排后只保留最相关的 topK 个。
        // 如果未启用重排序或候选为空，则直接按融合顺序截取前 topK 个。
        // Wave 4.1：保留 reranker 返回的相关度得分透传到 VO。
        List<KnowledgeChunk> finalChunks = fused;
        Map<Long, Double> chunkScores = new HashMap<>();
        if (enableRerank && !fused.isEmpty()) {
            List<String> texts = fused.stream().map(KnowledgeChunk::getContentText).toList();
            List<RerankerService.ScoredIndex> reranked = rerankerService.rerank(query, texts, topK);
            if (!reranked.isEmpty()) {
                finalChunks = reranked.stream().map(idx -> fused.get(idx.index())).toList();
                for (RerankerService.ScoredIndex si : reranked) {
                    chunkScores.put(fused.get(si.index()).getId(), si.score());
                }
            } else if (topK < fused.size()) {
                finalChunks = fused.subList(0, topK);
            }
        } else if (topK < fused.size()) {
            finalChunks = fused.subList(0, topK);
        }

        // ============ 第 8 步：转换为 VO（带 snippet + score）============
        final Map<Long, Double> scoresFinal = chunkScores;
        return finalChunks.stream()
                .map(c -> toVo(c, query, scoresFinal.get(c.getId())))
                .collect(Collectors.toList());
    }

    /**
     * 检索知识片段并格式化为上下文字符串。
     *
     * 调用 {@link #retrieve} 获取排序后的片段，然后将每个片段格式化为
     * {@code [参考1] 片段文本} 的形式，用换行符连接成一个字符串，
     * 可以直接注入到 LLM 的提示词中作为参考资料。
     */
    @Override
    public String retrieveAsContext(Long userId, Long kbId, String query, int topK) {
        // 先执行完整的检索管道获取排序后的片段
        List<ChunkSearchVO> hits = retrieve(userId, kbId, query, topK);
        // 将每个片段格式化为带编号的参考格式，便于 LLM 引用
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            sb.append("[参考").append(i + 1).append("] ")
              .append(hits.get(i).getContentText()).append("\n");
        }
        return sb.toString();
    }

    // ------------ 以下为内部辅助方法（部分为 TODO 待完善实现） ------------

    /**
     * HyDE 查询重写——用 LLM 生成一个"假设性回答"来替代原始查询进行检索。
     *
     * HyDE（Hypothetical Document Embeddings）的原理：
     * 用户的问题往往很短且口语化（如"怎么写小红书爆款"），
     * 而知识库中的文档是正式的、详细的。直接用短问题做向量检索，
     * 匹配效果可能不佳。HyDE 让 LLM 先生成一个类似知识库文档风格的
     * "假设性回答"，再用这个回答去检索，通常能找到更相关的内容。
     *
     * @param query 用户的原始查询
     * @return LLM 生成的假设性回答；调用失败时回退到原始查询
     */
    protected String hydeRewrite(String query) {
        try {
            // 构建 HyDE 提示词：让 LLM 生成一段假设性回答
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system("你是一个知识库助手"));
            messages.add(ChatMessage.user(
                    "请针对以下问题写一段可能的答案（约100字）：" + query));

            // 使用轻量级配置调用 LLM
            LlmConfig config = LlmConfig.builder()
                    .temperature(0.7)
                    .apiKey(systemApiKey)
                    .build();
            LlmResponse response = llmRouter.get(defaultProvider).chat(messages, config);

            String hypotheticalAnswer = response.getContent();
            log.debug("HyDE 重写完成, 原始查询={}, 假设性回答长度={}", query, hypotheticalAnswer.length());
            return hypotheticalAnswer;
        } catch (Exception e) {
            // 出错时回退到原始查询，不中断检索管道
            log.warn("HyDE 查询重写失败，回退到原始查询, query={}", query, e);
            return query;
        }
    }

    /**
     * RRF（Reciprocal Rank Fusion，倒数排名融合）——将多路搜索结果合并排序。
     *
     * 【RRF 算法原理】
     *
     * RRF 的公式为：{@code score(d) = sum(1 / (k + rank_i(d)))}，其中：
     *     - {@code d} 是一个文档（片段）
     *     - {@code rank_i(d)} 是该文档在第 i 路搜索结果中的排名（从 1 开始）
     *     - {@code k} 是平滑常数，通常取 60，防止排名靠前的结果权重过大
     * 如果一个片段同时出现在向量搜索和 BM25 搜索结果中，其得分会被累加，
     * 因此被两路搜索共同认可的片段会排得更靠前。
     *
     * @param vectorHits 向量搜索的命中结果
     * @param bm25Hits   BM25 关键词搜索的命中结果
     * @return 融合后按得分降序排列的片段 ID 列表
     */
    protected List<Long> fuseRrf(List<SearchHit> vectorHits, List<KnowledgeChunk> bm25Hits) {
        // 使用 LinkedHashMap 保持插入顺序，key 为片段 ID，value 为 RRF 累计得分
        Map<Long, Double> scores = new LinkedHashMap<>();
        // RRF 平滑常数 k = 60，这是论文推荐的默认值
        int k = 60;

        // --- 处理向量搜索结果 ---
        int rank = 0;
        for (SearchHit hit : vectorHits) {
            rank++; // 排名从 1 开始
            // 从向量数据库返回的元数据中提取片段 ID
            Object chunkId = hit.metadata() == null ? null : hit.metadata().get("chunk_id");
            if (chunkId == null) continue; // 元数据缺失则跳过
            long id = ((Number) chunkId).longValue();
            // RRF 公式：得分 = 1 / (k + rank)，merge 实现累加（同一片段可能出现在多路结果中）
            scores.merge(id, 1.0 / (k + rank), Double::sum);
        }

        // --- 处理 BM25 关键词搜索结果 ---
        rank = 0; // 重置排名计数器
        for (KnowledgeChunk chunk : bm25Hits) {
            rank++;
            // 同样使用 RRF 公式计算得分并累加
            scores.merge(chunk.getId(), 1.0 / (k + rank), Double::sum);
        }

        // 按 RRF 综合得分降序排序，返回片段 ID 列表
        return scores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * 补全（Hydrate）——根据片段 ID 列表从 MySQL 数据库中加载完整的片段数据。
     *
     * "Hydrate" 在这里是"给骨架数据填充完整内容"的意思。
     * 向量数据库中只存了向量和 chunk_id，具体的文本内容、文档归属等信息
     * 需要回到 MySQL 中查询。
     *
     * @param chunkIds 要加载的片段 ID 列表
     * @return 完整的 KnowledgeChunk 实体列表
     */
    protected List<KnowledgeChunk> hydrate(List<Long> chunkIds) {
        // 空列表保护：如果没有候选 ID，直接返回空列表避免无效查询
        if (chunkIds == null || chunkIds.isEmpty()) return new ArrayList<>();
        // 批量查询 MySQL，一次性加载所有片段数据
        return knowledgeChunkMapper.selectBatchIds(chunkIds);
    }

    /**
     * 将 KnowledgeChunk 实体转换为 ChunkSearchVO（Wave 4.1 升级：带 snippet + score）。
     *
     * @param c     片段实体
     * @param query 用户原始查询，用于 snippet 提取
     * @param score 重排序模型返回的相关度（可为 null）
     */
    protected ChunkSearchVO toVo(KnowledgeChunk c, String query, Double score) {
        ChunkSearchVO vo = new ChunkSearchVO();
        vo.setChunkId(c.getId());
        vo.setDocId(c.getDocId());
        vo.setKbId(c.getKbId());
        vo.setChunkIndex(c.getChunkIndex());
        vo.setContentText(c.getContentText());
        vo.setScore(score);
        vo.setSnippet(extractSnippet(c.getContentText(), query, 80));
        return vo;
    }

    /**
     * 兼容旧调用 —— 不带 query/score 的转换。
     */
    protected ChunkSearchVO toVo(KnowledgeChunk c) {
        return toVo(c, null, null);
    }

    /**
     * 摘录提取（Wave 4.1）—— 在 contentText 中找到 query 第一个出现位置，前后各取 N 字符。
     * 找不到匹配则返回首部截断。
     *
     * @param text     片段全文
     * @param query    查询关键词
     * @param halfWindow 单侧字符数
     * @return 摘录字符串（含省略号标记），text 为空时返回空串
     */
    protected String extractSnippet(String text, String query, int halfWindow) {
        if (text == null || text.isEmpty()) return "";
        int totalWindow = halfWindow * 2;
        if (text.length() <= totalWindow) return text;

        int hit = -1;
        if (query != null && !query.isBlank()) {
            // 简单实现：查找 query 任一关键词的首次出现（不分词，先按整串匹配）
            hit = text.indexOf(query);
            if (hit < 0) {
                // 退化按 query 首字符匹配
                hit = text.indexOf(query.substring(0, Math.min(2, query.length())));
            }
        }
        if (hit < 0) {
            // 没有命中：返回开头
            return text.substring(0, totalWindow) + "…";
        }
        int start = Math.max(0, hit - halfWindow);
        int end = Math.min(text.length(), hit + halfWindow);
        StringBuilder sb = new StringBuilder();
        if (start > 0) sb.append("…");
        sb.append(text, start, end);
        if (end < text.length()) sb.append("…");
        return sb.toString();
    }
}
