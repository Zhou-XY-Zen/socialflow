package com.socialflow.service.codeanalysis;

/**
 * 代码分析结果导出服务 —— 把原来前端直接 Blob 下载 summaryMd 的功能升级为完整导出。
 *
 * 导出内容包含：
 *   - 元信息（仓库/分支/提交/评分/耗时/Token 消耗）
 *   - summaryMd 全文
 *   - findings 列表（按级别分组）
 *   - mermaid 流程图（仅项目概览）
 *   - 技术栈 / 语言占比（仅项目概览）
 */
public interface CodeAnalysisExportService {

    enum Format {
        /** Markdown，带 findings 表格和 mermaid 代码块 */
        MARKDOWN,
        /** 自包含 HTML，直接浏览器打开；含 Mermaid 外部 CDN */
        HTML,
        /** PDF，可直接打印/归档；中文依赖服务端 CJK 字体配置 */
        PDF
    }

    /** 导出后的文件载荷 */
    record ExportArtifact(String filename, String contentType, byte[] content) {}

    /**
     * 导出分析结果。会做归属校验；若 userId 为 null，仅在 analysisId 的 shareToken 有效时才允许。
     *
     * @param userId     当前登录用户，可为 null（走 share-token 通道）
     * @param analysisId 分析 ID
     * @param format     目标格式
     */
    ExportArtifact export(Long userId, Long analysisId, Format format);

    /** 基于 shareToken 导出（公开访问，沿用 getByShareToken 的过期/计数规则） */
    ExportArtifact exportByShareToken(String shareToken, Format format);
}
