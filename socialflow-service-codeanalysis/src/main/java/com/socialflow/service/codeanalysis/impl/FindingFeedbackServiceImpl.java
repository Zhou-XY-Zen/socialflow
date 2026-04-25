package com.socialflow.service.codeanalysis.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.socialflow.dao.mapper.RepoAnalysisFindingMapper;
import com.socialflow.model.entity.RepoAnalysisFinding;
import com.socialflow.service.codeanalysis.FindingFeedbackService;
import com.socialflow.service.codeanalysis.RuleLibraryHolder;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class FindingFeedbackServiceImpl implements FindingFeedbackService {

    private final RepoAnalysisFindingMapper findingMapper;
    private final RuleLibraryHolder ruleLibraryHolder;

    private static final Pattern RULE_CODE = Pattern.compile("(\\d+\\.\\d+(?:\\.\\d+)?)");

    /** 当前屏蔽规约 X.Y.Z 集合，volatile 一次性替换 */
    private volatile Set<String> dismissedRuleRefs = new HashSet<>();

    @PostConstruct
    public void init() {
        refresh();
    }

    @Override
    public Set<String> getDismissedRuleRefs() {
        return dismissedRuleRefs;
    }

    @Override
    public void refresh() {
        try {
            List<RepoAnalysisFinding> rows = findingMapper.selectList(
                    new LambdaQueryWrapper<RepoAnalysisFinding>()
                            .eq(RepoAnalysisFinding::getDismissedReason, "INVALID")
                            .isNotNull(RepoAnalysisFinding::getRuleRef));
            // 按 ruleRef 抽出 X.Y.Z 编号 → 计数
            Map<String, Long> count = new HashMap<>();
            for (RepoAnalysisFinding r : rows) {
                String code = extractCode(r.getRuleRef());
                if (code == null) continue;
                count.merge(code, 1L, Long::sum);
            }
            Set<String> next = new HashSet<>();
            for (Map.Entry<String, Long> e : count.entrySet()) {
                if (e.getValue() >= INVALID_THRESHOLD) next.add(e.getKey());
            }
            this.dismissedRuleRefs = next;
            log.info("[FindingFeedback] 屏蔽规约刷新: {} 条进入屏蔽列表（阈值={}）",
                    next.size(), INVALID_THRESHOLD);
        } catch (Exception e) {
            log.error("[FindingFeedback] 刷新屏蔽列表失败", e);
        }
    }

    @Override
    public List<Item> topInvalid(int limit) {
        try {
            List<RepoAnalysisFinding> rows = findingMapper.selectList(
                    new LambdaQueryWrapper<RepoAnalysisFinding>()
                            .eq(RepoAnalysisFinding::getDismissedReason, "INVALID")
                            .isNotNull(RepoAnalysisFinding::getRuleRef));
            Map<String, Long> count = new HashMap<>();
            for (RepoAnalysisFinding r : rows) {
                String code = extractCode(r.getRuleRef());
                if (code == null) continue;
                count.merge(code, 1L, Long::sum);
            }
            List<Item> items = new ArrayList<>(count.size());
            for (Map.Entry<String, Long> e : count.entrySet()) {
                items.add(new Item(e.getKey(), e.getValue()));
            }
            items.sort((a, b) -> Long.compare(b.count(), a.count()));
            return items.subList(0, Math.min(limit, items.size()));
        } catch (Exception e) {
            log.warn("[FindingFeedback] topInvalid 失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public long countInvalid() {
        return findingMapper.selectCount(new LambdaQueryWrapper<RepoAnalysisFinding>()
                .eq(RepoAnalysisFinding::getDismissedReason, "INVALID"));
    }

    @Override
    public long countIgnored() {
        return findingMapper.selectCount(new LambdaQueryWrapper<RepoAnalysisFinding>()
                .eq(RepoAnalysisFinding::getStatus, "IGNORED"));
    }

    @Override
    public long countTotalFindings() {
        return findingMapper.selectCount(null);
    }

    private static String extractCode(String ref) {
        if (ref == null) return null;
        Matcher m = RULE_CODE.matcher(ref);
        return m.find() ? m.group(1) : null;
    }
}
