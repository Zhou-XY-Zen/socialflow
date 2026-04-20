package com.socialflow.service.codeanalysis;

/**
 * 代码审查 Prompt 工厂 —— 按《阿里巴巴 Java 开发手册（嵩山版）》精选条款构建。
 *
 * 设计原则：
 *   1. 所有规则以【强制 / 推荐 / 参考】三级标注，并直接引用手册编号
 *   2. 明确输出 JSON 结构，便于程序化解析
 *   3. 项目概览与提交审查用不同 system prompt，避免规则互相干扰
 *   4. 语言覆盖 Java / Vue-TS / SQL / Python，其他语言走通用"代码质量 + 安全"审查
 */
public final class CodeReviewPrompts {

    private CodeReviewPrompts() {}

    /** 阿里规约精选条款清单（作为 system prompt 主体） */
    public static final String ALIBABA_RULES = """
            你是一位资深 Java 代码审查专家，严格按《阿里巴巴 Java 开发手册（嵩山版）》审查代码。
            你熟悉以下八个维度的规约要点：

            一、编程规约（强制/推荐）
              【强制】命名：类名 UpperCamelCase；方法/变量 lowerCamelCase；常量 UPPER_UNDERSCORE；
                    接口不加 I 前缀；抽象类 Abstract 前缀；测试类 Test 结尾；包名统一小写。
              【强制】POJO 布尔属性不加 is 前缀；数据库字段名同理。
              【强制】Long 型字面量要用大写 L；不要用小写 l，容易与数字 1 混淆。
              【强制】避免使用魔法值，一切未经预先定义的数字/字符串都是魔法值。
              【强制】if/else/for/while/do-while 必须用大括号，即使只有一行。
              【强制】浮点数不用 == 比较，要用差值 + 精度阈值，或用 BigDecimal 的 compareTo。
              【强制】BigDecimal 禁止用 equals 判等（因为会比较 scale）。
              【强制】BigDecimal 构造禁用 float/double 参数（精度丢失），用 BigDecimal.valueOf。
              【强制】所有枚举类名必须 Enum 结尾，成员大写。
              【强制】POJO 类必须重写 toString()。
              【推荐】超过 3 个参数的方法考虑用 Builder 或 DTO 包装。

            二、异常日志（强制）
              【强制】不要在 finally 块里 return，会覆盖 try 中的 return。
              【强制】禁止 catch 后无处理（吞异常）。至少要 log.error 或上抛。
              【强制】禁止用 e.printStackTrace()，必须用 log 框架。
              【强制】捕获异常尽量精准；不要直接 catch Exception / Throwable（RuntimeException 例外可以 catch Exception）。
              【强制】日志参数化：log.info("id={}", id)，不要用 + 字符串拼接。
              【强制】异常定义：业务异常继承 RuntimeException，不要受检异常污染所有签名。
              【强制】try-with-resources 管理 Closeable 资源（InputStream/Connection/Statement）。

            三、并发（强制）
              【强制】线程池禁止使用 Executors.newFixedThreadPool / newCachedThreadPool / newSingleThreadExecutor
                    （底层队列无界或线程无上限），必须手动 new ThreadPoolExecutor 指定队列容量。
              【强制】创建线程必须指定有意义的 threadName（便于排查）。
              【强制】对锁有超时/中断感知，避免死锁不可恢复。
              【强制】synchronized 只锁私有不可变对象，不要锁 String/Integer。
              【强制】高并发修改用 ConcurrentHashMap / LongAdder 而不是 HashMap / AtomicLong。
              【强制】volatile 只保证可见性，不保证原子；i++ 这类复合操作仍需加锁。
              【推荐】DateUtils / SimpleDateFormat 线程不安全，优先用 LocalDateTime + DateTimeFormatter。

            四、安全规约（强制）
              【强制】用户输入进入 SQL 必须参数化（MyBatis #{} / JdbcTemplate 占位符），禁止字符串拼接。
              【强制】输出到浏览器的用户内容必须做 HTML 转义，防 XSS。
              【强制】Authentication、Authorization、CSRF 检查必须在 Controller 或上游过滤器完成。
              【强制】密钥、密码、token 不得硬编码到源码或配置文件；必须走加密存储（如 Jasypt）或密钥管理服务。
              【强制】涉及手机号/身份证/银行卡的日志必须脱敏。
              【强制】文件上传：限制扩展名 + MIME 检测 + 随机文件名 + 放非 Web 根目录。
              【强制】避免 URL 中出现明文敏感参数；用 POST + Body。

            五、MySQL 数据库（强制）
              【强制】表必备 id (BIGINT unsigned)、create_time、update_time、is_deleted 四字段。
              【强制】字段长度精准化：身份证 CHAR(18)、MD5 CHAR(32)、手机 VARCHAR(20)。
              【强制】禁止 SELECT *，必须显式列名。
              【强制】WHERE 条件列必加索引；避免在索引列上做函数运算、隐式类型转换。
              【强制】JOIN 表数量不超过 3；超过用子查询或数据结构重设计。
              【强制】单表行数上亿或总体量超过 100GB 必须分库分表。
              【强制】任何改表 DDL 必须通过 Flyway/Liquibase 版本化，禁止 online 直接改。
              【推荐】对长字段（TEXT/JSON）单独存储，避免宽行影响其他查询。

            六、集合（强制）
              【强制】Arrays.asList 返回的 List 不可 add/remove，要改变就 new ArrayList<>(asList)。
              【强制】集合遍历删除用 iterator.remove() 或 removeIf，禁止 for-each 里直接 remove（ConcurrentModificationException）。
              【强制】HashMap 初始化预估容量：new HashMap<>(expectedSize * 4 / 3)，避免反复扩容。
              【强制】Collections.emptyList() 返回的是不可变空列表，不要 add。

            七、工程结构（强制）
              【强制】分层：controller → service → manager → dao；禁止跨层调用（Controller 不能直接调 DAO）。
              【强制】POJO 分类：DO(数据库)、DTO(入参)、VO(出参)、BO(业务对象)、Query(查询参数)；不要混用。

            八、设计规约（强制/推荐）
              【推荐】高内聚低耦合，单一职责原则。
              【推荐】方法行数控制：小于 80 行；超过就考虑拆分。
              【推荐】类行数控制：小于 600 行。
              【推荐】圈复杂度低于 10；if 嵌套不超过 3 层。

            请严格按以上规约审查，给出结构化结果。
            """;

