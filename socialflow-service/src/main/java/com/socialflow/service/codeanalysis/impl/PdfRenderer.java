package com.socialflow.service.codeanalysis.impl;

import com.socialflow.common.exception.BusinessException;
import com.socialflow.model.vo.CodeAnalysisVO;
import com.socialflow.model.vo.CodeFindingVO;
import com.socialflow.model.vo.LanguageStatVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * 代码分析结果的 PDF 渲染器（基于 Apache PDFBox 3.x）。
 *
 * 设计目标：
 *   - 排版简洁可读；不追求富文本保真，保证"拿出来给人看"足够
 *   - 支持中文：优先嵌入系统/配置字体（{@link CjkFontLoader}），失败降级 ASCII 并把 CJK 字符替换为 '?'
 *   - 自动分页：每页预留页眉 / 页脚；行溢出时换行，页溢出时新开页
 *
 * 页面结构（自顶向下）：
 *   1. 标题 + 元信息块（仓库/分支/提交/评分/耗时/Token）
 *   2. 风险统计（若为审查类）
 *   3. 技术栈 / 语言占比（若为项目概览）
 *   4. 详细总结（summaryMd 原文）
 *   5. Mermaid 代码（纯文本呈现，提示"图表请查看 HTML 版本"）
 *   6. 审查发现清单（表格 + 每条详情）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PdfRenderer {

    private final CjkFontLoader fontLoader;

    /** A4 页面：595.28 x 841.89 点；以左下为原点 */
    private static final float PAGE_MARGIN = 40f;
    private static final float LINE_HEIGHT = 14f;
    private static final float TITLE_SIZE = 18f;
    private static final float H2_SIZE = 14f;
    private static final float BODY_SIZE = 10f;
    private static final float CODE_SIZE = 9f;

    public byte[] render(CodeAnalysisVO vo) {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024)) {

            PDFont cjk = fontLoader.load(doc);
            // cjk 加载成功 → 正文和代码都用 CJK（牺牲等宽风格换正确显示中文，
            //   因为 Mermaid 源码、代码片段里大概率含中文，用 Helvetica 会抛
            //   IllegalArgumentException: U+XXXX not available in Helvetica）
            // cjk 加载失败 → 全部回退 Helvetica，safeText 把非 ASCII 替换为 '?'
            PDFont fallback = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont body = cjk != null ? cjk : fallback;
            PDFont code = cjk != null ? cjk : fallback;
            Fonts fonts = new Fonts(body, code, cjk != null);

            RenderCtx ctx = new RenderCtx(doc, fonts);
            ctx.openPage();

            writeTitle(ctx, typeLabel(vo.getAnalysisType()) + " 报告 #" + vo.getId());
            writeMeta(ctx, vo);
            writeRiskSummary(ctx, vo);
            writeOverviewStats(ctx, vo);
            writeSummary(ctx, vo);
            writeMermaidNote(ctx, vo);
            writeFindings(ctx, vo);
            writeFooter(ctx);

            ctx.close();
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("[PDF] 渲染失败 analysisId={}", vo.getId(), e);
            throw new BusinessException("导出 PDF 失败: " + e.getMessage());
        }
    }

    // ==================== 各段渲染 ====================

    private void writeTitle(RenderCtx ctx, String title) {
        ctx.drawText(title, ctx.fonts.bodyFont, TITLE_SIZE);
        ctx.y -= 6;
        ctx.drawRule();
        ctx.y -= 6;
    }

    private void writeMeta(RenderCtx ctx, CodeAnalysisVO vo) {
        ctx.drawHeading("元信息");
        appendKv(ctx, "仓库地址", vo.getGitUrl());
        appendKv(ctx, "分支", vo.getBranch());
        appendKv(ctx, "提交 SHA", vo.getCommitSha());
        appendKv(ctx, "Base Ref", vo.getBaseRef());
        appendKv(ctx, "Head Ref", vo.getHeadRef());
        appendKv(ctx, "生成时间", vo.getCreateTime() == null ? null : vo.getCreateTime().toString());
        appendKv(ctx, "综合评分", vo.getOverallScore() == null ? null : vo.getOverallScore() + " / 100");
        appendKv(ctx, "耗时", vo.getDurationMs() == null ? null : (vo.getDurationMs() / 1000.0) + " s");
        appendKv(ctx, "Token 消耗", vo.getLlmTokensUsed() == null ? null : vo.getLlmTokensUsed().toString());
        ctx.y -= 6;
    }

    private void writeRiskSummary(RenderCtx ctx, CodeAnalysisVO vo) {
        if (vo.getHighCount() == null && vo.getMediumCount() == null && vo.getLowCount() == null) return;
        ctx.drawHeading("风险统计");
        int h = nz(vo.getHighCount()), m = nz(vo.getMediumCount()), l = nz(vo.getLowCount());
        ctx.drawText(String.format("高危 %d    中危 %d    低危 %d    合计 %d", h, m, l, h + m + l),
                ctx.fonts.bodyFont, BODY_SIZE);
        ctx.y -= 6;
    }

    private void writeOverviewStats(RenderCtx ctx, CodeAnalysisVO vo) {
        if (vo.getTechStack() != null && !vo.getTechStack().isEmpty()) {
            ctx.drawHeading("技术栈");
            ctx.drawText("- " + String.join("、", vo.getTechStack()), ctx.fonts.bodyFont, BODY_SIZE);
            ctx.y -= 4;
        }
        if (vo.getLanguageStats() != null && !vo.getLanguageStats().isEmpty()) {
            ctx.drawHeading("语言占比");
            for (LanguageStatVO l : vo.getLanguageStats()) {
                String line = String.format("  %-10s  %8s 行   %5.1f%%",
                        nvl(l.getLanguage()),
                        l.getTotalLines() == null ? "-" : l.getTotalLines().toString(),
                        l.getPercent() == null ? 0.0 : l.getPercent());
                ctx.drawText(line, ctx.fonts.bodyFont, BODY_SIZE);
            }
            ctx.y -= 4;
        }
    }

    private void writeSummary(RenderCtx ctx, CodeAnalysisVO vo) {
        if (vo.getSummaryMd() == null || vo.getSummaryMd().isBlank()) return;
        ctx.drawHeading("详细总结");
        // 直接把 markdown 原文按行写入，保留标题井号；不做 markdown 富文本解析
        for (String line : vo.getSummaryMd().split("\r?\n")) {
            ctx.drawWrappedText(line, ctx.fonts.bodyFont, BODY_SIZE);
        }
        ctx.y -= 4;
    }

    private void writeMermaidNote(RenderCtx ctx, CodeAnalysisVO vo) {
        if (vo.getMermaidCode() == null || vo.getMermaidCode().isBlank()) return;
        ctx.drawHeading("核心流程图 (Mermaid 源码)");
        ctx.drawText("提示：图形化请查看 HTML 版导出文件。以下为源码：", ctx.fonts.bodyFont, BODY_SIZE);
        ctx.y -= 2;
        for (String line : vo.getMermaidCode().split("\r?\n")) {
            ctx.drawWrappedText(line, ctx.fonts.codeFont, CODE_SIZE);
        }
        ctx.y -= 4;
    }

    private void writeFindings(RenderCtx ctx, CodeAnalysisVO vo) {
        List<CodeFindingVO> findings = vo.getFindings();
        if (findings == null || findings.isEmpty()) return;

        ctx.drawHeading("审查发现 (" + findings.size() + " 条)");
        int idx = 1;
        for (CodeFindingVO f : findings) {
            String header = String.format("[%d] %s  %s  %s:%s",
                    idx++, nvl(f.getLevel()), nvl(f.getCategory()), nvl(f.getFile()), nvl(f.getLineRange()));
            ctx.drawWrappedText(header, ctx.fonts.bodyFont, BODY_SIZE);
            if (f.getTitle() != null && !f.getTitle().isBlank()) {
                ctx.drawWrappedText("标题: " + f.getTitle(), ctx.fonts.bodyFont, BODY_SIZE);
            }
            if (f.getRuleRef() != null && !f.getRuleRef().isBlank()) {
                ctx.drawWrappedText("规约: " + f.getRuleRef(), ctx.fonts.bodyFont, BODY_SIZE);
            }
            if (f.getDescription() != null && !f.getDescription().isBlank()) {
                ctx.drawWrappedText("问题: " + f.getDescription(), ctx.fonts.bodyFont, BODY_SIZE);
            }
            if (f.getSuggestion() != null && !f.getSuggestion().isBlank()) {
                ctx.drawWrappedText("建议: " + f.getSuggestion(), ctx.fonts.bodyFont, BODY_SIZE);
            }
            if (f.getCodeSnippet() != null && !f.getCodeSnippet().isBlank()) {
                ctx.drawText("代码片段:", ctx.fonts.bodyFont, BODY_SIZE);
                for (String line : f.getCodeSnippet().split("\r?\n")) {
                    ctx.drawWrappedText("  " + line, ctx.fonts.codeFont, CODE_SIZE);
                }
            }
            ctx.y -= 4;
        }
    }

    private void writeFooter(RenderCtx ctx) {
        // 在每页末尾右下角写页码（简化：只在末页写）
        ctx.y = 20;
        ctx.drawText("— 由 SocialFlow 代码分析生成 —", ctx.fonts.bodyFont, 8);
    }

    // ==================== utils ====================

    private static void appendKv(RenderCtx ctx, String key, String val) {
        if (val == null || val.isBlank()) return;
        ctx.drawWrappedText(key + ": " + val, ctx.fonts.bodyFont, BODY_SIZE);
    }

    private static String typeLabel(String type) {
        if ("PROJECT_OVERVIEW".equals(type)) return "项目概览";
        if ("COMMIT_REVIEW".equals(type)) return "提交审查";
        if ("DIFF_REVIEW".equals(type)) return "对比分析";
        return "代码分析";
    }

    private static String nvl(String s) { return s == null || s.isBlank() ? "-" : s; }

    private static int nz(Integer v) { return v == null ? 0 : v; }

    // ==================== 内部排版上下文 ====================

    /** 字体集：正文 + 等宽代码字体；hasCjk 控制非 ASCII 字符是否需要转义 */
    private record Fonts(PDFont bodyFont, PDFont codeFont, boolean hasCjk) {}

    /** 排版上下文（非线程安全，一次 render 期间独占） */
    private static final class RenderCtx implements AutoCloseable {
        final PDDocument doc;
        final Fonts fonts;
        PDPage page;
        PDPageContentStream cs;
        float y;
        final float pageWidth = PDRectangle.A4.getWidth();
        final float pageHeight = PDRectangle.A4.getHeight();
        final float contentWidth = pageWidth - 2 * PAGE_MARGIN;

        RenderCtx(PDDocument doc, Fonts fonts) {
            this.doc = doc;
            this.fonts = fonts;
        }

        void openPage() throws Exception {
            if (cs != null) cs.close();
            page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            cs = new PDPageContentStream(doc, page);
            y = pageHeight - PAGE_MARGIN;
        }

        /** 保证当前页剩余高度 >= need；否则开新页 */
        void ensureSpace(float need) throws Exception {
            if (y - need < PAGE_MARGIN) openPage();
        }

        void drawHeading(String text) {
            y -= 4;
            drawText(text, fonts.bodyFont, H2_SIZE);
            y -= 2;
        }

        void drawRule() {
            try {
                ensureSpace(2);
                cs.setLineWidth(0.5f);
                cs.moveTo(PAGE_MARGIN, y);
                cs.lineTo(pageWidth - PAGE_MARGIN, y);
                cs.stroke();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        void drawText(String raw, PDFont font, float fontSize) {
            try {
                String safe = safeText(raw);
                ensureSpace(fontSize + 2);
                cs.beginText();
                cs.setFont(font, fontSize);
                cs.newLineAtOffset(PAGE_MARGIN, y - fontSize);
                cs.showText(safe);
                cs.endText();
                y -= (fontSize + 4);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        /** 按内容宽度自动折行 + 分页 */
        void drawWrappedText(String raw, PDFont font, float fontSize) {
            String safe = safeText(raw);
            if (safe.isEmpty()) {
                y -= (fontSize + 2);
                return;
            }
            List<String> lines = wrap(safe, font, fontSize, contentWidth);
            for (String line : lines) drawText(line, font, fontSize);
        }

        /** 按字符宽度折行（简单算法：累加字宽到接近 maxWidth 时切） */
        private static List<String> wrap(String text, PDFont font, float fontSize, float maxWidth) {
            List<String> out = new java.util.ArrayList<>();
            StringBuilder cur = new StringBuilder();
            float curW = 0;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '\t') c = ' ';
                float cw;
                try {
                    cw = font.getStringWidth(String.valueOf(c)) / 1000f * fontSize;
                } catch (Exception e) {
                    cw = fontSize * 0.6f;
                }
                if (curW + cw > maxWidth && cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                    curW = 0;
                }
                cur.append(c);
                curW += cw;
            }
            if (cur.length() > 0) out.add(cur.toString());
            return out;
        }

        /** 若字体不支持 CJK，把非 ASCII 字符替换为 '?'，防止 PDFBox 抛 IllegalArgumentException */
        String safeText(String s) {
            if (s == null) return "";
            if (fonts.hasCjk) return s;
            StringBuilder sb = new StringBuilder(s.length());
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                sb.append(c < 0x80 ? c : '?');
            }
            return sb.toString();
        }

        @Override
        public void close() throws Exception {
            if (cs != null) cs.close();
        }
    }
}
