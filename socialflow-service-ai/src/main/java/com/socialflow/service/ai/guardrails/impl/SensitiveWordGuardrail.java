package com.socialflow.service.ai.guardrails.impl;

import com.socialflow.common.enums.GuardrailRule;
import com.socialflow.service.ai.guardrails.Guardrail;
import com.socialflow.service.ai.guardrails.GuardrailContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * 敏感词过滤护栏——基于 DFA（确定性有限自动机）思想的敏感词检测。
 *
 * 【什么是敏感词检测？】
 *
 * 在 AI 生成内容的场景中，需要确保用户输入和模型输出不包含违禁词汇
 * （如涉政、涉黄、广告等敏感词）。敏感词检测就是在文本中查找是否出现了
 * 预定义的违禁词库中的词语。
 *
 * 【什么是 DFA（确定性有限自动机）？】
 *
 * DFA 是一种高效的多模式字符串匹配算法。传统的方式是对词库中每个词逐一
 * 在文本中查找（本实现的简化版），时间复杂度为 O(n * m)，其中 n 是文本长度、
 * m 是词库大小。DFA 将词库构建为一棵字典树（Trie），只需扫描一遍文本即可
 * 同时匹配所有敏感词，时间复杂度为 O(n)。
 * 注意：当前实现使用简单的 Set 遍历匹配，生产环境建议替换为真正的 DFA Trie 实现。
 *
 * 【{@code @PostConstruct} 加载机制】
 *
 * {@code @PostConstruct} 注解标记的方法会在 Spring Bean 创建完成、
 * 所有依赖注入完毕后自动调用一次。本类利用它在服务启动时从文件加载敏感词库，
 * 确保后续检测时词库已就绪。
 *
 * 【在护栏链中的位置】
 *
 * 阶段：INPUT（输入端）；优先级 order=20（在输入长度检查之后执行）。
 */
@Slf4j
@Component
public class SensitiveWordGuardrail implements Guardrail {

    /** 敏感词集合——在应用启动时从文件加载到内存中 */
    private final Set<String> dict = new HashSet<>();

    /** Spring 资源加载器，用于从 classpath 或文件系统加载敏感词字典文件 */
    private final ResourceLoader resourceLoader;

    /**
     * 敏感词字典文件路径。
     *
     * 默认从 classpath 下的 {@code guardrails/sensitive-words.txt} 加载。
     * 可通过配置项 {@code socialflow.ai.guardrails.sensitive-word-dict-path} 自定义。
     */
    @Value("${socialflow.ai.guardrails.sensitive-word-dict-path:classpath:guardrails/sensitive-words.txt}")
    private String dictPath;

    public SensitiveWordGuardrail(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * 应用启动时自动执行——从文件加载敏感词库到内存。
     *
     * {@code @PostConstruct} 确保此方法在 Bean 构造和依赖注入完成后自动调用。
     * 加载逻辑：
     *     - 通过 ResourceLoader 定位字典文件
     *     - 逐行读取，忽略空行和以 # 开头的注释行
     *     - 将每个有效词语加入 HashSet
     * 如果文件不存在或加载失败，护栏降级为空操作（不拦截任何内容）。
     */
    @PostConstruct
    public void load() {
        try {
            // 使用 Spring ResourceLoader 加载资源（支持 classpath: 和 file: 前缀）
            Resource resource = resourceLoader.getResource(dictPath);
            if (!resource.exists()) {
                log.warn("sensitive-word dict not found at {}, guardrail is a no-op", dictPath);
                return;
            }
            // 以 UTF-8 编码逐行读取字典文件
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String w = line.trim();
                    // 跳过空行和注释行（以 # 开头的行视为注释）
                    if (!w.isEmpty() && !w.startsWith("#")) dict.add(w);
                }
            }
            log.info("loaded {} sensitive words", dict.size());
        } catch (Exception e) {
            // 加载失败时记录错误但不抛出异常，避免阻止应用启动
            log.error("failed to load sensitive words", e);
        }
    }

    /** 返回规则枚举：SENSITIVE_WORD（敏感词检测） */
    @Override
    public GuardrailRule rule() { return GuardrailRule.SENSITIVE_WORD; }

    /** 运行阶段：INPUT（输入端） */
    @Override
    public Phase phase() { return Phase.INPUT; }

    /** 执行优先级：20（在输入长度检查 order=10 之后执行） */
    @Override
    public int order() { return 20; }

    /**
     * 评估文本中是否包含敏感词。
     *
     * 遍历词库中的每个敏感词，检查输入文本是否包含它。
     * 一旦命中任何一个敏感词，立即返回 BLOCKED 结果。
     *
     * 性能提示：当前实现是 O(n*m) 的简单遍历，对于大词库和长文本
     * 可能较慢。生产环境建议替换为 DFA Trie（AC 自动机）实现，
     * 可将复杂度降至 O(n)。
     */
    @Override
    public Result evaluate(GuardrailContext ctx) {
        // 如果词库为空（文件未加载）或输入文本为空，直接放行
        if (dict.isEmpty() || ctx.getText() == null) return Result.pass();
        // 遍历词库中的每个敏感词，检查是否在输入文本中出现
        for (String w : dict) {
            if (ctx.getText().contains(w)) {
                // 命中敏感词，返回阻断结果，附带命中的词语
                return Result.blocked("sensitive word hit: " + w);
            }
        }
        // 未命中任何敏感词，放行
        return Result.pass();
    }
}