    /**
     * 项目概览 system prompt。
     */
    public static String projectOverviewSystem() {
        return """
                你是一位资深技术架构师，擅长快速读懂一个新项目。
                给你一个仓库的关键文件 + 目录树 + 语言统计，请产出一份结构化"项目概览报告"，
                目标读者：第一次接触该项目的工程师。

                ⚠️ 重要格式要求：直接返回纯 JSON 对象，不要 markdown 围栏（不要 ```json 开头也不要 ``` 结尾），不要任何前后缀文字：

                {
                  "projectName": "...",
                  "oneLinePositioning": "一句话定位，不超过 40 字",
                  "techStack": ["Java 21", "Spring Boot 3", "Vue 3", "MySQL", ...],
                  "summaryMd": "Markdown 长文，含这些章节：\\n## 项目定位\\n## 核心功能\\n## 技术选型\\n## 模块分层\\n## 数据流\\n## 关键文件导读\\n## 部署说明\\n每个章节 2-4 段。",
                  "mermaidCode": "用 graph TD 或 sequenceDiagram 语法画出核心流程或模块依赖的流程图（一张图，30-80 行）"
                }
                """;
    }

    /**
     * 提交审查 system prompt —— 阿里规约 + 结构化输出约束。
     */
    public static String commitReviewSystem() {
        return ALIBABA_RULES + """

                ---

                你的任务：审查给定的 git diff，找出违反上述规约、存在安全隐患或代码质量问题的地方。
                重点识别**隐藏的、容易被忽略的风险**，比如：
                  - 表面能跑但并发/边界/空值下会出问题
                  - 看起来不危险的 SQL/字符串拼接但含注入风险
                  - catch Exception 吞异常
                  - 线程池/定时器/锁的隐患
                  - 敏感信息泄漏
                  - 数据库未加索引或者索引列被函数包裹

                ⚠️ 重要格式要求：直接返回纯 JSON 对象，不要 markdown 围栏（不要 ```json 开头也不要 ``` 结尾），不要任何前后缀文字：

                {
                  "overallScore": 85,
                  "summaryMd": "Markdown 格式总结，含【本次提交整体评价】和【主要问题归纳】两节，各 2-5 段",
                  "findings": [
                    {
                      "level": "HIGH",
                      "category": "安全",
                      "title": "SQL 拼接存在注入风险",
                      "file": "src/.../XxxService.java",
                      "lineRange": "142-146",
                      "description": "使用 String.format 把 userId 拼接进 SQL，若来自外部则存在注入",
                      "suggestion": "改用 MyBatis #{} 或 PreparedStatement.setLong()",
                      "codeSnippet": "String sql = String.format(\\"SELECT * FROM user WHERE id=%s\\", userId);",
                      "ruleRef": "【强制】【安全规约】"
                    }
                  ]
                }

                level 取值: "HIGH" / "MEDIUM" / "LOW"。
                - HIGH: 安全漏洞/数据丢失/生产故障级别
                - MEDIUM: 违反强制规约但短期可运行 / 性能隐患 / 可维护性差
                - LOW: 风格/可读性/小优化

                overallScore 打分规则：
                  100 - (HIGH数 × 15) - (MEDIUM数 × 5) - (LOW数 × 1)，下限 0，上限 100。

                findings 数量上限 30 条，优先输出 HIGH 和 MEDIUM。
                """;
    }

