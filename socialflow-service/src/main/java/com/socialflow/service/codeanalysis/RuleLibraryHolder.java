package com.socialflow.service.codeanalysis;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.socialflow.common.util.JsonUtil;
import com.socialflow.dao.mapper.RuleLibraryItemMapper;
import com.socialflow.model.entity.RuleLibraryItem;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 阿里巴巴《Java 开发手册（黄山版）》321 条规约的内存索引。
 *
 * 启动时一次性从 classpath 加载 rules/huangshan_rules.json，构建：
 *   - codeSet：所有规约编号集合，用于 ruleRef 白名单校验
 *   - byTopCategory：按大类分组的规约清单，用于按文件类型挑选注入 LLM Prompt
 *
 * 用途：
 *   方案 B —— LLM 输出的 ruleRef 必须形如 "X.Y.Z" 且存在于 codeSet，否则 finding 丢弃
 *   方案 G（后续）—— 按文件类型注入相关规约清单到 Prompt
 */
@Slf4j
@Component
public class RuleLibraryHolder {

    @Getter
    private volatile Set<String> codeSet = new HashSet<>();

    @Getter
    private volatile Map<String, List<RuleEntry>> byTopCategory = new HashMap<>();

    @Getter
    private volatile List<RuleEntry> all = new ArrayList<>();

    /**
     * 预构建的"文件类型 → 规约清单"倒排索引。键形如 {@code "ext:.java"} / {@code "path:/mapper/"}。
     * 随 {@link #load()} 和 {@link #reloadFromDb()} 同步重建。
     */
    private volatile Map<String, List<RuleEntry>> byFileType = new HashMap<>();

    /** Mapper 通过 setter 可选注入；DB 不可用时仍走 JSON 兜底 */
    @Autowired(required = false)
    private RuleLibraryItemMapper ruleLibraryItemMapper;

    /** 启动时先从 JSON 加载（保证 InitRunner 之前 Holder 已就绪）；InitRunner 之后会触发 reloadFromDb */
    @PostConstruct
    public void load() {
        try (InputStream in = new ClassPathResource("rules/huangshan_rules.json").getInputStream()) {
            JsonNode root = JsonUtil.mapper().readTree(in);
            JsonNode arr = root.path("rules");
            if (!arr.isArray()) {
                log.warn("[RuleLibrary] rules/huangshan_rules.json 格式异常，rules 字段不是数组");
                return;
            }
            for (JsonNode r : arr) {
                String code = r.path("code").asText("");
                String top = r.path("topCategory").asText("");
                String sub = r.path("subCategory").asText("");
                String level = r.path("level").asText("");
                String title = r.path("title").asText("");
                String body = r.path("body").asText("");
                if (code.isBlank()) continue;
                codeSet.add(code);
                RuleEntry e = new RuleEntry(code, top, sub, level, title, body);
                all.add(e);
                byTopCategory.computeIfAbsent(top, k -> new ArrayList<>()).add(e);
            }
            this.byFileType = buildFileTypeIndex(byTopCategory);
            log.info("[RuleLibrary] JSON 兜底加载完成: {} 条规约, 大类 {} 个", codeSet.size(), byTopCategory.size());
        } catch (JacksonException e) {
            log.error("[RuleLibrary] rules/huangshan_rules.json 解析失败（JSON 格式错误）", e);
        } catch (IOException e) {
            log.error("[RuleLibrary] 读取 rules/huangshan_rules.json 失败（IO 错误）", e);
        }
    }

    /**
     * 基于大类分组构建"文件类型 → 规约清单"倒排索引。
     * 规约条目数固定、加载/重载不频繁，提前一次性预计算，热点查询 O(1) 命中。
     */
    private static Map<String, List<RuleEntry>> buildFileTypeIndex(Map<String, List<RuleEntry>> byTop) {
        Map<String, List<RuleEntry>> idx = new HashMap<>();
        idx.put("ext:.java", concat(
                byTop.getOrDefault("编程规约", List.of()),
                byTop.getOrDefault("异常日志", List.of()),
                byTop.getOrDefault("安全规约", List.of())));
        List<RuleEntry> sql = byTop.getOrDefault("MySQL数据库", List.of());
        idx.put("ext:.sql", sql);
        idx.put("path:/mapper/", sql);
        List<RuleEntry> sec = byTop.getOrDefault("安全规约", List.of());
        idx.put("ext:.yml", sec);
        idx.put("ext:.yaml", sec);
        idx.put("ext:.properties", sec);
        return idx;
    }

