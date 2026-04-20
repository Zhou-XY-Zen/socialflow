package com.socialflow.service.codeanalysis.impl;

import com.socialflow.common.exception.BusinessException;
import com.socialflow.model.vo.CodeAnalysisVO;
import com.socialflow.model.vo.CodeFindingVO;
import com.socialflow.model.vo.LanguageStatVO;
import com.socialflow.service.codeanalysis.CodeAnalysisExportService;
import com.socialflow.service.codeanalysis.CodeAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 导出实现 —— 复用 {@link CodeAnalysisService} 读取数据，专注格式化。
 *
 * 设计：
 *   - MD/HTML/JSON 三个渲染方法各自幂等，只吃一个 VO，便于测试
 *   - 所有文本走 UTF-8；HTML 做 HTML escape，避免 summaryMd 里的用户内容注入
 *   - 文件名里带 analysisId + 时间戳，避免下载目录里同名覆盖
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeAnalysisExportServiceImpl implements CodeAnalysisExportService {

    private final CodeAnalysisService codeAnalysisService;

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    @Override
    public ExportArtifact export(Long userId, Long analysisId, Format format) {
        CodeAnalysisVO vo = codeAnalysisService.getAnalysis(userId, analysisId);
        return render(vo, format);
    }

    @Override
    public ExportArtifact exportByShareToken(String shareToken, Format format) {
        CodeAnalysisVO vo = codeAnalysisService.getByShareToken(shareToken);
        return render(vo, format);
    }

    private ExportArtifact render(CodeAnalysisVO vo, Format format) {
        if (vo == null) throw new BusinessException("分析记录不存在");
        String ts = vo.getCreateTime() != null ? vo.getCreateTime().format(TS_FMT) : "now";
        String base = "code-analysis-" + vo.getId() + "-" + ts;
        return switch (format) {
            case MARKDOWN -> new ExportArtifact(base + ".md",
                    "text/markdown; charset=utf-8",
                    renderMarkdown(vo).getBytes(StandardCharsets.UTF_8));
            case HTML -> new ExportArtifact(base + ".html",
                    "text/html; charset=utf-8",
                    renderHtml(vo).getBytes(StandardCharsets.UTF_8));
        };
    }

    // ==================== Markdown ====================

    private String renderMarkdown(CodeAnalysisVO vo) {
        StringBuilder sb = new StringBuilder(8192);
        sb.append("# ").append(typeLabel(vo.getAnalysisType())).append("报告\n\n");

        // 元信息块
        sb.append("## 元信息\n\n");
        appendKv(sb, "仓库地址", vo.getGitUrl());
        appendKv(sb, "分支", vo.getBranch());
        appendKv(sb, "提交 SHA", vo.getCommitSha());
        appendKv(sb, "Base Ref", vo.getBaseRef());
        appendKv(sb, "Head Ref", vo.getHeadRef());
        appendKv(sb, "生成时间", vo.getCreateTime() == null ? null : vo.getCreateTime().toString());
        appendKv(sb, "综合评分", vo.getOverallScore() == null ? null : vo.getOverallScore() + " / 100");
        appendKv(sb, "耗时",
                vo.getDurationMs() == null ? null : (vo.getDurationMs() / 1000.0) + " s");
        appendKv(sb, "Token 消耗", vo.getLlmTokensUsed() == null ? null : vo.getLlmTokensUsed().toString());
        sb.append('\n');

        // 计数行（只对审查类显示）
        if (vo.getHighCount() != null || vo.getMediumCount() != null || vo.getLowCount() != null) {
            sb.append("## 风险统计\n\n");
            sb.append("| 高危 | 中危 | 低危 | 合计 |\n|------|------|------|------|\n");
            int h = zero(vo.getHighCount()), m = zero(vo.getMediumCount()), l = zero(vo.getLowCount());
            sb.append("| ").append(h).append(" | ").append(m).append(" | ").append(l)
                    .append(" | ").append(h + m + l).append(" |\n\n");
        }

        // 技术栈 / 语言（项目概览）
        if (vo.getTechStack() != null && !vo.getTechStack().isEmpty()) {
            sb.append("## 技术栈\n\n");
            for (String t : vo.getTechStack()) sb.append("- ").append(t).append('\n');
            sb.append('\n');
        }
        if (vo.getLanguageStats() != null && !vo.getLanguageStats().isEmpty()) {
            sb.append("## 语言占比\n\n| 语言 | 行数 | 占比 |\n|------|------|------|\n");
            for (LanguageStatVO l : vo.getLanguageStats()) {
                sb.append("| ").append(nvl(l.getLanguage())).append(" | ")
                        .append(l.getTotalLines() == null ? "-" : l.getTotalLines()).append(" | ")
                        .append(l.getPercent() == null ? "-" : String.format("%.1f%%", l.getPercent()))
                        .append(" |\n");
            }
            sb.append('\n');
        }

        // summary
        if (vo.getSummaryMd() != null && !vo.getSummaryMd().isBlank()) {
            sb.append("## 详细总结\n\n").append(vo.getSummaryMd()).append("\n\n");
        }

        // mermaid
        if (vo.getMermaidCode() != null && !vo.getMermaidCode().isBlank()) {
            sb.append("## 核心流程图\n\n```mermaid\n").append(vo.getMermaidCode()).append("\n```\n\n");
        }

        // findings
        List<CodeFindingVO> findings = vo.getFindings();
        if (findings != null && !findings.isEmpty()) {
            sb.append("## 审查发现 (").append(findings.size()).append(" 条)\n\n");
            sb.append("| # | 级别 | 类别 | 文件 | 行号 | 规约 | 状态 | 标题 |\n");
            sb.append("|---|------|------|------|------|------|------|------|\n");
            int idx = 1;
            for (CodeFindingVO f : findings) {
                sb.append("| ").append(idx++).append(" | ")
                        .append(nvl(f.getLevel())).append(" | ")
                        .append(mdEscape(f.getCategory())).append(" | ")
                        .append(mdEscape(f.getFile())).append(" | ")
                        .append(nvl(f.getLineRange())).append(" | ")
                        .append(mdEscape(f.getRuleRef())).append(" | ")
                        .append(nvl(f.getStatus())).append(" | ")
                        .append(mdEscape(f.getTitle())).append(" |\n");
            }
            sb.append("\n### 详情\n\n");
            idx = 1;
            for (CodeFindingVO f : findings) {
                sb.append("#### ").append(idx++).append(". [").append(nvl(f.getLevel())).append("] ")
                        .append(nvl(f.getTitle())).append('\n');
                if (f.getRuleRef() != null && !f.getRuleRef().isBlank()) {
                    sb.append("- **规约**：").append(f.getRuleRef()).append('\n');
                }
                sb.append("- **位置**：").append(nvl(f.getFile())).append(':').append(nvl(f.getLineRange())).append('\n');
                if (f.getDescription() != null && !f.getDescription().isBlank()) {
                    sb.append("- **问题描述**：\n\n").append(f.getDescription()).append("\n\n");
                }
                if (f.getCodeSnippet() != null && !f.getCodeSnippet().isBlank()) {
                    sb.append("- **代码片段**：\n\n```\n").append(f.getCodeSnippet()).append("\n```\n\n");
                }
                if (f.getSuggestion() != null && !f.getSuggestion().isBlank()) {
                    sb.append("- **建议**：\n\n").append(f.getSuggestion()).append("\n\n");
                }
            }
        }

        sb.append("\n---\n_由 SocialFlow 代码分析生成_\n");
        return sb.toString();
    }

    private static void appendKv(StringBuilder sb, String key, String val) {
        if (val == null || val.isBlank()) return;
        sb.append("- **").append(key).append("**：").append(val).append('\n');
    }

    // ==================== HTML ====================

    private String renderHtml(CodeAnalysisVO vo) {
        String title = typeLabel(vo.getAnalysisType()) + "报告 #" + vo.getId();
        String mdBody = renderMarkdown(vo);
        // 用 Mermaid CDN + markdown-it CDN 在浏览器里渲染，保持导出文件小、离线查看时至少能看到原始 markdown
        String html = """
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="utf-8"/>
                  <title>%s</title>
                  <style>
                    body { font-family: -apple-system, "Segoe UI", "PingFang SC", sans-serif;
                           max-width: 980px; margin: 32px auto; padding: 0 20px; color: #1f2937; line-height: 1.7; }
                    h1 { border-bottom: 2px solid #e5e7eb; padding-bottom: 8px; }
                    h2 { color: #111827; margin-top: 28px; }
                    h3 { color: #374151; }
                    h4 { color: #4b5563; margin-top: 18px; }
                    code { background: #f3f4f6; padding: 2px 6px; border-radius: 3px; color: #6d28d9; font-size: 0.92em; }
                    pre { background: #1e293b; color: #e2e8f0; padding: 14px; border-radius: 6px; overflow-x: auto; }
                    pre code { background: transparent; color: inherit; padding: 0; }
                    table { border-collapse: collapse; width: 100%%; margin: 14px 0; font-size: 14px; }
                    th, td { border: 1px solid #e5e7eb; padding: 6px 10px; text-align: left; }
                    th { background: #f9fafb; }
                    .mermaid { background: #f9fafb; padding: 18px; border-radius: 8px; border: 1px solid #e5e7eb; }
                    .badge-HIGH { color: #dc2626; font-weight: 600; }
                    .badge-MEDIUM { color: #d97706; font-weight: 600; }
                    .badge-LOW { color: #2563eb; font-weight: 600; }
                  </style>
                  <script src="https://cdn.jsdelivr.net/npm/markdown-it@13/dist/markdown-it.min.js"></script>
                  <script type="module">
                    import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.esm.min.mjs';
                    window.__mermaid = mermaid;
                  </script>
                </head>
                <body>
                  <div id="src" style="display:none">%s</div>
                  <div id="render"></div>
                  <script>
                    (function(){
                      var md = window.markdownit({ html: false, linkify: true, breaks: true });
                      var src = document.getElementById('src').textContent;
                      document.getElementById('render').innerHTML = md.render(src);
                      // mermaid 代码块渲染
                      var waits = 0;
                      var iv = setInterval(function(){
                        if (window.__mermaid || waits > 20) {
                          clearInterval(iv);
                          if (!window.__mermaid) return;
                          var blocks = document.querySelectorAll('pre code.language-mermaid, pre code[class*="mermaid"]');
                          blocks.forEach(function(b){
                            var div = document.createElement('div');
                            div.className = 'mermaid';
                            div.textContent = b.textContent;
                            b.parentNode.parentNode.replaceChild(div, b.parentNode);
                          });
                          window.__mermaid.initialize({ startOnLoad: true, securityLevel: 'loose' });
                          window.__mermaid.run();
                        }
                        waits++;
                      }, 200);
                    })();
                  </script>
                </body>
                </html>
                """;
        return String.format(html, htmlEscape(title), htmlEscape(mdBody));
    }

    // ==================== utils ====================

    private static String typeLabel(String type) {
        if ("PROJECT_OVERVIEW".equals(type)) return "项目概览";
        if ("COMMIT_REVIEW".equals(type)) return "提交审查";
        if ("DIFF_REVIEW".equals(type)) return "对比分析";
        return "代码分析";
    }

    private static String nvl(String s) { return s == null || s.isBlank() ? "-" : s; }

    private static int zero(Integer v) { return v == null ? 0 : v; }

    private static String mdEscape(String s) {
        if (s == null) return "-";
        return s.replace("|", "\\|").replace("\n", " ");
    }

    private static String htmlEscape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