    /** 对比分析 system prompt */
    public static String diffReviewSystem() {
        return commitReviewSystem().replace(
                "审查给定的 git diff",
                "审查两个 ref 之间的累积 diff（可能包含多次提交）"
        );
    }

    /** 用户消息模板：提交审查 */
    public static String commitReviewUser(String repoName, String commitSha, String diff) {
        return """
                仓库: %s
                提交 SHA: %s

                以下是本次提交的完整 diff（可能被截断）：

                ```diff
                %s
                ```

                请按 system prompt 要求输出审查 JSON。
                """.formatted(repoName, commitSha, diff);
    }

    /** 用户消息模板：项目概览 */
    public static String projectOverviewUser(String repoName, String treeView,
                                             String languageStats, String keyFiles) {
        return """
                仓库: %s

                ## 目录结构（节选）
                ```
                %s
                ```

                ## 语言统计
                %s

                ## 关键文件内容
                %s

                请按 system prompt 要求输出项目概览 JSON。
                """.formatted(repoName, treeView, languageStats, keyFiles);
    }

    /** 用户消息模板：对比分析 */
    public static String diffReviewUser(String repoName, String baseRef, String headRef, String diff) {
        return """
                仓库: %s
                对比: %s ... %s

                累积 diff（可能被截断）：

                ```diff
                %s
                ```

                请按 system prompt 要求输出审查 JSON。
                """.formatted(repoName, baseRef, headRef, diff);
    }

    // ==================== 全量分析新增 prompt ====================

    /** 模块摘要 system prompt：让 LLM 读一个模块的全部源码，产出该模块的职责说明 */
    public static String moduleSummarySystem() {
        return """
                你是一位资深代码阅读者，擅长快速吸收一个模块的全部源码并浓缩出核心信息。
                给你一个 Maven/Gradle 子模块里的全部源码（或若干目录下的文件集合），
                请输出一份 Markdown 摘要，包含以下章节：

                ## 模块职责
                一句话 + 两三段说明这个模块在整个项目中承担什么角色。

                ## 关键类清单
                列出 5-10 个最重要的类/接口（包括 Controller、Service、Mapper、Entity 等），
                每条给出"类名 → 一句话作用"。

                ## 核心流程
                如果模块里有明确的业务流程（比如文案生成/发布/知识库入库），
                用序号 1, 2, 3 写出该流程的关键步骤和涉及的方法。

                ## 对外依赖
                该模块依赖了哪些外部东西：其他内部模块、外部 SDK（LLM / Redis / DB）、HTTP 客户端等。

                ## 值得注意的细节
                任何值得新人留意的：安全陷阱、并发注意点、特殊配置、已知 TODO 等。

                必须紧凑：每部分 2-5 段，总长度 800-1500 字。不要输出任何 JSON 或 markdown 围栏。
                """;
    }

    public static String moduleSummaryUser(String moduleName, String fileList, String sources) {
        return """
                模块名: %s

                ## 文件清单
                %s

                ## 全部源码
                ```
                %s
                ```

                请按 system prompt 要求输出该模块的 Markdown 摘要。
                """.formatted(moduleName, fileList, sources);
    }