    @SafeVarargs
    private static List<RuleEntry> concat(List<RuleEntry>... lists) {
        int total = 0;
        for (List<RuleEntry> l : lists) total += l.size();
        List<RuleEntry> out = new ArrayList<>(total);
        for (List<RuleEntry> l : lists) out.addAll(l);
        return out;
    }

    /**
     * 启动后由 RuleLibraryInitRunner 调用：从 DB 重建内存索引。
     * 仅加载 enabled=1 的规约（禁用的规约审查时不参与 Prompt 注入也不在白名单）。
     * 同步重建，使用 volatile 字段一次性替换，对正在跑的审查没有影响。
     */
    public void reloadFromDb() {
        if (ruleLibraryItemMapper == null) {
            log.warn("[RuleLibrary] mapper 不可用，保持 JSON 兜底数据");
            return;
        }
        try {
            List<RuleLibraryItem> rows = ruleLibraryItemMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RuleLibraryItem>()
                            .eq(RuleLibraryItem::getEnabled, 1));
            Set<String> nextCodes = new HashSet<>(rows.size() * 2);
            Map<String, List<RuleEntry>> nextByTop = new HashMap<>();
            List<RuleEntry> nextAll = new ArrayList<>(rows.size());
            for (RuleLibraryItem r : rows) {
                if (r.getCode() == null || r.getCode().isBlank()) continue;
                nextCodes.add(r.getCode());
                RuleEntry e = new RuleEntry(r.getCode(), r.getTopCategory(),
                        r.getSubCategory() == null ? "" : r.getSubCategory(),
                        r.getLevel(), r.getTitle(), r.getBody() == null ? "" : r.getBody());
                nextAll.add(e);
                nextByTop.computeIfAbsent(e.topCategory(), k -> new ArrayList<>()).add(e);
            }
            // 一次性替换（volatile 保证可见性）
            this.codeSet = nextCodes;
            this.byTopCategory = nextByTop;
            this.all = nextAll;
            this.byFileType = buildFileTypeIndex(nextByTop);
            log.info("[RuleLibrary] DB 重载完成: {} 条 (enabled=1), 大类 {} 个", nextCodes.size(), nextByTop.size());
        } catch (DataAccessException e) {
            log.error("[RuleLibrary] DB 重载失败（数据库访问异常），保留旧索引", e);
        } catch (RuntimeException e) {
            // 其他运行时异常（如索引重建中的非预期错误）：记录但不抛 —— 旧索引仍可用
            log.error("[RuleLibrary] DB 重载意外失败，保留旧索引", e);
        }
    }

    /** ruleRef 白名单校验。LLM 必须输出 "X.Y.Z" 形式的真实存在编号，否则丢弃 finding。 */
    public boolean isValidRuleRef(String ref) {
        if (ref == null || ref.isBlank()) return false;
        // 从字符串里抽取第一个形如 "X.Y" 或 "X.Y.Z" 的编号
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+\\.\\d+(?:\\.\\d+)?)").matcher(ref);
        if (!m.find()) return false;
        return codeSet.contains(m.group(1));
    }

    /** 按文件类型挑选可能相关的规约清单（小列表，给 LLM Prompt 当参考）。走预建倒排索引，O(1) 命中。 */
    public List<RuleEntry> pickForFile(String filePath) {
        if (filePath == null) return List.of();
        Map<String, List<RuleEntry>> idx = this.byFileType;
        if (filePath.endsWith(".java")) return idx.getOrDefault("ext:.java", List.of());
        if (filePath.endsWith(".sql")) return idx.getOrDefault("ext:.sql", List.of());
        if (filePath.contains("/mapper/")) return idx.getOrDefault("path:/mapper/", List.of());
        if (filePath.endsWith(".yml")) return idx.getOrDefault("ext:.yml", List.of());
        if (filePath.endsWith(".yaml")) return idx.getOrDefault("ext:.yaml", List.of());
        if (filePath.endsWith(".properties")) return idx.getOrDefault("ext:.properties", List.of());
        return List.of();
    }

    public record RuleEntry(String code, String topCategory, String subCategory,
                            String level, String title, String body) {}
}
