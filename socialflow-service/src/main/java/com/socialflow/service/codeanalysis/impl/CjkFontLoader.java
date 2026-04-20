package com.socialflow.service.codeanalysis.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.fontbox.ttf.OTFParser;
import org.apache.fontbox.ttf.OpenTypeFont;
import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 中文字体加载器 —— PDFBox 自带 14 标准字体只覆盖 ASCII，想在 PDF 里显示中文必须嵌入 TTF/OTF/TTC。
 *
 * 按以下优先级尝试加载，找到就嵌入 PDF：
 *   1) 配置项 {@code socialflow.code-analysis.pdf.font-path}
 *   2) 环境变量 {@code SOCIALFLOW_PDF_FONT}
 *   3) 项目 classpath:fonts/cjk.ttf （可选，运维可自行投放）
 *   4) 系统常见路径（Win: simhei.ttf / msyh.ttc；Linux: wqy-* / Noto CJK）
 *
 * 找不到时返回 null，调用方需负责降级（如把非 ASCII 字符替换为 '?'）。
 *
 * 为什么不直接把字体打进 jar：
 *   Noto/思源 等开源字体 10-20 MB，会显著拉大 jar；让运维按部署环境决定更灵活。
 *   项目 README 会补充一段字体部署说明。
 */
@Slf4j
@Component
public class CjkFontLoader {

    @Value("${socialflow.code-analysis.pdf.font-path:}")
    private String configuredPath;

    /** 系统常见 CJK 字体候选路径（TTF/TTC/OTF 均支持） */
    private static final List<String> COMMON_PATHS = List.of(
            // Windows
            "C:/Windows/Fonts/simhei.ttf",
            "C:/Windows/Fonts/msyh.ttc",
            "C:/Windows/Fonts/simsun.ttc",
            // Linux Debian/Ubuntu
            "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
            "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/opentype/noto/NotoSerifCJK-Regular.ttc",
            // Linux CentOS/RHEL/TencentOS —— google-noto-sans-cjk-sc-fonts 只带 OTF，按字重挑
            "/usr/share/fonts/google-noto-cjk/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/google-noto-cjk/NotoSansCJKsc-Regular.otf",
            "/usr/share/fonts/google-noto-cjk/NotoSansCJKsc-Medium.otf",
            "/usr/share/fonts/google-noto-cjk/NotoSansCJKsc-Light.otf",
            "/usr/share/fonts/google-noto-cjk/NotoSansCJKsc-DemiLight.otf",
            "/usr/share/fonts/google-noto-cjk/NotoSansCJKsc-Bold.otf",
            "/usr/share/fonts/google-noto-cjk/NotoSansCJKsc-Black.otf",
            "/usr/share/fonts/wqy-zenhei/wqy-zenhei.ttc",
            // macOS
            "/System/Library/Fonts/PingFang.ttc",
            "/Library/Fonts/Microsoft/SimHei.ttf"
    );

    /**
     * 把 CJK 字体加载到当前 PDDocument；失败返回 null。
     * 按扩展名分发：
     *   - .ttc → {@link TrueTypeCollection} 挑第一个子字体
     *   - .otf → {@link OTFParser}（CFF outlines；PDType0Font.load(doc,File) 默认 TTFParser 解析会失败）
     *   - .ttf → {@code PDType0Font.load(doc, file)}
     */
    public PDType0Font load(PDDocument doc) {
        String path = resolvePath();
        if (path == null) {
            log.warn("[PDF] 未找到可用的中文字体，中文字符会被替换为 '?'。建议配置 socialflow.code-analysis.pdf.font-path 指向 TTF/TTC/OTF 文件");
            return null;
        }
        try {
            File f = new File(path);
            String lower = path.toLowerCase();
            if (lower.endsWith(".ttc")) {
                try (TrueTypeCollection ttc = new TrueTypeCollection(f)) {
                    AtomicReference<TrueTypeFont> picked = new AtomicReference<>();
                    ttc.processAllFonts(ttf -> {
                        if (picked.get() == null) picked.set(ttf);
                    });
                    TrueTypeFont chosen = picked.get();
                    if (chosen == null) {
                        log.warn("[PDF] TTC 集合 {} 未包含任何子字体", path);
                        return null;
                    }
                    // embedSubset=true 只嵌入实际用到的字形，控制 PDF 大小
                    return PDType0Font.load(doc, chosen, true);
                }
            }
            if (lower.endsWith(".otf")) {
                OpenTypeFont otf = new OTFParser().parse(new org.apache.pdfbox.io.RandomAccessReadBufferedFile(f));
                return PDType0Font.load(doc, otf, true);
            }
            return PDType0Font.load(doc, f);
        } catch (Exception e) {
            log.warn("[PDF] 加载中文字体失败 path={}: {}", path, e.getMessage());
            return null;
        }
    }

    /** 按优先级解析字体路径，都找不到返回 null */
    private String resolvePath() {
        if (configuredPath != null && !configuredPath.isBlank() && fileExists(configuredPath)) {
            return configuredPath;
        }
        String env = System.getenv("SOCIALFLOW_PDF_FONT");
        if (env != null && !env.isBlank() && fileExists(env)) return env;

        // classpath 下 fonts/cjk.ttf 放一份备用（运维投放后立即生效）
        try {
            java.net.URL res = getClass().getClassLoader().getResource("fonts/cjk.ttf");
            if (res != null && "file".equals(res.getProtocol())) {
                return Paths.get(res.toURI()).toString();
            }
        } catch (Exception ignored) { /* 非 file 协议（jar 内）则跳过 */ }

        for (String p : COMMON_PATHS) {
            if (fileExists(p)) return p;
        }
        return null;
    }

    private static boolean fileExists(String path) {
        try {
            Path p = Path.of(path);
            return Files.isRegularFile(p);
        } catch (Exception e) {
            return false;
        }
    }
}
