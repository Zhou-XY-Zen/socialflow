package com.socialflow.service.codeanalysis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时把 classpath 的 huangshan_rules.json upsert 进 rule_library 表。
 *
 * - 首次启动：表为空 → 全部 INSERT
 * - 升级 PDF 时（rules JSON 替换为新版）：现有行 UPDATE 内容字段，但保留 enabled / is_custom
 * - @Order 设置较晚，避免和数据库连接初始化冲突
 *
 * 完成后，RuleLibraryHolder 会从 DB 重新加载到内存索引（DB 是 SOT，内存是缓存）。
 */
@Slf4j
@Component
@Order(1000)
@RequiredArgsConstructor
public class RuleLibraryInitRunner implements ApplicationRunner {

    private final RuleLibraryService ruleLibraryService;
    private final RuleLibraryHolder ruleLibraryHolder;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int n = ruleLibraryService.upsertFromJsonResource("rules/huangshan_rules.json");
            log.info("[RuleLibraryInit] JSON upsert 完成 = {} 条", n);
            ruleLibraryHolder.reloadFromDb();
        } catch (Exception e) {
            log.error("[RuleLibraryInit] 启动初始化失败 - 继续保持内存 holder 的 JSON 数据", e);
        }
    }
}
