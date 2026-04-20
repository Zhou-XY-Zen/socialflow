package com.socialflow.service.codeanalysis;

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
import org.springframework.stereotype.Component;

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
            log.info("[RuleLibrary] JSON 兜底加载完成: {} 条规约, 大类 {} 个", codeSet.size(), byTopCategory.size());
        } catch (Exception e) {
            log.error("[RuleLibrary] 加载 rules/huangshan_rules.json 失败", e);
        }
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
            log.info("[RuleLibrary] DB 重载完成: {} 条 (enabled=1), 大类 {} 个", nextCodes.size(), nextByTop.size());
        } catch (Exception e) {
            log.error("[RuleLibrary] DB 重载失败，保留旧索引", e);
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

    /** 按文件类型挑选可能相关的规约清单（小列表，给 LLM Prompt 当参考）。 */
    public List<RuleEntry> pickForFile(String filePath) {
        if (filePath == null) return List.of();
        List<RuleEntry> result = new ArrayList<>();
        if (filePath.endsWith(".java")) {
            // Java 文件：编程规约 + 异常日志 + 安全规约 + 单元测试
            result.addAll(byTopCategory.getOrDefault("编程规约", List.of()));
            result.addAll(byTopCategory.getOrDefault("异常日志", List.of()));
            result.addAll(byTopCategory.getOrDefault("安全规约", List.of()));
        } else if (filePath.endsWith(".sql") || filePath.contains("/mapper/")) {
            result.addAll(byTopCategory.getOrDefault("MySQL数据库", List.of()));
        } else if (filePath.endsWith(".yml") || filePath.endsWith(".yaml") || filePath.endsWith(".properties")) {
            result.addAll(byTopCategory.getOrDefault("安全规约", List.of()));
        }
        return result;
    }

    public record RuleEntry(String code, String topCategory, String subCategory,
                            String level, String title, String body) {}
}