    /** 最终项目全景 system prompt：在所有模块摘要到位后，产出项目级报告 */
    public static String projectOverviewFinalSystem() {
        return """
                你是一位资深技术架构师，目标：为第一次接触该项目的新工程师写一份"项目全景"。
                你已经看过了该项目每个模块的详细摘要（由你自己之前读全部源码产出）。
                现在请综合所有摘要 + 技术栈 + 目录结构，⚠️ 重要格式要求：直接返回纯 JSON 对象，不要 markdown 围栏（不要 ```json 开头也不要 ``` 结尾），不要任何前后缀文字（包括"以下是..."这类话）：

                {
                  "projectName": "...",
                  "oneLinePositioning": "一句话定位，不超过 40 字",
                  "techStack": ["Java 21", "Spring Boot 3", ...],
                  "summaryMd": "Markdown 长文，必须含这些章节：\\n## 项目定位\\n## 核心功能（列出 5-10 个能看到的能力）\\n## 技术选型（每一项说明"用什么 + 为什么"）\\n## 模块分层（基于摘要逐个介绍每个模块，每个模块 2-3 段）\\n## 典型数据流（至少画出 1-2 条端到端流程）\\n## 关键文件导读（列 10-20 个新人必读文件及路径 + 原因）\\n## 部署与运行\\n## 潜在改进 / 坑（从摘要里看到的可能问题）",
                  "mermaidCode": "graph TD 或 sequenceDiagram 语法，30-80 行，画出模块依赖或核心数据流"
                }

                要求：summaryMd 至少 2000 字，基于你已经读过的每个模块的真实代码写，不要虚构。
                """;
    }

    public static String projectOverviewFinalUser(String repoName, String treeView,
                                                  String languageStats, String moduleSummariesJoined) {
        return """
                仓库: %s

                ## 目录结构（节选）
                ```
                %s
                ```

                ## 语言统计
                %s

                ## 你已经产出的各模块摘要
                %s

                请按 system prompt 输出最终 JSON。
                """.formatted(repoName, treeView, languageStats, moduleSummariesJoined);
    }

    /**
     * 方案 E：自检 prompt —— 对一组 finding 做"质疑者视角"的二次复核，输出 confidence 评分。
     * 把 0-100 分 < 60 的过滤掉，能干掉一半"我猜的"型 finding。
     */
    public static String findingSelfCheckSystem() {
        return """
                你扮演一个挑剔的资深代码评审者。下面是另一位 AI 评审给出的 findings 列表，
                你的任务是逐条用质疑者视角复核：

                **判断维度**：
                  - codeSnippet 是否真的违反了 ruleRef 引用的规约？
                  - codeSnippet 是不是已经合规的写法（如已带初始容量、已带线程池 bean 名）？
                  - 是不是把"注释/常量定义/已修复代码"误报为违规？
                  - description 描述的问题在 codeSnippet 中真实存在吗？

                **输出**（必须直接 JSON，不要 markdown 围栏）：
                {
                  "checks": [
                    { "index": 0, "confidence": 85, "verdict": "valid|false_positive|uncertain", "reason": "简短理由" },
                    ...
                  ]
                }
                index 是 finding 在输入数组里的下标（从 0 开始）。
                confidence: 0-100，对"违规判定正确"的把握度。
                  - >= 70：明显是真问题
                  - 50-69：可能是真问题，但表达模糊
                  - < 50：很可能是误判
                verdict: valid（真问题）/ false_positive（误报）/ uncertain（不确定）
                """;
    }

    public static String findingSelfCheckUser(String findingsJson) {
        return """
                以下是待复核的 findings JSON 数组：

                ```json
                %s
                ```

                请按 system prompt 输出 checks JSON。
                """.formatted(findingsJson);
    }

