package com.socialflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.socialflow.model.entity.KnowledgeChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 知识库分块 Mapper —— 操作数据库表 `knowledge_chunk`
 *
 * 该接口负责对"知识库分块"表进行数据库操作。
 * 当用户上传一篇知识文档后，系统会把文档切分为多个小段落（分块/Chunk），
 * 每个分块存储在此表中，包含分块的文本内容（content_text）、
 * 所属知识库 ID（kb_id）、所属文档 ID 等信息。
 * 这些分块是 RAG 检索的最小单元。
 *
 * 【继承自 BaseMapper】
 *   MyBatis-Plus 的 BaseMapper 已经帮我们自动实现了以下常用方法，不需要写 SQL：
 *     - insert(entity)            插入一条分块记录
 *     - deleteById(id)            根据主键删除分块
 *     - updateById(entity)        根据主键更新分块信息
 *     - selectById(id)            根据主键查询单个分块
 *     - selectList(wrapper)       按条件查询分块列表
 *     - selectPage(page, wrapper) 分页查询分块列表
 *
 * 【自定义方法】
 *   - fulltextSearch(): 利用 MySQL 全文索引做关键词搜索（类似 BM25 打分），
 *     这是 RAG 混合检索（Hybrid Search）中"关键词检索"那一路。
 *     另一路是"向量检索"，通常由向量数据库（如 Milvus）完成。
 *     两路结果合并后返回给大模型作为上下文参考。
 *
 * @author SocialFlow
 */
@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunk> {

    /**
     * 全文检索 —— 利用 MySQL FULLTEXT 索引在知识库分块中搜索关键词
     *
     * 这是 RAG 混合检索中"关键词检索"的实现，与"向量检索"互补，
     * 提高召回率和检索质量。
     *
     * 【SQL 逻辑详解】
     *   1. FROM knowledge_chunk
     *      —— 从知识库分块表中查询
     *
     *   2. WHERE kb_id = #{kbId}
     *      —— 限定只在指定的知识库（kbId）中搜索，避免跨知识库混淆结果
     *
     *   3. AND is_deleted = 0
     *      —— 排除已被逻辑删除的分块（软删除机制，0 表示未删除）
     *
     *   4. AND MATCH(content_text) AGAINST(#{query} IN NATURAL LANGUAGE MODE)
     *      —— 核心：使用 MySQL 的全文索引功能进行搜索
     *         MATCH(content_text) 指定在 content_text 字段上执行全文检索
     *         AGAINST(#{query} IN NATURAL LANGUAGE MODE) 表示以"自然语言模式"
     *         匹配用户的查询关键词。MySQL 会自动对查询词进行分词（使用 ngram 分词器），
     *         并计算每条记录的相关性得分（类似搜索引擎的 BM25 算法）。
     *         只有相关性得分 > 0 的记录才会被返回。
     *
     *   5. ORDER BY MATCH(content_text) AGAINST(#{query} IN NATURAL LANGUAGE MODE) DESC
     *      —— 按相关性得分从高到低排序，最相关的结果排在前面
     *         注意：这里的 MATCH...AGAINST 会被 MySQL 复用第 4 步的计算结果，
     *         不会重复计算，所以性能没有问题。
     *
     *   6. LIMIT #{topK}
     *      —— 只返回前 topK 条最相关的结果（通常 topK = 5~20），
     *         避免返回过多结果浪费资源
     *
     * 【参数说明】
     * @param kbId  知识库 ID —— 指定在哪个知识库中搜索
     * @param query 用户输入的搜索关键词 —— MySQL 会自动分词后去匹配
     * @param topK  最多返回多少条结果 —— 控制返回数量
     * @return 按相关性从高到低排列的分块列表
     */
    @Select("""
            SELECT * FROM knowledge_chunk
            WHERE kb_id = #{kbId}
              AND is_deleted = 0
              AND MATCH(content_text) AGAINST(#{query} IN NATURAL LANGUAGE MODE)
            ORDER BY MATCH(content_text) AGAINST(#{query} IN NATURAL LANGUAGE MODE) DESC
            LIMIT #{topK}
            """)
    List<KnowledgeChunk> fulltextSearch(@Param("kbId") Long kbId,
                                        @Param("query") String query,
                                        @Param("topK") int topK);
}
