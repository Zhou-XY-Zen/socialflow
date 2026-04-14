package com.socialflow.service.ai.rag.impl;

import com.socialflow.common.util.TokenCountUtil;
import com.socialflow.service.ai.rag.DocumentChunker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 递归字符文本分块器 —— 按分隔符优先级递归切分长文本为适合检索的小段。
 *
 * 【算法概述】
 * 采用"递归字符分块"策略，按照分隔符优先级进行切分：
 * 段落分隔(\n\n) > 换行(\n) > 中文句号(。) > 英文句号(.) > 分号(;) > 逗号(,) > 空格( )
 * 优先在段落边界处切分，保证每个块尽量是一个完整的语义单元。
 *
 * 【处理流程】
 * 1. 如果文本 token 数不超过 chunk-size，直接作为一个块返回
 * 2. 按最高优先级的分隔符切分文本
 * 3. 贪心合并：将切分后的片段逐个合并，直到再加入下一个片段会超过 chunk-size
 * 4. 块间重叠：将前一个块末尾的若干 token 追加到下一个块开头，保持上下文连续
 * 5. 如果没有可用的分隔符，按估算字符位置硬切
 * 6. 后处理：将过小的块（< 50 token）合并到前一个块中
 */
@Service
public class RecursiveDocumentChunker implements DocumentChunker {

    private static final Logger log = LoggerFactory.getLogger(RecursiveDocumentChunker.class);

    /** 每个块的目标 token 数量 */
    @Value("${socialflow.rag.chunk-size:512}")
    private int chunkSize;

    /** 相邻块之间的重叠 token 数量，避免在切分边界处丢失上下文 */
    @Value("${socialflow.rag.chunk-overlap:64}")
    private int chunkOverlap;

    /** 分隔符优先级列表：段落 > 换行 > 中文句号 > 英文句号 > 分号 > 逗号 > 空格 */
    private static final String[] SEPARATORS = {"\n\n", "\n", "。", ".", ";", ",", " "};

    /** 最小块 token 数阈值，低于此值的块会被合并到前一个块 */
    private static final int MIN_CHUNK_TOKENS = 50;

    /**
     * 将原始文本切分为多个块。
     *
     * @param text 原始长文本
     * @return 切分后的块列表，按在原文中的出现顺序排列
     */
    @Override
    public List<Chunk> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        // 第一步：递归切分文本为原始片段
        List<String> rawChunks = recursiveSplit(text, 0);

        // 第二步：后处理 — 合并过小的块（< MIN_CHUNK_TOKENS 个 token）到前一个块
        List<String> mergedChunks = mergeSmallChunks(rawChunks);

        // 第三步：构建最终 Chunk 记录列表，带序号和 token 计数
        List<Chunk> result = new ArrayList<>();
        for (int i = 0; i < mergedChunks.size(); i++) {
            String chunkText = mergedChunks.get(i);
            int tokenCount = TokenCountUtil.estimate(chunkText);
            result.add(new Chunk(i, chunkText, tokenCount));
        }