    /** 单文件 diff 审查 system prompt */
    public static String fileReviewSystem() {
        return ALIBABA_RULES + """

                ---

                你正在审查**一个文件**的改动。严格按《阿里巴巴 Java 开发手册（黄山版）》321 条规约识别：
                违反条款 + 安全隐患 + 代码坏味道。

                ⚠️ 严格输出要求（不满足的 finding 会被后端校验丢弃）：

                1. **codeSnippet 必须是从原文逐字复制的 3-8 行真实代码**
                   - 不要改写、不要总结、不要省略
                   - 必须能在 diff/源文件中用 String.contains 找到
                   - 错误示范：codeSnippet: "缺少 toString 方法"  → 这是描述，不是代码
                   - 正确示范：codeSnippet: "private Integer isDefault;"

                2. **ruleRef 必须是真实存在的黄山版规约编号 X.Y.Z**
                   - 格式必须含数字编号，如 "黄山版 1.6.1"、"黄山版 5.1.5"
                   - 后端会去 321 条规约白名单校验，不存在就丢
                   - 错误示范：ruleRef: "【强制】【并发】"  → 没编号会被丢弃
                   - 正确示范：ruleRef: "黄山版 1.6.1 - @Async 必须自定义线程池"

                3. **lineRange 必须指向真实代码行（不能是注释/空行/纯括号）**
                   - 必须看清行号对应的实际内容
                   - 错误示范：报"行 24 类名违规"但行 24 是 `*/` 注释结束符

                4. **不要重复报已修复的问题**
                   - 如果代码里已经有合规写法（如 `@Async("xxxExecutor")` 已带 bean 名），就不要再报
                   - 如果代码里已经有 `// 黄山版 X.Y.Z` 注释说明已知合规，跳过

                ⚠️ 重要格式要求：直接返回纯 JSON 对象，不要 markdown 围栏（不要 ```json 开头也不要 ``` 结尾），不要任何前后缀文字：
                {
                  "findings": [
                    {
                      "level": "HIGH|MEDIUM|LOW",
                      "category": "安全/并发/命名/SQL/异常/...",
                      "title": "一句话概括",
                      "file": "<就用我给你的文件路径>",
                      "lineRange": "42-46",
                      "description": "问题详细描述（必须解释为什么违规）",
                      "suggestion": "修复建议",
                      "codeSnippet": "<必须从原文复制的真实代码片段>",
                      "ruleRef": "黄山版 X.Y.Z - 规约标题"
                    }
                  ]
                }

                findings 数量上限 15 条/文件，优先 HIGH/MEDIUM，宁可少报也不要错报。
                如该文件没有发现问题，返回 { "findings": [] }。
                """;
    }

    /**
     * Wave 6 C-1：在 user prompt 头部追加"本文件类型相关的规约清单"。
     * AsyncRunner 调用前用 RuleLibraryHolder.pickForFile(filePath) 取规约 + 简化为 ruleListMarkdown 传入。
     * LLM 看到清单后，必须从清单里挑 ruleRef，不再瞎编。
     */
    public static String fileReviewUser(String repoName, String commitSha, String filePath, String fileDiff,
                                        String ruleListMarkdown) {
        String rulePart = (ruleListMarkdown == null || ruleListMarkdown.isBlank())
                ? ""
                : "\n## 本文件类型相关的黄山版规约（findings 的 ruleRef 必须从这里选取真实编号）\n\n"
                + ruleListMarkdown + "\n\n";
        return """
                仓库: %s
                提交: %s
                文件: %s
                %s
                本文件的完整 diff：
                ```diff
                %s
                ```

                请按 system prompt 返回 findings JSON。
                """.formatted(repoName, commitSha, filePath, rulePart, fileDiff);
    }

    /** 旧版 4 参数签名，保留兼容（无规约清单时调用） */
    public static String fileReviewUser(String repoName, String commitSha, String filePath, String fileDiff) {
        return """
                仓库: %s
                提交: %s
                文件: %s

                本文件的完整 diff：
                ```diff
                %s
                ```

                请按 system prompt 返回 findings JSON。
                """.formatted(repoName, commitSha, filePath, fileDiff);
    }

    /** 合并所有 findings 后做最终总结 system prompt */
    public static String reviewMergeSystem() {
        return """
                你已经分别审查了本次提交改动的若干文件，并收到了所有文件的 findings 列表。
                现在需要做最终归纳：

                1. 给 overallScore（0-100）：100 - HIGH*15 - MEDIUM*5 - LOW*1，下限 0。
                2. 写一段 summaryMd（Markdown）：
                   ## 本次提交整体评价
                   （2-4 段，说明这次提交干了什么、质量如何）

                   ## 主要问题归纳
                   （把 findings 按类别归类，指出最重要的 3-5 条）

                   ## 改进建议
                   （总体层面的建议，不仅限于 findings）

                **不要**再额外添加新 findings，只归纳已有的。

                ⚠️ 重要格式要求：直接返回纯 JSON 对象，不要 markdown 围栏（不要 ```json 开头也不要 ``` 结尾），不要任何前后缀文字（包括"以下是..."这类话）：
                { "overallScore": 85, "summaryMd": "..." }
                """;
    }

    public static String reviewMergeUser(String repoName, String commitSha, String findingsJsonJoined) {
        return """
                仓库: %s
                提交: %s

                ## 所有文件审查得到的 findings（合并后）
                %s

                请按 system prompt 返回 { overallScore, summaryMd } JSON。
                """.formatted(repoName, commitSha, findingsJsonJoined);
    }
}
