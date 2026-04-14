package com.socialflow.common.util;

/**
 * 轻量级Token数量估算工具类
 *
 * 【作用】估算一段文本大约会消耗多少Token。
 *   Token是大语言模型计费的基本单位，每次调用AI的费用与Token数量成正比。
 *
 * 【什么是Token】
 *   Token不等于字符数，也不等于单词数。大模型用一种叫"BPE"的算法把文本切分成Token：
 *   - 中文：大约每个汉字算1个Token
 *   - 英文：大约每4个字符算1个Token（一个常见单词约1-2个Token）
 *
 * 【为什么用估算而不用精确计算】
 *   精确的Token计算需要使用与模型对应的BPE分词器（如jtokkit），
 *   但这里只需要一个大致的数字，用于：
 *   - 护栏检查：判断输入/输出是否超过Token限制
 *   - 配额控制：估算用户本次调用大约消耗多少配额
 *   这种"近似估算"的性能比精确计算快得多，对于上述场景已经够用了。
 *
 * 【精度说明】此估算方法对中文文本比较准确（误差约10%），
 *   对英文文本误差稍大。如需精确计数，请替换为 jtokkit 或模型提供商的SDK。
 *
 * 【使用示例】
 *   int tokens = TokenCountUtil.estimate("这是一段测试文本hello world");
 *   // 中文9个字 ≈ 9 Token + 英文"hello world"(10字符) ≈ 2 Token = 约11 Token
 */
public final class TokenCountUtil {

    /** 私有构造方法，禁止实例化此工具类 */
    private TokenCountUtil() {}

    /**
     * 估算文本的Token数量
     *
     * 【估算规则】
     *   - 中文字符（CJK）：每个字算1个Token
     *   - 英文/数字等ASCII字符：每约4个字符算1个Token（至少算1个）
     *   - 空白字符（空格、换行等）：作为英文单词的分隔符，本身不算Token
     *
     * 【算法流程】
     *   逐个字符扫描文本：
     *   1. 遇到中文字符 → 先结算之前积累的ASCII字符Token数，然后中文字符+1 Token
     *   2. 遇到空白字符 → 结算之前积累的ASCII字符Token数（空白本身不算）
     *   3. 遇到其他字符（英文字母、数字等）→ 累加ASCII连续长度，等后续结算
     *   4. 扫描结束 → 结算最后一段ASCII字符的Token数
     *
     * @param text 要估算的文本
     * @return 估算的Token数量，null或空字符串返回0
     */
    public static int estimate(String text) {
        // 空文本直接返回0
        if (text == null || text.isEmpty()) return 0;

        int tokens = 0;      // Token计数器
        int asciiRun = 0;    // 当前连续ASCII字符的长度（积累中，等待结算）

        // 逐字符扫描整段文本
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (isCjk(c)) {
                // 情况1：遇到中文字符
                if (asciiRun > 0) {
                    // 先把之前积累的ASCII字符结算成Token（每4个字符约1个Token，至少1个）
                    tokens += Math.max(1, asciiRun / 4);
                    asciiRun = 0; // 重置ASCII累计
                }
                tokens++; // 每个中文字符算1个Token
            } else if (Character.isWhitespace(c)) {
                // 情况2：遇到空白字符（空格、Tab、换行等）
                if (asciiRun > 0) {
                    // 空白表示一个英文单词结束，结算之前累积的ASCII字符
                    tokens += Math.max(1, asciiRun / 4);
                    asciiRun = 0; // 重置ASCII累计
                }
                // 空白字符本身不算Token
            } else {
                // 情况3：遇到其他字符（英文字母、数字、标点等）
                asciiRun++; // 累加ASCII连续长度，暂不结算
            }
        }

        // 扫描结束后，如果还有未结算的ASCII字符，进行最后一次结算
        if (asciiRun > 0) tokens += Math.max(1, asciiRun / 4);

        return tokens;
    }

    /**
     * 判断一个字符是否是CJK（中日韩）字符
     *
     * 【判断依据】通过Unicode区块（UnicodeBlock）来判断：
     *   - CJK_UNIFIED_IDEOGRAPHS：CJK统一汉字（最常用的汉字区块，包含2万多个汉字）
     *   - CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A：CJK统一汉字扩展A区（罕用字）
     *   - CJK_SYMBOLS_AND_PUNCTUATION：CJK符号和标点（如、。「」等中文标点）
     *   - HALFWIDTH_AND_FULLWIDTH_FORMS：全角和半角形式（如全角字母Ａ、全角数字１等）
     *
     * @param c 要判断的字符
     * @return true=是CJK字符，false=不是
     */
    private static boolean isCjk(char c) {
        // 获取字符所属的Unicode区块
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        // 判断是否属于CJK相关的区块
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS;
    }
}
