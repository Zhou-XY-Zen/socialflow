package com.socialflow.service.ai.embedding;

import com.pgvector.PGvector;
import jakarta.annotation.PostConstruct;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 基于 pgvector 的向量数据库服务实现。
 *
 * 使用 PostgreSQL + pgvector 扩展实现向量存储和相似度搜索。
 * 通过 JDBC 直接操作 PostgreSQL，利用 pgvector 的余弦距离运算符 <=> 实现语义检索。
 *
 * 【核心功能】
 * - 向量的插入（upsert）和批量插入
 * - 基于元数据的过滤删除
 * - 余弦相似度搜索（利用 HNSW 索引加速）
 */
@Service
public class PgVectorStoreServiceImpl implements VectorStoreService {

    private static final Logger log = LoggerFactory.getLogger(PgVectorStoreServiceImpl.class);

    /** PostgreSQL 主机地址 */
    @Value("${socialflow.vector.host}")
    private String host;

    /** PostgreSQL 端口 */
    @Value("${socialflow.vector.port}")
    private int port;

    /** 向量数据库名称 */
    @Value("${socialflow.vector.database}")
    private String database;

    /** 数据库用户名 */
    @Value("${socialflow.vector.username}")
    private String username;

    /** 数据库密码 */
    @Value("${socialflow.vector.password}")
    private String password;

    /** JDBC 数据源，用于获取数据库连接 */
    private DataSource dataSource;

