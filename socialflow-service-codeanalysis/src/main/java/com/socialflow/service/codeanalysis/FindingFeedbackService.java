package com.socialflow.service.codeanalysis;

import java.util.List;
import java.util.Set;

/**
 * Finding 反馈聚合服务（Wave 8）。
 *
 * 用户在审查详情页标"无效（INVALID）"的次数会被聚合：
 *   - 某条 ruleRef 累计被标 INVALID >= INVALID_THRESHOLD（默认 3）次 →
 *     加入 prompt 屏蔽列表，下次审查 LLM 不再被引导命中该规约
 *   - 也用于仪表盘"误判 Top 5"展示
 *
 * 数据源：repo_analysis_finding 表的 dismissed_reason='INVALID' 记录。
 * 缓存：内存 dismissedRuleRefs Set，用户标记后异步刷新。
 */
public interface FindingFeedbackService {

    /** 阈值：某 ruleRef 累计标 INVALID >= 此次数即加入屏蔽列表 */
    int INVALID_THRESHOLD = 3;

    /** 当前屏蔽规约编号集合（X.Y.Z），用于 Prompt 注入和 finding 入库时过滤 */
    Set<String> getDismissedRuleRefs();

    /** 重新从 DB 计算屏蔽列表并刷新缓存（用户标记后调用） */
    void refresh();

    /** 误报排行：返回被标 INVALID 次数前 N 的 ruleRef + 次数 */
    List<Item> topInvalid(int limit);

    /** 累计 INVALID 次数 / 累计 IGNORED 次数 / 总 finding 数 → 误判率 */
    long countInvalid();

    long countIgnored();

    long countTotalFindings();

    record Item(String ruleRef, long count) {}
}
