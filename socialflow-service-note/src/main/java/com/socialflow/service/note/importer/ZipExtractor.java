package com.socialflow.service.note.importer;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * ZIP 解包 + Notion/Obsidian 适配
 *
 * 设计：流式逐 entry 写入临时目录，**不在内存里同时持有所有文件字节**，
 * 避免大 ZIP（如 1GB Notion 导出）一次撑爆 JVM。
 *
 * - 自动剥离 Notion 文件名末尾的 32 位 UUID 后缀
 * - 跳过 __MACOSX/、.DS_Store、.git/ 等噪音
 * - 仅写出"支持的扩展名"条目；图片等附件由后续 ImageExtractor 处理
 */
@Component
public class ZipExtractor {

    @Data
    @AllArgsConstructor
    public static class ZipEntryFile {
        private String path;       // 相对路径（保留文件夹层级，仅做展示）
        private String fileName;   // 干净的文件名（去 Notion UUID）
        private long size;
        private Path tempPath;     // 落到磁盘的临时文件
    }

    /**
     * 把 zip 内的支持文件流式落到 outDir
     * @return 每个落地文件的元信息
     */
    public List<ZipEntryFile> extract(InputStream zipStream, Path outDir, Set<String> supportedExts) throws IOException {
        Files.createDirectories(outDir);
        List<ZipEntryFile> out = new ArrayList<>();
        try (ZipInputStream zin = new ZipInputStream(zipStream)) {
            ZipEntry e;
            int seq = 0;
            while ((e = zin.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String name = e.getName();
                if (isJunk(name)) continue;
                String ext = extOf(name);
                if (!supportedExts.contains(ext)) continue;

                String cleanName = stripNotionUuid(baseName(name));
                String suffix = ext.isEmpty() ? "" : "." + ext;
                Path tmp = outDir.resolve("e" + (seq++) + suffix);
                long size;
                try (OutputStream fos = Files.newOutputStream(tmp)) {
                    size = zin.transferTo(fos);
                }
                out.add(new ZipEntryFile(name, cleanName + suffix, size, tmp));
            }
        }
        return out;
    }

    private static boolean isJunk(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        return p.startsWith("__macosx/") || p.endsWith(".ds_store")
               || p.contains("/.git/") || p.endsWith("/.git")
               || p.endsWith(".keep");
    }

    private static String extOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String baseName(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String base = slash < 0 ? path : path.substring(slash + 1);
        int dot = base.lastIndexOf('.');
        return dot < 0 ? base : base.substring(0, dot);
    }

    /** Notion 导出的文件名形如 "标题 32位hex" —— 把尾部 32 hex 字符剥掉 */
    static String stripNotionUuid(String base) {
        if (base.length() < 33) return base;
        String tail = base.substring(base.length() - 32);
        if (tail.matches("[0-9a-fA-F]{32}")) {
            return base.substring(0, base.length() - 32).trim();
        }
        return base;
    }
}