    /**
     * 初始化 PostgreSQL 数据源。
     *
     * 使用 PGSimpleDataSource 创建简单的连接池（非池化），
     * 适用于文档级别的操作频率。
     */
    @PostConstruct
    public void init() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setServerNames(new String[]{host});
        ds.setPortNumbers(new int[]{port});
        ds.setDatabaseName(database);
        ds.setUser(username);
        ds.setPassword(password);
        this.dataSource = ds;
        log.info("【pgvector】数据源初始化完成, 地址={}:{}, 数据库={}", host, port, database);
    }

    /**
     * 获取数据库连接并注册 pgvector 类型。
     *
     * 每次获取连接后都需要注册 PGvector 类型，使 JDBC 能正确处理 VECTOR 类型的列。
     *
     * @return 已注册 pgvector 类型的数据库连接
     */
    private Connection getConnection() throws Exception {
        Connection conn = dataSource.getConnection();
        // 注册 pgvector 类型，使 PreparedStatement 能正确处理 VECTOR 参数
        PGvector.addVectorType(conn);
        return conn;
    }

    /**
     * 插入单条向量记录到 kb_chunks 表。
     *
     * @param collection 集合名称（对应表名，当前固定为 kb_chunks）
     * @param vector     浮点数向量（1024 维）
     * @param metadata   元数据：必须包含 kb_id, doc_id, chunk_index, source
     * @return 新插入记录的自增主键 ID（字符串形式）
     */
    @Override
    public String upsert(String collection, float[] vector, Map<String, Object> metadata) {
        String sql = "INSERT INTO kb_chunks (kb_id, doc_id, chunk_index, source, embedding) " +
                     "VALUES (?, ?, ?, ?, ?::vector) RETURNING id";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            // 从 metadata 中提取各字段值
            long kbId = toLong(metadata.get("kb_id"));
            long docId = toLong(metadata.get("doc_id"));
            int chunkIndex = toInt(metadata.get("chunk_index"));
            String source = metadata.get("source") != null ? metadata.get("source").toString() : null;

            ps.setLong(1, kbId);
            ps.setLong(2, docId);
            ps.setInt(3, chunkIndex);
            ps.setString(4, source);
            // 使用 PGvector 对象封装浮点向量，pgvector 驱动会自动序列化为 PostgreSQL 的 vector 格式
            ps.setObject(5, new PGvector(vector));

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String id = String.valueOf(rs.getLong("id"));
                    log.debug("【pgvector upsert】插入成功, id={}, kb_id={}, doc_id={}, chunk_index={}",
                            id, kbId, docId, chunkIndex);
                    return id;
                }
            }

            throw new RuntimeException("插入 kb_chunks 未返回 ID");

        } catch (Exception e) {
            log.warn("【pgvector upsert】插入失败: {}", e.getMessage(), e);
            throw new RuntimeException("pgvector 插入向量失败: " + e.getMessage(), e);
        }
    }

    /**
     * 批量插入向量记录。
     *
     * 逐条调用 upsert 方法，适用于文档级别的数据量（通常几十到几百条）。
     *
     * @param collection   集合名称
     * @param vectors      向量列表
     * @param metadataList 元数据列表，与 vectors 一一对应
     * @return 新插入记录的 ID 列表
     */
    @Override
    public List<String> upsertBatch(String collection,
                                    List<float[]> vectors,
                                    List<Map<String, Object>> metadataList) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < vectors.size(); i++) {
            String id = upsert(collection, vectors.get(i), metadataList.get(i));
            ids.add(id);
        }
        log.info("【pgvector upsertBatch】批量插入完成, 数量={}", ids.size());
        return ids;
    }

    /**
     * 根据 ID 列表删除向量记录。
     *
     * @param collection 集合名称
     * @param ids        要删除的记录 ID 列表
     */
    @Override
    public void deleteByIds(String collection, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        // 构建 IN 子句的占位符：(?, ?, ?)
        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(", "));
        String sql = "DELETE FROM kb_chunks WHERE id IN (" + placeholders + ")";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            for (int i = 0; i < ids.size(); i++) {
                ps.setLong(i + 1, Long.parseLong(ids.get(i)));
            }

            int deleted = ps.executeUpdate();
            log.info("【pgvector deleteByIds】删除完成, 请求数={}, 实际删除={}", ids.size(), deleted);

        } catch (Exception e) {
            log.warn("【pgvector deleteByIds】删除失败: {}", e.getMessage(), e);
            throw new RuntimeException("pgvector 按 ID 删除失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据元数据条件批量删除向量记录。
     *
     * 当前支持按 kb_id 过滤删除，用于删除某个知识库下的所有向量。
     *
     * @param collection 集合名称
     * @param filter     过滤条件，如 {kb_id: 2}
     */
    @Override
    public void deleteByFilter(String collection, Map<String, Object> filter) {
        if (filter == null || filter.isEmpty()) {
            log.warn("【pgvector deleteByFilter】过滤条件为空，跳过删除以防止误删全表");
            return;
        }

        // 按 kb_id 过滤删除
        Object kbIdObj = filter.get("kb_id");
        if (kbIdObj == null) {
            log.warn("【pgvector deleteByFilter】filter 中无 kb_id，跳过删除");
            return;
        }

        String sql = "DELETE FROM kb_chunks WHERE kb_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, toLong(kbIdObj));
            int deleted = ps.executeUpdate();
            log.info("【pgvector deleteByFilter】按 kb_id={} 删除完成, 删除数={}", kbIdObj, deleted);

        } catch (Exception e) {
            log.warn("【pgvector deleteByFilter】删除失败: {}", e.getMessage(), e);
            throw new RuntimeException("pgvector 按过滤条件删除失败: " + e.getMessage(), e);
        }
    }

    /**
     * 余弦相似度搜索 —— RAG 检索管道的核心方法。
     *
     * 使用 pgvector 的 <=> 余弦距离运算符，在 HNSW 索引上执行近似最近邻搜索。
     * 相似度得分 = 1 - 余弦距离（得分越高越相似）。
     *
     * 【SQL 说明】
     * - <=> 是 pgvector 的余弦距离运算符，返回值范围 [0, 2]
     * - 1 - (embedding <=> ?) 将距离转换为相似度得分，范围 [-1, 1]
     * - ORDER BY embedding <=> ? 按距离升序排列（距离越小越相似）
     * - LIMIT ? 限制返回结果数量
     *
     * @param collection 集合名称
     * @param query      查询向量（用户问题的嵌入向量）
     * @param filter     过滤条件，必须包含 kb_id 以实现数据隔离
     * @param topK       返回结果数量
     * @return 搜索命中列表，按相似度得分降序排列
     */
    @Override
    public List<SearchHit> search(String collection,
                                  float[] query,
                                  Map<String, Object> filter,
                                  int topK) {
        // SQL 中使用两次 query 向量：一次计算得分，一次用于排序
        String sql = "SELECT id, kb_id, doc_id, chunk_index, " +
                     "1 - (embedding <=> ?::vector) AS score " +
                     "FROM kb_chunks WHERE kb_id = ? " +
                     "ORDER BY embedding <=> ?::vector LIMIT ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            PGvector pgVector = new PGvector(query);

            // 参数 1：用于计算相似度得分的查询向量
            ps.setObject(1, pgVector);
            // 参数 2：kb_id 过滤条件（数据隔离）
            ps.setLong(2, toLong(filter.get("kb_id")));
            // 参数 3：用于排序的查询向量（需要传两次）
            ps.setObject(3, pgVector);
            // 参数 4：返回数量限制
            ps.setInt(4, topK);

            List<SearchHit> hits = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    long kbId = rs.getLong("kb_id");
                    long docId = rs.getLong("doc_id");
                    int chunkIndex = rs.getInt("chunk_index");
                    double score = rs.getDouble("score");

                    // 构建元数据 Map —— 注意：RagPipelineServiceImpl.fuseRrf() 需要 "chunk_id" 键
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("chunk_id", id);       // fuseRrf() 依赖此键提取片段 ID
                    metadata.put("kb_id", kbId);
                    metadata.put("doc_id", docId);
                    metadata.put("chunk_index", chunkIndex);

                    hits.add(new SearchHit(String.valueOf(id), score, metadata));
                }
            }

            log.debug("【pgvector search】搜索完成, kb_id={}, topK={}, 命中数={}",
                    filter.get("kb_id"), topK, hits.size());
            return hits;

        } catch (Exception e) {
            log.warn("【pgvector search】搜索失败: {}", e.getMessage(), e);
            // 搜索失败时返回空列表，不中断 RAG 管道
            return List.of();
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 将 Object 安全转换为 long 值。
     * 支持 Number 类型和 String 类型。
     */
    private long toLong(Object obj) {
        if (obj == null) return 0L;
        if (obj instanceof Number) return ((Number) obj).longValue();
        return Long.parseLong(obj.toString());
    }

    /**
     * 将 Object 安全转换为 int 值。
     * 支持 Number 类型和 String 类型。
     */
    private int toInt(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Number) return ((Number) obj).intValue();
        return Integer.parseInt(obj.toString());
    }
}