        log.debug("【文档分块】输入文本长度={}, 分块数={}, chunkSize={}, overlap={}",
                text.length(), result.size(), chunkSize, chunkOverlap);
        return result;
    }

    /**
     * 递归分块核心算法。
     *
     * 从最高优先级的分隔符开始尝试：
     * 1. 如果文本 token 数 <= chunkSize，直接返回
     * 2. 找到文本中存在的最高优先级分隔符
     * 3. 按该分隔符切分文本
     * 4. 贪心合并切分片段，直到添加下一个片段会超过 chunkSize
     * 5. 对超过 chunkSize 的合并结果，递归用下一级分隔符继续切分
     * 6. 在合并后的块之间应用重叠
     *
     * @param text           待切分的文本
     * @param separatorIndex 当前尝试的分隔符索引
     * @return 切分后的文本片段列表
     */
    private List<String> recursiveSplit(String text, int separatorIndex) {
        // 基础情况：文本足够短，直接返回
        int textTokens = TokenCountUtil.estimate(text);
        if (textTokens <= chunkSize) {
            List<String> single = new ArrayList<>();
            if (!text.isBlank()) {
                single.add(text.trim());
            }
            return single;
        }

        // 没有更多分隔符可用，执行硬切分
        if (separatorIndex >= SEPARATORS.length) {
            return hardSplit(text);
        }

        String separator = SEPARATORS[separatorIndex];

        // 如果当前分隔符不存在于文本中，尝试下一个分隔符
        if (!text.contains(separator)) {
            return recursiveSplit(text, separatorIndex + 1);
        }

        // 按分隔符切分文本
        String[] splits = text.split(escapeRegex(separator), -1);

        // 贪心合并：将切分片段逐个合并，直到再加入下一个会超过 chunkSize
        List<String> mergedSegments = greedyMerge(splits, separator);

        // 对合并后的结果进行处理
        List<String> result = new ArrayList<>();
        for (int i = 0; i < mergedSegments.size(); i++) {
            String segment = mergedSegments.get(i);
            int segTokens = TokenCountUtil.estimate(segment);

            if (segTokens <= chunkSize) {
                // 片段大小合适，应用重叠后加入结果
                String withOverlap = applyOverlap(result, segment);
                if (!withOverlap.isBlank()) {
                    result.add(withOverlap.trim());
                }
            } else {
                // 片段仍然过大，用下一级分隔符递归切分
                List<String> subChunks = recursiveSplit(segment, separatorIndex + 1);
                for (int j = 0; j < subChunks.size(); j++) {
                    String subChunk = subChunks.get(j);
                    // 只对子列表中的第一个块应用来自上一级的重叠
                    if (j == 0) {
                        String withOverlap = applyOverlap(result, subChunk);
                        if (!withOverlap.isBlank()) {
                            result.add(withOverlap.trim());
                        }
                    } else {
                        if (!subChunk.isBlank()) {
                            result.add(subChunk.trim());
                        }
                    }
                }
            }
        }

        return result;
    }

    /**
     * 贪心合并：将切分后的片段逐个合并，直到再加入下一个片段会超过 chunkSize。
     *
     * @param splits    按分隔符切分后的字符串数组
     * @param separator 使用的分隔符（合并时需要加回来）
     * @return 合并后的字符串列表
     */
    private List<String> greedyMerge(String[] splits, String separator) {
        List<String> merged = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentTokens = 0;

        for (String split : splits) {
            int splitTokens = TokenCountUtil.estimate(split);
            // 计算加上分隔符后的总 token 数
            int separatorTokens = current.length() > 0 ? TokenCountUtil.estimate(separator) : 0;
            int totalIfAdded = currentTokens + separatorTokens + splitTokens;

            if (current.length() > 0 && totalIfAdded > chunkSize) {
                // 当前累积块已满，保存并开始新块
                merged.add(current.toString());
                current = new StringBuilder(split);
                currentTokens = splitTokens;
            } else {
                // 继续往当前块追加
                if (current.length() > 0) {
                    current.append(separator);
                }
                current.append(split);
                currentTokens = totalIfAdded;
            }
        }

        // 保存最后一个块
        if (current.length() > 0) {
            merged.add(current.toString());
        }

        return merged;
    }

    /**
     * 应用块间重叠：将前一个块末尾的若干字符追加到当前块开头。
     *
     * 重叠的目的是避免在切分边界处丢失上下文信息。例如一个句子被切到两个块中，
     * 通过重叠可以让第二个块也包含第一个块末尾的部分内容。
     *
     * @param previousChunks 已经生成的块列表
     * @param currentText    当前要处理的文本
     * @return 加上重叠前缀后的文本
     */
    private String applyOverlap(List<String> previousChunks, String currentText) {
        if (chunkOverlap <= 0 || previousChunks.isEmpty()) {
            return currentText;
        }

        // 取前一个块的文本
        String prevChunk = previousChunks.get(previousChunks.size() - 1);

        // 从前一个块末尾截取约 chunkOverlap 个 token 的文本作为重叠前缀
        String overlapText = extractTailByTokens(prevChunk, chunkOverlap);
        if (overlapText.isEmpty()) {
            return currentText;
        }

        // 将重叠前缀拼接到当前块开头
        return overlapText + currentText;
    }

    /**
     * 从文本末尾提取约指定 token 数量的文本。
     *
     * 从末尾逐字符向前扫描，直到累积的 token 数达到目标值。
     *
     * @param text       源文本
     * @param targetTokens 目标 token 数量
     * @return 末尾的子串
     */
    private String extractTailByTokens(String text, int targetTokens) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // 估算每个 token 大约对应的字符数（中文约 1 字符/token，英文约 4 字符/token）
        // 取一个保守估计：约 2 字符/token
        int estimatedChars = targetTokens * 2;
        // 确保不超过原文长度
        int startPos = Math.max(0, text.length() - estimatedChars);
        String tail = text.substring(startPos);

        // 微调：如果估算的 token 数偏差较大，逐步调整
        int tailTokens = TokenCountUtil.estimate(tail);
        while (tailTokens > targetTokens && startPos < text.length() - 1) {
            startPos++;
            tail = text.substring(startPos);
            tailTokens = TokenCountUtil.estimate(tail);
        }

        return tail;
    }

    /**
     * 硬切分：当没有任何分隔符可用时，按估算的字符位置强制切分。
     *
     * 根据 token 和字符数的比例关系，估算 chunkSize 个 token 对应的字符位置，
     * 然后在该位置切断文本。
     *
     * @param text 待切分的文本
     * @return 切分后的文本列表
     */
    private List<String> hardSplit(String text) {
        List<String> result = new ArrayList<>();
        int textLen = text.length();
        int totalTokens = TokenCountUtil.estimate(text);

        if (totalTokens == 0) {
            return result;
        }

        // 估算每个 token 对应的平均字符数
        double charsPerToken = (double) textLen / totalTokens;
        // 每个块的估算字符数
        int chunkChars = Math.max(1, (int) (chunkSize * charsPerToken));

        int pos = 0;
        String overlapPrefix = "";

        while (pos < textLen) {
            int end = Math.min(pos + chunkChars, textLen);
            String chunk = overlapPrefix + text.substring(pos, end);

            if (!chunk.isBlank()) {
                result.add(chunk.trim());
            }

            // 计算下一个块的重叠前缀
            if (chunkOverlap > 0 && end < textLen) {
                int overlapChars = Math.max(1, (int) (chunkOverlap * charsPerToken));
                int overlapStart = Math.max(pos, end - overlapChars);
                overlapPrefix = text.substring(overlapStart, end);
            } else {
                overlapPrefix = "";
            }

            pos = end;
        }

        return result;
    }

    /**
     * 后处理：合并过小的块到前一个块中。
     *
     * 如果一个块的 token 数小于 MIN_CHUNK_TOKENS（50），将其合并到前一个块中，
     * 避免产生过于碎片化的小块影响检索质量。
     *
     * @param chunks 原始块列表
     * @return 合并后的块列表
     */
    private List<String> mergeSmallChunks(List<String> chunks) {
        if (chunks.size() <= 1) {
            return chunks;
        }

        List<String> result = new ArrayList<>();
        for (String chunk : chunks) {
            int tokenCount = TokenCountUtil.estimate(chunk);

            if (tokenCount < MIN_CHUNK_TOKENS && !result.isEmpty()) {
                // 当前块过小，合并到前一个块末尾
                int lastIndex = result.size() - 1;
                result.set(lastIndex, result.get(lastIndex) + "\n" + chunk);
            } else {
                result.add(chunk);
            }
        }

        return result;
    }

    /**
     * 转义正则表达式特殊字符，确保分隔符能安全用于 String.split()。
     *
     * @param separator 原始分隔符字符串
     * @return 转义后的正则表达式字符串
     */
    private String escapeRegex(String separator) {
        // 对常见的正则特殊字符进行转义
        return separator
                .replace(".", "\\.")
                .replace("|", "\\|")
                .replace("*", "\\*")
                .replace("+", "\\+")
                .replace("?", "\\?")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("{", "\\{")
                .replace("}", "\\}");
    }
}
