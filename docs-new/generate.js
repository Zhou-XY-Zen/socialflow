/**
 * SocialFlow 小白学习手册 Word 生成脚本。
 * 使用 docx-js 创建一本"从 Java 基础 → 整个项目能读懂"的文档。
 */
const fs = require("fs");
const path = require("path");
const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell, ImageRun,
  AlignmentType, LevelFormat, PageOrientation,
  HeadingLevel, BorderStyle, WidthType, ShadingType, VerticalAlign,
  PageNumber, PageBreak, Header, Footer,
} = require("docx");

const IMG = (n) => fs.readFileSync(path.join(__dirname, "images", n));

// ---- 辅助函数 ----
const FONT = "Microsoft YaHei";
const MONO = "Consolas";

function p(text, opts = {}) {
  const { size = 22, bold = false, color, italic = false, indent = 0, spacing = 100, align } = opts;
  return new Paragraph({
    spacing: { before: spacing, after: spacing, line: 340 },
    alignment: align,
    indent: indent ? { left: indent } : undefined,
    children: [new TextRun({ text, font: FONT, size, bold, color, italics: italic })],
  });
}

function rich(runs, opts = {}) {
  const { spacing = 100, indent = 0, align } = opts;
  return new Paragraph({
    spacing: { before: spacing, after: spacing, line: 340 },
    alignment: align,
    indent: indent ? { left: indent } : undefined,
    children: runs.map(r => {
      if (typeof r === "string") return new TextRun({ text: r, font: FONT, size: 22 });
      return new TextRun({
        text: r.text,
        font: r.mono ? MONO : FONT,
        size: r.size ?? 22,
        bold: r.bold,
        italics: r.italic,
        color: r.color,
        highlight: r.highlight,
      });
    }),
  });
}

function h1(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_1,
    spacing: { before: 400, after: 200 },
    children: [new TextRun({ text, font: FONT, size: 36, bold: true, color: "1565C0" })],
  });
}

function h2(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_2,
    spacing: { before: 320, after: 160 },
    children: [new TextRun({ text, font: FONT, size: 30, bold: true, color: "1976D2" })],
  });
}

function h3(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_3,
    spacing: { before: 240, after: 120 },
    children: [new TextRun({ text, font: FONT, size: 26, bold: true, color: "2E7D32" })],
  });
}

function bullet(text, level = 0) {
  return new Paragraph({
    numbering: { reference: "bullets", level },
    spacing: { before: 40, after: 40, line: 320 },
    children: [new TextRun({ text, font: FONT, size: 22 })],
  });
}

function bulletRich(runs, level = 0) {
  return new Paragraph({
    numbering: { reference: "bullets", level },
    spacing: { before: 40, after: 40, line: 320 },
    children: runs.map(r => {
      if (typeof r === "string") return new TextRun({ text: r, font: FONT, size: 22 });
      return new TextRun({
        text: r.text,
        font: r.mono ? MONO : FONT,
        size: r.size ?? 22,
        bold: r.bold,
        italics: r.italic,
        color: r.color,
      });
    }),
  });
}

function code(lines, language = "") {
  // 代码块：灰底等宽字体，每行一个 paragraph
  const arr = Array.isArray(lines) ? lines : lines.split("\n");
  const paragraphs = [];
  arr.forEach((line, i) => {
    paragraphs.push(new Paragraph({
      spacing: { before: i === 0 ? 120 : 0, after: i === arr.length - 1 ? 120 : 0, line: 280 },
      shading: { fill: "F5F5F5", type: ShadingType.CLEAR, color: "auto" },
      indent: { left: 200 },
      children: [new TextRun({ text: line || " ", font: MONO, size: 20, color: "333333" })],
    }));
  });
  return paragraphs;
}

function callout(title, body, color = "FFF3E0") {
  // 提示框：带颜色的表格
  const border = { style: BorderStyle.SINGLE, size: 6, color: "FFA726" };
  const darkBorder = { ...border };
  return new Table({
    width: { size: 9360, type: WidthType.DXA },
    columnWidths: [9360],
    borders: {
      top: darkBorder, bottom: darkBorder, left: darkBorder, right: darkBorder,
      insideHorizontal: { style: BorderStyle.NONE }, insideVertical: { style: BorderStyle.NONE },
    },
    rows: [new TableRow({
      children: [new TableCell({
        width: { size: 9360, type: WidthType.DXA },
        shading: { fill: color, type: ShadingType.CLEAR, color: "auto" },
        margins: { top: 120, bottom: 120, left: 200, right: 200 },
        children: [
          new Paragraph({
            spacing: { before: 0, after: 80 },
            children: [new TextRun({ text: "💡 " + title, font: FONT, size: 22, bold: true, color: "BF360C" })],
          }),
          ...(Array.isArray(body) ? body : [body]).map(t => new Paragraph({
            spacing: { before: 0, after: 0, line: 320 },
            children: [new TextRun({ text: t, font: FONT, size: 22 })],
          })),
        ],
      })],
    })],
  });
}

function img(name, w = 600, h = 380) {
  return new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { before: 200, after: 200 },
    children: [new ImageRun({
      type: "png",
      data: IMG(name),
      transformation: { width: w, height: h },
      altText: { title: name, description: name, name: name },
    })],
  });
}

function caption(text) {
  return new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { before: 0, after: 200 },
    children: [new TextRun({ text, font: FONT, size: 20, italics: true, color: "666666" })],
  });
}

// 表格辅助
function table(headers, rows, widths) {
  const totalW = widths.reduce((a, b) => a + b, 0);
  const border = { style: BorderStyle.SINGLE, size: 4, color: "BDBDBD" };
  const borders = { top: border, bottom: border, left: border, right: border,
                    insideHorizontal: border, insideVertical: border };
  const mk = (texts, isHeader) => new TableRow({
    children: texts.map((t, i) => new TableCell({
      width: { size: widths[i], type: WidthType.DXA },
      shading: isHeader ? { fill: "E3F2FD", type: ShadingType.CLEAR, color: "auto" } : undefined,
      margins: { top: 80, bottom: 80, left: 120, right: 120 },
      children: [new Paragraph({
        spacing: { before: 0, after: 0 },
        children: [new TextRun({ text: t, font: FONT, size: 20, bold: isHeader })],
      })],
    })),
  });
  return new Table({
    width: { size: totalW, type: WidthType.DXA },
    columnWidths: widths,
    borders,
    rows: [mk(headers, true), ...rows.map(r => mk(r, false))],
  });
}

// ============================================================
// 章节内容
// ============================================================
const children = [];

// ========== 封面 ==========
children.push(new Paragraph({
  alignment: AlignmentType.CENTER,
  spacing: { before: 2000, after: 400 },
  children: [new TextRun({ text: "SocialFlow", font: FONT, size: 72, bold: true, color: "1565C0" })],
}));
children.push(new Paragraph({
  alignment: AlignmentType.CENTER,
  spacing: { before: 0, after: 800 },
  children: [new TextRun({ text: "小白学习手册", font: FONT, size: 48, bold: true, color: "424242" })],
}));
children.push(new Paragraph({
  alignment: AlignmentType.CENTER,
  spacing: { before: 0, after: 400 },
  children: [new TextRun({ text: "—— 从 Java 基础到读懂整个项目", font: FONT, size: 28, italics: true, color: "666666" })],
}));
children.push(new Paragraph({
  alignment: AlignmentType.CENTER,
  spacing: { before: 1200, after: 200 },
  children: [new TextRun({ text: "面向刚入行的 Java 工程师", font: FONT, size: 24, color: "555555" })],
}));
children.push(new Paragraph({
  alignment: AlignmentType.CENTER,
  spacing: { before: 0, after: 0 },
  children: [new TextRun({ text: "AI 社交媒体内容运营平台", font: FONT, size: 24, color: "555555" })],
}));
children.push(new Paragraph({ children: [new PageBreak()] }));

// ========== 前言 ==========
children.push(h1("0  前言：这本手册给谁看"));
children.push(p("你好，刚入行的 Java 工程师 👋"));
children.push(p("这本手册是为你量身定做的 —— 你有基础的编程概念（变量、循环、面向对象），但可能："));
children.push(bullet("没写过真正的 Spring Boot 项目"));
children.push(bullet("不知道多模块 Maven 怎么拆分，为什么拆"));
children.push(bullet("听过 Redis / Nacos / 向量数据库，但没用过"));
children.push(bullet("对 AI、大模型、RAG 这些概念心里没底"));
children.push(bullet("想读懂一个完整的、上线跑着的生产项目，但不知道从哪里开始"));
children.push(p("SocialFlow 是一个完整的 AI 社交媒体内容运营平台 —— 用户给个主题，它就能生成小红书/抖音/朋友圈/公众号的文案，还能 AI 配图、发布、做知识库 RAG、做 A/B 评估。整个项目大约 18,000 行后端 Java + 15,000 行前端 TypeScript，跑在腾讯云上，功能齐全。"));
children.push(p("本手册的目标："));
children.push(bullet("读完第 1-3 章 → 你知道 Java 生态里的基本概念是什么、为什么"));
children.push(bullet("读完第 4-10 章 → 你能独立读懂 SocialFlow 每个模块的代码，并知道它为什么这样设计"));
children.push(bullet("读完第 11-14 章 → 你能自己部署一遍、改一个功能、加一张表"));
children.push(bullet("读完第 15 章 → 你有了一条清晰的学习路径"));
children.push(callout("怎么读这本手册", [
  "• 从头按顺序读。每章都假设你读过前面的章节。",
  "• 看到不懂的名词，不要跳 —— 它可能就在当前页定义。",
  "• 代码片段是真实从项目里截的，可以直接在 GitHub 上找到完整文件。",
  "• 读完一章，打开项目对应的代码看一眼 —— 文档 + 代码配套理解最快。",
]));
children.push(new Paragraph({ children: [new PageBreak()] }));

// ============================================================
// 第 1 章：Java 生态扫盲
// ============================================================
children.push(h1("1  Java 生态扫盲（必读）"));
children.push(p("在开始看项目代码前，先把这些\"最基础但最容易蒙圈\"的概念过一遍。如果你已经熟，直接跳过。"));

children.push(h2("1.1  JDK / JRE / JVM 到底是什么"));
children.push(p("很多新人分不清这三个。用一张表说清："));
children.push(table(
  ["名字", "是什么", "包含哪些东西", "你什么时候需要"],
  [
    ["JVM", "Java 虚拟机（Java Virtual Machine）", "负责把 .class 字节码翻译成机器指令执行", "永远在后台跑，不用管"],
    ["JRE", "Java 运行环境（Java Runtime Environment）", "= JVM + 核心类库（java.util.*、java.io.* 等）", "只想运行 .jar 文件时"],
    ["JDK", "Java 开发工具包（Java Development Kit）", "= JRE + 编译器 javac + 其他工具（javadoc/jstack/jmap）", "写代码、编译、调试时必须"],
  ],
  [1400, 2400, 3200, 2360],
));
children.push(p("SocialFlow 用的是 JDK 21（2023 年 9 月发布的 LTS 长期支持版），服务器上是腾讯 Kona 21。IDE 里看代码时用的是 IntelliJ IDEA 自带的 JBR 21。"));
children.push(callout("LTS 是什么意思", "LTS = Long Term Support 长期支持版。Java 现在每半年发一个新版，但只有 8、11、17、21、25 这种是 LTS，官方维护至少 3 年，是企业会用的版本。其他版本只是过渡。"));

children.push(h2("1.2  Maven 是什么，为什么要多模块"));
children.push(p("Maven 干两件事："));
children.push(bulletRich([{ text: "依赖管理", bold: true }, "：你在 pom.xml 里写 ", { text: "<dependency>mybatis-plus</dependency>", mono: true }, "，Maven 自动从中央仓库下载这个 jar 和它依赖的所有 jar 到本地 ~/.m2/repository。"]));
children.push(bulletRich([{ text: "构建生命周期", bold: true }, "：", { text: "mvn compile → mvn test → mvn package", mono: true }, "，把源代码编译、跑测试、打成 jar 包。"]));
children.push(p("SocialFlow 是个「多模块」Maven 项目。想象一下："));
children.push(p("❌ 如果只有一个 module，所有代码塞一个 src/main/java —— 18000 行，包混乱，改数据库实体牵动业务逻辑，谁也不敢碰。"));
children.push(p("✅ 拆成 6 个 module，职责分明："));
children.push(...code([
  "socialflow/",
  "├── pom.xml                    ← 父 pom，管理所有子模块",
  "├── socialflow-common/         ← R<T>、异常类、工具（最底层）",
  "├── socialflow-model/          ← Entity、DTO、VO（依赖 common）",
  "├── socialflow-dao/            ← Mapper 接口（依赖 model）",
  "├── socialflow-service/        ← 业务逻辑（依赖 dao）",
  "├── socialflow-web/            ← Controller（依赖 service）",
  "└── socialflow-admin/          ← Spring Boot 启动入口（依赖 web）",
]));
children.push(p("规则：上层可以 import 下层的类，下层绝不能 import 上层的 —— 这就是「分层架构」。"));

children.push(h2("1.3  Spring Boot 是什么：IoC 和依赖注入"));
children.push(p("Spring 是 Java 最流行的框架。Spring Boot = Spring + 「自动配置」—— 你不用写一大堆 XML，注解就够了。"));
children.push(h3("1.3.1  什么是 IoC（控制反转）"));
children.push(p("传统写法："));
children.push(...code([
  "// 传统：你自己 new",
  "public class ContentService {",
  "    private ContentMapper mapper = new ContentMapper();  // ← 你决定怎么创建",
  "    private LlmRouter llm = new LlmRouter();",
  "}",
]));
children.push(p("Spring 写法："));
children.push(...code([
  "// Spring：你只声明「我需要什么」，容器自己给你塞",
  "@Service",
  "public class ContentServiceImpl {",
  "    private final ContentMapper mapper;    // ← Spring 塞进来",
  "    private final LlmRouter llm;",
  "    public ContentServiceImpl(ContentMapper m, LlmRouter l) {   // ← 构造函数注入",
  "        this.mapper = m;",
  "        this.llm = l;",
  "    }",
  "}",
]));
children.push(p("「控制反转」就是：创建对象的控制权从你手里，反转到 Spring 容器手里。"));
children.push(p("好处：换一个 LlmRouter 的实现？改 1 行 @Primary 注解，业务代码一行不用动。"));

children.push(h3("1.3.2  常见注解对照表"));
children.push(table(
  ["注解", "打在哪", "作用"],
  [
    ["@Component", "普通类", "声明这是个 Spring Bean（被容器管理的对象）"],
    ["@Service", "业务类", "语义化的 @Component（表示这是业务层）"],
    ["@Repository", "DAO 类", "语义化的 @Component（表示数据访问层）"],
    ["@Controller / @RestController", "控制器类", "语义化的 @Component + 自动 JSON 序列化"],
    ["@Configuration", "配置类", "里面可以放 @Bean 方法"],
    ["@Bean", "@Configuration 类里的方法", "返回值成为一个 Bean"],
    ["@Autowired / @Resource", "字段/构造器", "告诉 Spring：把匹配类型的 Bean 注入到这里"],
    ["@Value", "字段", "从 application.yml 读配置值"],
  ],
  [2000, 2400, 4960],
));
children.push(p("SocialFlow 主要用构造器注入（配合 Lombok 的 @RequiredArgsConstructor），这是业界推荐做法：不可变、易测试。"));

children.push(h2("1.4  MVC 三层架构"));
children.push(p("Spring Boot 项目的经典分层 —— SocialFlow 每个业务都这么组织："));
children.push(...code([
  "Controller (web 层)   ← 接收 HTTP 请求，做参数校验，调用 Service",
  "       ↓",
  "Service    (业务层)   ← 核心业务逻辑，调用 Mapper 读写数据",
  "       ↓",
  "Mapper     (数据层)   ← 只负责 SQL，不懂业务",
]));
children.push(p("为什么要分层？一句话：让每一层只做一件事，改动影响最小。"));
children.push(bulletRich(["改 UI 展示字段 → 只动 Controller / VO"]));
children.push(bulletRich(["改 AI 生成逻辑 → 只动 Service"]));
children.push(bulletRich(["加一张表 → 只动 Mapper / Entity"]));
children.push(new Paragraph({ children: [new PageBreak()] }));

// ============================================================
// 第 2 章：存储和中间件扫盲
// ============================================================
children.push(h1("2  存储 & 中间件扫盲"));
children.push(p("SocialFlow 用到了 5 种存储/中间件。每种都有明确的分工。"));

children.push(h2("2.1  MySQL：业务数据"));
children.push(p("MySQL 是最流行的关系型数据库。SocialFlow 里所有\"结构化\"数据都在 MySQL："));
children.push(bullet("用户信息（sys_user）"));
children.push(bullet("生成的内容（content、content_version）"));
children.push(bullet("发布任务（publish_task）"));
children.push(bullet("API 密钥（user_api_key，AES-GCM 加密存储）"));
children.push(bullet("18 张表，约 100MB 数据"));
children.push(p("项目用 MyBatis-Plus 操作 MySQL。MyBatis-Plus 是 MyBatis 的增强版，提供："));
children.push(...code([
  "// 1 行代码完成查询，不用写 SQL",
  "Content c = contentMapper.selectById(123L);",
  "List<Content> list = contentMapper.selectList(",
  "    new LambdaQueryWrapper<Content>()",
  "        .eq(Content::getUserId, userId)",
  "        .like(Content::getTitle, \"咖啡\")",
  "        .orderByDesc(Content::getCreateTime)",
  "        .last(\"LIMIT 10\"));",
]));
children.push(callout("为什么不直接写 SQL", [
  "• 类型安全：Content::getUserId 是个方法引用，改字段名编译器立刻报错",
  "• 防 SQL 注入：参数都走 PreparedStatement 绑定",
  "• 代码量少：复杂的分页、软删除、乐观锁都自动处理",
]));

children.push(h2("2.2  Redis：缓存 + 锁 + 会话"));
children.push(p("Redis 是内存 KV 数据库，读写延迟 &lt; 1ms。SocialFlow 用它做 3 件事："));
children.push(bulletRich([{ text: "① 缓存", bold: true }, "：Wave 1.3 的 Spring Cache（@Cacheable）—— 比如文本向量化结果缓存 24 小时，同样 prompt 不再调外部 API。"]));
children.push(bulletRich([{ text: "② 分布式锁", bold: true }, "：Wave 3.1 的 ShedLock —— 定时发布任务在多实例部署时，用 Redis 锁保证全集群只跑一份。"]));
children.push(bulletRich([{ text: "③ 会话存储", bold: true }, "：Sa-Token 把用户登录后的 token 和权限信息放 Redis，7 天过期。"]));

children.push(h2("2.3  PostgreSQL + pgvector：向量数据库"));
children.push(p("PostgreSQL（简称 PG）是另一种关系型数据库，功能比 MySQL 更强。pgvector 是 PG 的扩展，让它能存「向量」并做相似度搜索。"));
children.push(p("SocialFlow 用 PG+pgvector 存知识库切片的向量（1024 维 float[]，每个向量代表一段文本的\"意思\"）。"));
children.push(p("为什么要另起一个数据库？因为 MySQL 不擅长向量运算，而 pgvector 有专门的 HNSW 索引算法，15000 条向量做 Top-10 余弦搜索大约只要 10ms。"));
children.push(callout("向量 / 相似度是什么", [
  "把一段文字喂给「Embedding 模型」，它会吐出一串浮点数（1024 维）。",
  "两段文字越相近，它们的向量越接近（余弦距离越小）。",
  "所以\"给定问题，找知识库里最相关的片段\"就变成了\"给定向量 A，找最近的向量\"。",
]));

children.push(h2("2.4  Nacos：配置中心"));
children.push(p("一个简单问题：数据库密码写在哪？"));
children.push(p("❌ 写在代码里：改一次要重新打包。多环境（dev/test/prod）更头疼。"));
children.push(p("❌ 写在 application.yml：密码在 GitHub 上公开了。"));
children.push(p("✅ 写在 Nacos：启动时从 Nacos 拉取，运行中改了配置能动态生效。"));
children.push(p("SocialFlow 在生产用的是 spring.config.import 方式加载 Nacos："));
children.push(...code([
  "spring:",
  "  config:",
  "    import:",
  "      - optional:nacos:socialflow.yml?group=DEFAULT_GROUP",
  "  cloud:",
  "    nacos:",
  "      config:",
  "        server-addr: ${NACOS_ADDR:127.0.0.1:8848}",
]));
children.push(p("启动时，Spring 先连 Nacos，把 socialflow.yml 的所有配置拉下来，覆盖本地 application.yml 的默认值。"));

children.push(h2("2.5  腾讯云 COS：对象存储"));
children.push(p("文件（图片、视频、PDF、知识库文档）不应该存 MySQL —— 慢且贵。"));
children.push(p("对象存储 = 专门放大文件的 HTTP 服务。上传返回一个 URL，下载用这个 URL。SocialFlow 用腾讯云 COS（类似 AWS S3）。"));
children.push(p("抽象在 StorageService 接口，实现有 TencentCosStorageService 和 MinioStorageServiceLegacy_REMOVED（本地开发用）。"));
children.push(new Paragraph({ children: [new PageBreak()] }));

// ============================================================
// 第 3 章：AI 专属概念
// ============================================================
children.push(h1("3  AI 专属概念扫盲"));
children.push(p("SocialFlow 本质是个 AI 应用，所以必须先把 AI 术语弄明白。"));

children.push(h2("3.1  LLM / Token / 上下文窗口"));
children.push(p("LLM = Large Language Model，大语言模型。GPT、DeepSeek、Claude、通义千问、GLM、文心一言，全是 LLM。"));
children.push(p("LLM 按 Token 计费。Token ≈ 一个汉字 / 半个英文单词。"));
children.push(p("比如「写一篇 500 字的咖啡探店文案」这 12 个字，大约 14 个 tokens。LLM 返回的 500 字回复大约 600 tokens。一次调用总消耗 ~614 tokens。"));
children.push(callout("Token 实际花费", [
  "DeepSeek：输入 ¥0.001/千 tokens，输出 ¥0.002/千。一次 614 tokens 大约 ¥0.0012。",
  "但知识库场景下一次可能 10,000+ tokens（因为塞了大量参考文档），就是几毛钱。",
  "所以 Wave 1.3 的 embeddingCache 24h 缓存 是\"一天最多消耗一次\"的关键优化。",
]));
children.push(p("上下文窗口（context window）= LLM 一次最多能接收多少 tokens。DeepSeek 64K tokens，GPT-4o 128K，Gemini 1M+。超过就得截断或 RAG。"));

children.push(h2("3.2  Prompt / System Prompt / User Prompt"));
children.push(p("你不是和 LLM\"聊天\"，是在给它一段指令（prompt），它按指令生成文本。"));
children.push(p("真正发给 LLM 的不是纯文本，而是一个 messages 数组，有两种主要角色："));
children.push(...code([
  "[",
  "  {",
  "    \"role\": \"system\",",
  "    \"content\": \"你是一位资深的小红书博主，擅长种草文案。\"",
  "  },",
  "  {",
  "    \"role\": \"user\",",
  "    \"content\": \"写一篇关于春季护肤的 300 字文案，目标平台：小红书。\"",
  "  }",
  "]",
]));
children.push(bulletRich([{ text: "System Prompt", bold: true }, "：定义 AI 的\"角色\"和\"规则\"。SocialFlow 的每个 Prompt 模板都有一个精心设计的 system prompt。"]));
children.push(bulletRich([{ text: "User Prompt", bold: true }, "：实际的任务描述，里面可以有 ", { text: "{{topic}}", mono: true }, " 这种占位符，项目的 PromptService 会帮你替换成真实值。"]));

children.push(h2("3.3  Embedding（向量化）"));
children.push(p("把一段文字塞给 Embedding 模型（SocialFlow 用阿里云 DashScope 的 text-embedding-v3），它返回 1024 个浮点数。"));
children.push(p("这 1024 个数字编码了文字的\"语义\"—— 说得通俗：相似意思的文字，数字也相似。"));
children.push(p("例子："));
children.push(...code([
  "\"今天天气真好\"    → [0.12, -0.34, 0.87, ..., 0.05]  (1024 维)",
  "\"今日阳光明媚\"    → [0.14, -0.32, 0.85, ..., 0.06]  (非常接近)",
  "\"MySQL 连接超时\" → [-0.45, 0.78, -0.21, ..., 0.92] (完全不同)",
]));
children.push(p("有了向量，\"搜索\"就变成了\"找最近的向量\" —— 这是 RAG 的基础。"));

children.push(h2("3.4  RAG（检索增强生成）"));
children.push(p("LLM 有个大问题：它不知道你公司的内部知识。比如你问\"我们公司的请假流程是什么\"，它会瞎编。"));
children.push(p("RAG = Retrieval-Augmented Generation，解法很聪明："));
children.push(p("① 提前把公司知识库（PDF、文档）切成小块（chunk），每块算一个向量存起来。"));
children.push(p("② 用户问问题时，先把问题向量化，在知识库里找最相关的 Top-5 片段。"));
children.push(p("③ 把这 5 个片段塞进 prompt："));
children.push(...code([
  "[参考1] 员工请假需提前 3 天申请...",
  "[参考2] 病假需提供医院证明...",
  "",
  "问题：我们公司的请假流程是什么？",
]));
children.push(p("④ LLM 现在有了依据，回答就准确了。"));
children.push(p("SocialFlow 的 RagPipelineServiceImpl 有 8 步的复杂流水线（后面第 8 章详细讲）。"));

children.push(h2("3.5  Chunk（切片）与 Chunking 策略"));
children.push(p("为什么要切片？因为 LLM 上下文有限，整本 200 页 PDF 塞不进去。"));
children.push(p("切片策略（SocialFlow 用的）：每块 512 tokens，相邻块重叠 64 tokens（防止句子被切断）。"));
children.push(p("一个 100 页的 PDF 大约 50,000 字 = ~70,000 tokens = ~140 个 chunk。每个 chunk 都会被向量化，存到 PG pgvector。"));

children.push(h2("3.6  Reranker（精排模型）"));
children.push(p("向量检索是\"粗排\"—— 快，但不够准。"));
children.push(p("Reranker 是\"精排\"—— 把用户问题和每个候选 chunk 拼在一起，用 Cross-Encoder 模型算精确的相关度分数。"));
children.push(p("对比：Bi-Encoder（向量）一次编码一段文字，Cross-Encoder 一次编码\"问题+文档\"的拼接，精度更高但慢。"));
children.push(p("所以流程是：先用向量/BM25 召回 Top-20 候选（粗排），再用 Reranker 挑 Top-5（精排）。"));
children.push(p("SocialFlow Wave 4.1 用了阿里云 DashScope 的 gte-rerank 模型。"));
children.push(new Paragraph({ children: [new PageBreak()] }));

// ============================================================
// 第 4 章：项目总览
// ============================================================
children.push(h1("4  SocialFlow 项目总览"));

children.push(h2("4.1  这个项目做什么"));
children.push(p("一句话：帮运营人员用 AI 批量生成社交媒体文案、配图、排期发布。"));
children.push(p("典型使用流程："));
children.push(bullet("用户注册登录"));
children.push(bullet("配置 Prompt 模板（系统预设了小红书种草、抖音脚本、朋友圈短文、公众号长文等多套）"));
children.push(bullet("可选：上传知识库文档（PDF/Word/Markdown），用来做 RAG"));
children.push(bullet("在「工作台」输入主题 → AI 流式生成文案 → 自动 AI 配图（4 变体选 1）"));
children.push(bullet("保存到「内容库」，可编辑（支持 autosave + 版本历史）"));
children.push(bullet("在「分发中心」一键复制到小红书/抖音/朋友圈，或自动发布到微信公众号，或创建定时任务"));
children.push(bullet("在「评估中心」用 A/B 测试评估不同 Prompt / 模型的效果（配对 t 检验）"));
children.push(bullet("在「数据看板」看 AI 用量、成本趋势"));

children.push(h2("4.2  技术栈总览"));
children.push(table(
  ["分类", "技术/工具", "版本", "用途"],
  [
    ["语言", "Java / TypeScript", "21 / 5.x", "后端 / 前端"],
    ["后端框架", "Spring Boot", "3.3.5", "Web + 自动装配"],
    ["前端框架", "Vue", "3.5", "组合式 API"],
    ["ORM", "MyBatis-Plus", "3.5.9", "数据库操作"],
    ["认证", "Sa-Token", "1.39", "token + 会话"],
    ["HTTP 客户端", "Spring WebClient", "内置", "调 LLM API（异步）"],
    ["数据库", "MySQL", "8.0", "业务表"],
    ["向量库", "PostgreSQL + pgvector", "15 + 0.8", "知识库向量"],
    ["缓存/锁", "Redis", "7.2", "@Cacheable / ShedLock / 会话"],
    ["配置中心", "Nacos", "2.4", "生产环境动态配置"],
    ["对象存储", "腾讯云 COS", "—", "图片/文档"],
    ["弹性库", "Resilience4j", "2.2", "熔断/重试/限流"],
    ["迁移", "Flyway", "内置", "DB schema 版本管理"],
    ["监控", "Actuator + Micrometer + Prometheus", "—", "指标 + 健康"],
    ["UI 组件", "Element Plus", "2.x", "Vue 组件库"],
    ["Web 服务器", "Nginx", "1.26", "反向代理 + 静态托管"],
  ],
  [1400, 3400, 1400, 3160],
));

children.push(h2("4.3  整体架构"));
children.push(img("01_architecture.png", 640, 390));
children.push(caption("图 4-1：SocialFlow 整体架构"));
children.push(p("看这张图记住 3 件事："));
children.push(bulletRich([{ text: "一个 Nginx 入口", bold: true }, "：用户只看到 http://***REDACTED-PROD-IP***，Nginx 分发到静态页面 / 后端 API。"]));
children.push(bulletRich([{ text: "一个 Java 进程包揽业务", bold: true }, "：socialflow.jar 一个进程跑所有业务。Nacos 也是 Java 进程但跟业务无关。"]));
children.push(bulletRich([{ text: "5 种存储各司其职", bold: true }, "：MySQL 业务表、Redis 缓存、PG 向量、Nacos 配置、COS 文件。"]));
children.push(new Paragraph({ children: [new PageBreak()] }));

// ============================================================
// 第 5 章：代码组织
// ============================================================
children.push(h1("5  代码组织：多模块 Maven"));

children.push(h2("5.1  模块依赖图"));
children.push(img("02_modules.png", 620, 360));
children.push(caption("图 5-1：6 个模块的依赖关系"));

children.push(h2("5.2  每个模块的职责"));
children.push(h3("5.2.1  socialflow-common（最底层）"));
children.push(p("所有模块共享的\"基础设施\"代码。不依赖任何其他 socialflow 模块。"));
children.push(...code([
  "socialflow-common/src/main/java/com/socialflow/common/",
  "├── result/",
  "│   ├── R.java                 ← 统一响应包装 R<T>",
  "│   ├── ResultCode.java        ← 错误码枚举",
  "│   └── PageResult.java        ← 分页响应",
  "├── exception/",
  "│   ├── BaseException.java     ← 异常基类",
  "│   ├── BusinessException.java",
  "│   ├── ParamException.java",
  "│   ├── NotFoundException.java",
  "│   ├── AiCallException.java",
  "│   └── GuardrailException.java",
  "├── enums/                     ← LlmProvider、PlatformType 等",
  "└── util/                      ← AesGcmUtil、JsonUtil、TokenCountUtil",
]));
children.push(p("R<T> 是整个项目的\"响应格式约定\"，每个接口都返回它："));
children.push(...code([
  "{",
  "  \"code\": 200,                // 业务状态码，200=成功",
  "  \"message\": \"ok\",            // 提示消息",
  "  \"data\": {...},              // 实际数据，类型由泛型 T 决定",
  "  \"timestamp\": 1704067200000",
  "}",
]));

children.push(h3("5.2.2  socialflow-model（数据结构）"));
children.push(p("三种对象："));
children.push(table(
  ["类型", "放哪", "例子", "用途"],
  [
    ["Entity", "entity/", "Content.java", "数据库表的 Java 映射"],
    ["DTO", "dto/", "ContentGenerateDTO.java", "前端传给后端的请求体"],
    ["VO", "vo/", "ContentVO.java", "后端返回给前端的响应体"],
  ],
  [1400, 1400, 3200, 3360],
));
children.push(p("为什么分 Entity / DTO / VO 而不是一个类打到底？"));
children.push(bullet("Entity 有 password_hash 等内部字段，直接返回前端会泄露"));
children.push(bullet("DTO 只含用户能传的字段，加 @NotBlank 等校验"));
children.push(bullet("VO 可以组合多个 Entity 的信息（比如 ContentVO 含 guardrailWarnings 等运行时字段）"));

children.push(h3("5.2.3  socialflow-dao（数据库操作）"));
children.push(p("只有 Mapper 接口，没有实现类 —— MyBatis-Plus 自动生成。"));
children.push(...code([
  "@Mapper",
  "public interface ContentMapper extends BaseMapper<Content> {",
  "    // 继承了 BaseMapper 就自动有 insert/delete/update/selectById/selectList 等方法",
  "    ",
  "    // 复杂查询可以写 @Select 注解（这个项目不用 XML）",
  "    @Select(\"SELECT * FROM content WHERE user_id = #{userId} AND status = #{status}\")",
  "    List<Content> findByUserAndStatus(Long userId, String status);",
  "}",
]));

children.push(h3("5.2.4  socialflow-service（业务逻辑）"));
children.push(p("项目的\"大脑\"，~50 个类。每个业务域一个子包："));
children.push(...code([
  "socialflow-service/src/main/java/com/socialflow/service/",
  "├── content/         ← 内容生成、CRUD、版本",
  "├── user/            ← 用户、API Key",
  "├── knowledge/       ← 知识库、文档摄入",
  "├── media/           ← 素材上传、图像生成",
  "├── publish/         ← 发布到各平台",
  "├── dashboard/       ← 数据聚合（Wave 3.2 新增）",
  "└── ai/              ← AI 核心能力",
  "    ├── llm/         ← LLM provider 抽象 + 实现",
  "    ├── embedding/   ← 向量化 + pgvector",
  "    ├── rag/         ← RAG 8 步流水线",
  "    ├── guardrails/  ← 内容安全护栏",
  "    ├── eval/        ← A/B 评估",
  "    ├── prompt/      ← Prompt 模板渲染",
  "    └── agent/       ← 多智能体协作",
]));

children.push(h3("5.2.5  socialflow-web（HTTP 接口）"));
children.push(p("Controller + Filter + 全局异常处理。包比较扁："));
children.push(...code([
  "socialflow-web/src/main/java/com/socialflow/web/",
  "├── controller/      ← ContentController、KbController 等",
  "├── filter/          ← MdcTraceIdFilter、AccessLogFilter",
  "├── config/          ← CorsConfig、JacksonConfig、OpenApiConfig",
  "└── handler/         ← GlobalExceptionHandler",
]));

children.push(h3("5.2.6  socialflow-admin（启动入口）"));
children.push(p("极小的模块，就一个 main 方法 + 配置。依赖 web，而 web 依赖 service，service 依赖其他 —— 所以 admin 打出来的 jar 包含所有代码。"));
children.push(...code([
  "@EnableAsync",
  "@EnableScheduling",
  "@EnableTransactionManagement",
  "@SpringBootApplication(scanBasePackages = \"com.socialflow\")",
  "public class SocialFlowApplication {",
  "    public static void main(String[] args) {",
  "        SpringApplication.run(SocialFlowApplication.class, args);",
  "    }",
  "}",
]));
children.push(p("也放这些 config 类："));
children.push(bullet("CacheConfig（Redis 缓存 TTL 配置，Wave 1.3）"));
children.push(bullet("SchedulingConfig（ShedLock + Redis 锁，Wave 3.1）"));
children.push(bullet("resources/db/migration/V1-V6__*.sql（Flyway 迁移脚本）"));
children.push(new Paragraph({ children: [new PageBreak()] }));

// ============================================================
// 第 6 章：一次请求的完整链路
// ============================================================
children.push(h1("6  一次请求的完整链路"));
children.push(p("搞懂\"用户点一下按钮，代码里发生了什么\"，这是读懂整个项目的钥匙。"));

children.push(h2("6.1  时序图：/content/generate"));
children.push(img("03_sequence.png", 640, 450));
children.push(caption("图 6-1：一次 POST /api/v1/content/generate 的完整时序"));

children.push(h2("6.2  逐步分解"));

children.push(h3("第 ① 步：浏览器发请求"));
children.push(p("Vue 组件调用 axios："));
children.push(...code([
  "// socialflow-ui/src/views/Workspace.vue",
  "const res = await contentApi.generate({",
  "  topic: form.topic,",
  "  platform: 'XIAOHONGSHU',",
  "  tone: 'casual',",
  "  wordCount: 300,",
  "})",
]));

children.push(h3("第 ② 步：Nginx 反向代理"));
children.push(p("浏览器发到 http://***REDACTED-PROD-IP***/api/v1/content/generate。Nginx 配置："));
children.push(...code([
  "# /etc/nginx/conf.d/socialflow.conf",
  "location /api/ {",
  "    proxy_pass http://127.0.0.1:8080;",
  "    proxy_set_header Host $host;",
  "    proxy_set_header X-Real-IP $remote_addr;",
  "}",
  "",
  "location / {",
  "    root /opt/socialflow/web;",
  "    try_files $uri $uri/ /index.html;   # Vue Router history 模式",
  "}",
]));

children.push(h3("第 ③ 步：MdcTraceIdFilter（Wave 2.2）"));
children.push(p("Spring Boot 应用里，请求先过 Filter 链。第一个是我们的 MdcTraceIdFilter："));
children.push(...code([
  "// socialflow-web/.../filter/MdcTraceIdFilter.java",
  "String traceId = request.getHeader(\"X-Trace-Id\");",
  "if (traceId == null) traceId = UUID.randomUUID().toString().replace(\"-\", \"\");",
  "MDC.put(\"traceId\", traceId);                   // 写入线程上下文",
  "response.setHeader(\"X-Trace-Id\", traceId);       // 回传给前端",
  "try {",
  "    chain.doFilter(request, response);",
  "} finally {",
  "    MDC.remove(\"traceId\");                      // 清理，防止线程复用污染",
  "}",
]));
children.push(p("作用：整个请求链路的日志会自动带上 traceId，排查问题时能把一次请求的所有日志串起来。"));

children.push(h3("第 ④ 步：Sa-Token 鉴权"));
children.push(p("Sa-Token 看到请求头 Authorization: Bearer xxx，从 Redis 里查这个 token 对应的 userId。如果没登录，抛 NotLoginException → GlobalExceptionHandler 返回 401。"));

children.push(h3("第 ⑤ 步：ContentController"));
children.push(...code([
  "// socialflow-web/.../controller/ContentController.java",
  "@PostMapping(\"/generate\")",
  "@RateLimiter(name = \"ai-generate\")          // Wave 1.4 限流：10 次/秒",
  "public R<ContentVO> generate(@Valid @RequestBody ContentGenerateDTO dto) {",
  "    Long userId = StpUtil.getLoginIdAsLong();     // Sa-Token 拿当前用户",
  "    return R.ok(contentService.generate(userId, dto));",
  "}",
]));
children.push(p("@Valid 触发 Jakarta Bean Validation（比如 @NotBlank 字段不能为空），不合法抛 MethodArgumentNotValidException → GlobalExceptionHandler 返回 400。"));

children.push(h3("第 ⑥ 步：ContentServiceImpl.generate —— 核心 5 步流水线"));
children.push(...code([
  "@Override",
  "public ContentVO generate(Long userId, ContentGenerateDTO dto) {",
  "    // ① 安全护栏：检查输入是否含敏感词",
  "    guardrailService.checkInput(userId, dto.getTopic());",
  "",
  "    // ② RAG：如果指定了知识库，检索参考资料",
  "    String ragContext = null;",
  "    if (dto.getKbId() != null) {",
  "        ragContext = ragPipelineService.retrieveAsContext(userId, dto.getKbId(), dto.getTopic(), 5);",
  "    }",
  "",
  "    // ③ 渲染 Prompt：把 {{topic}} {{tone}} 等变量替换成实际值",
  "    List<ChatMessage> messages = promptService.render(dto.getTemplateId(), dto.getPlatform(), vars, ragContext);",
  "",
  "    // ④ 调 LLM（这里耗时 5-30s）",
  "    LlmConfig config = buildLlmConfig(userId, dto);",
  "    LlmResponse response = llmRouter.get(defaultProvider).chat(messages, config);",
  "",
  "    // ⑤ 持久化（独立短事务，见下节）",
  "    Content entity = saveContent(userId, dto, response.getContent(), ...);",
  "",
  "    return toVo(entity);",
  "}",
]));

children.push(h3("第 ⑦ 步：LlmRouter → DeepSeekLlmProvider"));
children.push(p("LlmRouter 是个\"查表\"组件，根据 provider 名字找到对应的实现："));
children.push(...code([
  "@Component",
  "public class LlmRouter {",
  "    private final Map<LlmProvider, LlmProviderService> providers = new HashMap<>();",
  "    public LlmRouter(List<LlmProviderService> beans) {",
  "        for (LlmProviderService svc : beans) providers.put(svc.provider(), svc);",
  "    }",
  "    public LlmProviderService get(LlmProvider p) { return providers.get(p); }",
  "}",
]));
children.push(p("DeepSeekLlmProvider.chat 带两个注解（Wave 1.2）："));
children.push(...code([
  "@CircuitBreaker(name = \"llm-deepseek\", fallbackMethod = \"chatFallback\")",
  "@Retry(name = \"llm-deepseek\")",
  "public LlmResponse chat(List<ChatMessage> messages, LlmConfig config) {",
  "    String json = webClient.post().uri(...).bodyValue(body).retrieve()",
  "                           .bodyToMono(String.class).timeout(timeout).block();",
  "    return parseResponse(json);",
  "}",
]));
children.push(p("Resilience4j 会拦截方法调用："));
children.push(bullet("@Retry：失败自动重试 3 次（指数退避 1s / 2s / 4s）"));
children.push(bullet("@CircuitBreaker：过去 10 次调用有 50% 失败 → 打开熔断器，30 秒内不再调用该 provider，直接走 fallback 方法"));

children.push(h3("第 ⑧ 步：ContentPersister 独立事务（Wave 1.1）"));
children.push(callout("为什么要抽 ContentPersister", [
  "错误做法：@Transactional 加到 generate 方法上。",
  "问题：LLM 调用要 5-30 秒，事务会持有一个 DB 连接整整 30 秒。",
  "50 个用户并发 → 50 个 DB 连接被占用 → 连接池耗尽 → 系统挂。",
  "",
  "正确做法：",
  "• generate 方法不加 @Transactional（不占连接）",
  "• 把「Content + ContentVersion 双表写入」抽到 ContentPersister.insertWithVersion()，它加 @Transactional",
  "• 事务只覆盖最后几毫秒的 DB 写入",
]));
children.push(...code([
  "// socialflow-service/.../content/support/ContentPersister.java",
  "@Component",
  "public class ContentPersister {",
  "    @Transactional(rollbackFor = Exception.class)",
  "    public Content insertWithVersion(Content entity, String versionDesc) {",
  "        contentMapper.insert(entity);",
  "        saveVersion(entity.getId(), entity.getBody(), versionDesc);",
  "        return entity;",
  "    }",
  "}",
]));

children.push(h3("第 ⑨ 步：返回 R.ok(vo)"));
children.push(p("Spring 把 ContentVO 序列化成 JSON，Jackson 默认配置。Content 里的 createTime（LocalDateTime）会按 ISO 格式输出。"));
children.push(new Paragraph({ children: [new PageBreak()] }));

// ============================================================
// 第 7 章：RAG 知识库详解
// ============================================================
children.push(h1("7  RAG 知识库深入"));

children.push(h2("7.1  整体流程"));
children.push(img("04_rag.png", 640, 260));
children.push(caption("图 7-1：RAG 8 步流水线"));
children.push(p("代码在 socialflow-service/.../ai/rag/impl/RagPipelineServiceImpl.java，约 300 行。下面逐步拆解。"));

children.push(h2("7.2  从上传文档说起"));
children.push(p("用户上传 PDF → KnowledgeIngestServiceImpl.ingest()（@Async 异步执行）："));
children.push(...code([
  "1. 从 COS 下载原文件",
  "2. Apache Tika 解析（PDF/Word/Markdown 都能认）→ 纯文本",
  "3. DocumentChunker 切片：按 512 token 一块，相邻重叠 64 token",
  "4. EmbeddingService.embedBatch 批量调 DashScope → 每块一个 1024 维向量",
  "5. 分别写 MySQL knowledge_chunk（存原文）+ PostgreSQL kb_chunks（存向量）",
  "6. 更新 knowledge_document.parse_status = COMPLETED",
]));
children.push(callout("为什么 MySQL 存原文、PG 存向量", [
  "MySQL 擅长结构化查询、ACID 事务、BM25 FULLTEXT 索引（关键字搜索）",
  "PG + pgvector 擅长高维向量相似度（HNSW 索引）",
  "各自做自己最擅长的 —— 一块 chunk 同时存在两个库，用 chunk_id 关联",
]));

children.push(h2("7.3  检索流程（RagPipelineServiceImpl.retrieve）"));

children.push(h3("第 ①② 步：HyDE + Embedding"));
children.push(p("用户问\"春季护肤有哪些要点\"。直接用这 10 个字做向量检索，效果一般。"));
children.push(p("HyDE（Hypothetical Document Embeddings）让 LLM 先生成一段\"假设性回答\"："));
children.push(...code([
  "假设性回答：春季护肤要注意加强保湿、温和清洁、防晒。",
  "油性肌肤选用清爽型产品，干性肌肤需加强补水..."
]));
children.push(p("然后用这段 100 字的答案做向量检索 —— 匹配知识库的措辞风格，召回率更高。"));

children.push(h3("第 ③ 步：向量检索（PgVectorStoreServiceImpl.search）"));
children.push(...code([
  "-- 核心 SQL",
  "SELECT id, kb_id, doc_id, chunk_index,",
  "       1 - (embedding <=> ?::vector) AS score   -- 余弦相似度（0-1，越大越相似）",
  "FROM kb_chunks",
  "WHERE kb_id = ?                                  -- 隔离：只搜用户指定的知识库",
  "ORDER BY embedding <=> ?::vector                 -- <=> 是 pgvector 余弦距离运算符",
  "LIMIT 10;",
]));
children.push(p("HNSW 索引让这个查询在 15000 条向量里 10ms 内返回。"));

children.push(h3("第 ④ 步：BM25 关键词检索（KnowledgeChunkMapper.fulltextSearch）"));
children.push(p("纯向量检索会漏掉一些精确关键字匹配（比如型号、专有名词）。所以并行做 BM25："));
children.push(...code([
  "-- MySQL FULLTEXT 索引 + ngram 分词器",
  "SELECT * FROM knowledge_chunk",
  "WHERE kb_id = ?",
  "  AND MATCH(content_text) AGAINST(? IN NATURAL LANGUAGE MODE)",
  "ORDER BY MATCH(content_text) AGAINST(? IN NATURAL LANGUAGE MODE) DESC",
  "LIMIT 10;",
]));

children.push(h3("第 ⑤ 步：RRF 融合"));
children.push(p("两路召回各 Top-10，怎么合并？"));
children.push(p("直接比较分数不行：向量是余弦相似度（0-1），BM25 是 TF-IDF 加权分（0-∞），不在一个量纲。"));
children.push(p("RRF（Reciprocal Rank Fusion）只看\"排名\"："));
children.push(...code([
  "score(doc) = 1/(60 + rank_vector) + 1/(60 + rank_bm25)",
  "",
  "// 例子：doc_X 在向量第 3 名、BM25 第 8 名",
  "// score = 1/63 + 1/68 = 0.0159 + 0.0147 = 0.0306",
  "// doc_Y 在向量第 1 名、BM25 缺席",
  "// score = 1/61 + 0 = 0.0164",
  "// doc_X 同时被两路看好，最终排名更高",
]));

children.push(h3("第 ⑥ 步：Hydrate（补全）"));
children.push(p("前面只有 chunk_id，现在 selectBatchIds 从 MySQL 取完整文本。"));

children.push(h3("第 ⑦ 步：Reranker 精排（Wave 4.1 真实实现）"));
children.push(p("DashScope gte-rerank 接收 query 和 texts[] 返回每个的相关度分数。Wave 4.1 之前这里是个空实现，现在真正调用了。"));
children.push(...code([
  "// socialflow-service/.../ai/rag/impl/RerankerServiceImpl.java",
  "List<ScoredIndex> reranked = rerankerService.rerank(query, texts, 5);",
  "// 返回 [{index: 2, score: 0.95}, {index: 0, score: 0.87}, ...]",
  "// index 是在 fused 列表里的位置，按 score 降序",
]));

children.push(h3("第 ⑧ 步：转 VO + 填充 snippet"));
children.push(...code([
  "// Wave 4.1 加的 snippet：找 query 首次出现位置 ±80 字符",
  "protected String extractSnippet(String text, String query, int halfWindow) {",
  "    int hit = text.indexOf(query);",
  "    int start = Math.max(0, hit - halfWindow);",
  "    int end = Math.min(text.length(), hit + halfWindow);",
  "    return (start > 0 ? \"…\" : \"\") + text.substring(start, end) + ",
  "           (end < text.length() ? \"…\" : \"\");",
  "}",
]));
children.push(p("前端显示时把 snippet 高亮成黄底，全文折叠到\"查看完整片段\"。"));
children.push(new Paragraph({ children: [new PageBreak()] }));

// ============================================================
// 第 8 章：AI 文生图
// ============================================================
children.push(h1("8  AI 文生图"));

children.push(h2("8.1  DashScope wanx 异步流程"));
children.push(img("05_image_fsm.png", 600, 280));
children.push(caption("图 8-1：文生图异步流程 + Wave 4.2 去重缓存"));

children.push(h2("8.2  为什么是异步的"));
children.push(p("wanx 一次生成 4 张 1024×1024 图，大约 30-60 秒。如果同步调用，前端 HTTP 请求会卡半分钟被网关超时断开。"));
children.push(p("所以 DashScope 设计成异步："));
children.push(bullet("submit：提交任务，立刻返回 task_id"));
children.push(bullet("getStatus：轮询，状态从 PENDING → RUNNING → SUCCEEDED / FAILED"));
children.push(bullet("SUCCEEDED 时返回 4 个临时 URL（有效期 24h）"));

children.push(h2("8.3  Wave 4.2 的去重缓存"));
children.push(p("同样的 prompt，不应该重新花钱再生成一次。"));
children.push(...code([
  "-- Flyway V3__image_asset_cache.sql",
  "CREATE TABLE image_asset_cache (",
  "  id BIGINT PRIMARY KEY,",
  "  user_id BIGINT NOT NULL,",
  "  prompt_hash CHAR(64) NOT NULL,       -- SHA-256(prompt + ':' + model + ':' + size)",
  "  media_ids JSON NOT NULL,             -- 已下载到 COS 的 MediaAsset.id 列表",
  "  UNIQUE KEY uk_user_prompt_hash (user_id, prompt_hash)",
  ");",
]));
children.push(p("ImageController.generate 命中缓存时直接返回："));
children.push(...code([
  "{",
  "  \"cached\": true,",
  "  \"mediaIds\": [12345, 12346, 12347, 12348],",
  "  \"imageUrls\": [\"https://cos.../ai_xxx1.png\", ...]",
  "}",
]));
children.push(p("未命中时返回 {cached: false, taskId: \"xxx\"}，前端走轮询流程。"));

children.push(h2("8.4  多变体选择 + selectVariants"));
children.push(p("生成 4 张，用户选 1-4 张下载到 COS。前端调 POST /api/v1/image/select："));
children.push(...code([
  "// ImageController.selectVariants",
  "for (Integer idx : selected) {",
  "    MediaAsset asset = imageService.downloadAndSave(userId, imageUrls.get(idx), tags);",
  "    saved.add(asset);",
  "    savedIds.add(asset.getId());",
  "}",
  "imageService.saveCache(userId, prompt, imageModel, imageSize, savedIds);   // 写缓存",
]));
children.push(new Paragraph({ children: [new PageBreak()] }));

// ============================================================
// 第 9 章：发布系统
// ============================================================
children.push(h1("9  发布系统"));

children.push(h2("9.1  策略模式：Publisher 接口 + 4 种实现"));
children.push(p("4 个社交平台的发布方式完全不同，但对外要有统一接口。这就是经典的\"策略模式\""));
children.push(...code([
  "public interface Publisher {",
  "    PlatformType platform();                                      // 我负责哪个平台",
  "    boolean supportsAuto();                                       // 是否支持自动发布",
  "    PublishResult publish(Content content, PlatformAccount acc);  // 真发布",
  "    PublishResult prepare(Content content);                       // 辅助发布（打包 ZIP）",
  "}",
]));
children.push(p("4 个实现："));
children.push(table(
  ["实现", "平台", "支持 auto", "特点"],
  [
    ["WechatMpPublisher", "微信公众号", "✓", "草稿 → 发布 → 降级 mpnews 三层"],
    ["WeiboPublisher", "微博", "—", "当前只有 prepare（需微博开放平台 API）"],
    ["XiaohongshuPublisher", "小红书", "—", "只能 prepare（无官方 API）"],
    ["DouyinPublisher", "抖音", "—", "只能 prepare（企业资质）"],
  ],
  [2400, 1800, 1400, 3760],
));

children.push(h2("9.2  PublishRouter：根据平台选实现"));
children.push(p("跟 LlmRouter 一样的套路，构造器里自动收集所有 Publisher Bean："));
children.push(...code([
  "@Component",
  "public class PublishRouter {",
  "    private final Map<PlatformType, Publisher> publishers = new HashMap<>();",
  "    public PublishRouter(List<Publisher> beans) {",
  "        for (Publisher p : beans) publishers.put(p.platform(), p);",
  "    }",
  "    public Publisher get(PlatformType type) { return publishers.get(type); }",
  "}",
]));

children.push(h2("9.3  Wave 3.1：定时发布执行器"));
children.push(img("06_publish_fsm.png", 600, 340));
children.push(caption("图 9-1：PublishTask 状态机"));
children.push(p("Wave 3.1 前的问题：PublishTask 表有 scheduled_time 字段，但没有任何 @Scheduled 消费它 —— 定时发布功能是个空架子。"));
children.push(p("Wave 3.1 新增 ScheduledPublishExecutor："));
children.push(...code([
  "@Scheduled(fixedDelay = 30_000)",
  "@SchedulerLock(name = \"scheduled-publish-scan\", lockAtMostFor = \"PT5M\", lockAtLeastFor = \"PT5S\")",
  "public void scanAndExecute() {",
  "    List<PublishTask> due = publishTaskMapper.selectList(",
  "        new LambdaQueryWrapper<PublishTask>()",
  "            .eq(PublishTask::getPublishType, \"SCHEDULED\")",
  "            .eq(PublishTask::getStatus, \"PENDING\")",
  "            .le(PublishTask::getScheduledTime, LocalDateTime.now())",
  "            .last(\"LIMIT 50\"));",
  "    for (PublishTask task : due) executeOne(task);",
  "}",
]));
children.push(callout("两个关键锁", [
  "① ShedLock + Redis：多实例部署时，只有一个实例拿到 scheduled-publish-scan 锁，其他实例本轮跳过。防止重复执行。",
  "② DB 乐观锁：UPDATE publish_task SET status='EXECUTING' WHERE id=? AND status='PENDING'。即使两个实例都拿到锁，执行单个 task 时只有一个 UPDATE 成功。",
]));
children.push(p("失败处理（指数退避）："));
children.push(...code([
  "if (retryCount < MAX_RETRY) {",
  "    long backoffSeconds = (long) Math.pow(2, retryCount) * 60L;  // 1min, 2min, 4min",
  "    task.setStatus(\"PENDING\");",
  "    task.setScheduledTime(LocalDateTime.now().plusSeconds(backoffSeconds));",
  "} else {",
  "    task.setStatus(\"FAILED_PERMANENT\");     // 转永久失败，等用户手动 retry",
  "}",
]));
children.push(new Paragraph({ children: [new PageBreak()] }));

// ============================================================
// 第 10 章：数据层细节
// ============================================================
children.push(h1("10  数据层细节"));

children.push(h2("10.1  表关系"));
children.push(img("07_er.png", 620, 450));
children.push(caption("图 10-1：核心表关系简图"));

children.push(h2("10.2  BaseEntity：统一审计字段"));
children.push(p("每张表都有 id、create_time、update_time、is_deleted 4 个通用字段。重复 18 次太 low —— 抽到 BaseEntity："));
children.push(...code([
  "@Data",
  "public abstract class BaseEntity implements Serializable {",
  "    @TableId(type = IdType.ASSIGN_ID)                  // 雪花算法生成 Long 主键",
  "    private Long id;",
  "    @TableField(fill = FieldFill.INSERT)               // 插入时自动填 now()",
  "    private LocalDateTime createTime;",
  "    @TableField(fill = FieldFill.INSERT_UPDATE)        // 插入 + 更新时自动填 now()",
  "    private LocalDateTime updateTime;",
  "    @TableLogic                                         // 软删除：UPDATE ... SET is_deleted=1",
  "    private Integer isDeleted;",
  "}",
]));
children.push(p("所有 Entity 继承它："));
children.push(...code([
  "public class Content extends BaseEntity {",
  "    private Long userId;",
  "    private String title;",
  "    private String body;",
  "    // ... 只写业务字段",
  "}",
]));

children.push(h2("10.3  Flyway：数据库版本管理"));
children.push(p("以前的做法：数据库改动写 SQL 文件丢群里，\"大家记得跑一下\"。结果永远有人漏。"));
children.push(p("Flyway 的做法：SQL 文件放 src/main/resources/db/migration/，按版本号命名，启动时自动跑未执行过的版本。"));
children.push(...code([
  "socialflow-admin/src/main/resources/db/migration/",
  "├── V1__init_schema.sql            ← 18 张表的 CREATE TABLE",
  "├── V2__seed_templates.sql         ← 系统预置 Prompt 模板",
  "├── V3__image_asset_cache.sql      ← Wave 4.2 去重表",
  "├── V4__content_optimistic_lock.sql ← Wave 4.3 加 version 列",
  "├── V5__media_asset_dedup.sql      ← Wave 4.5 加 sha256/width/height",
  "└── V6__eval_task_pvalue.sql       ← Wave 4.6 加 p_value 列",
]));
children.push(callout("baseline-version=2 的巧思", [
  "生产库已经有完整 schema 和数据，不能重跑 V1 V2（DROP TABLE 会丢数据）。",
  "baseline-on-migrate=true + baseline-version=2：",
  "• 首次启动发现 flyway_schema_history 表不存在，创建它，把 V1/V2 标记为『已跳过』",
  "• 从 V3 开始正常执行",
  "新部署（空库）走 V1 完整建表 + V2 种子数据，不受影响。",
]));

children.push(h2("10.4  @Transactional：事务最佳实践"));
children.push(p("Wave 1.1 的修正原则："));
children.push(bulletRich([{ text: "写操作", bold: true }, " → @Transactional(rollbackFor = Exception.class)"]));
children.push(bulletRich([{ text: "只读查询", bold: true }, " → @Transactional(readOnly = true)（优化器 hint，不开写事务）"]));
children.push(bulletRich([{ text: "方法里有外部调用（LLM/HTTP）", bold: true }, " → 不加 @Transactional，把 DB 写入部分抽 helper"]));
children.push(bulletRich([{ text: "@Async 方法", bold: true }, " → 通常不加（异步里 rollback 不好处理）"]));
children.push(new Paragraph({ children: [new PageBreak()] }));

// ============================================================
// 第 11 章：基础设施
// ============================================================
children.push(h1("11  基础设施（Wave 1-2 详解）"));

children.push(h2("11.1  Resilience4j：弹性四件套"));
children.push(p("AI API / 第三方服务经常抽风。Resilience4j 让你一行注解搞定弹性。"));
children.push(table(
  ["注解", "作用", "SocialFlow 配置"],
  [
    ["@Retry", "失败自动重试", "3 次，指数退避 1s/2s/4s"],
    ["@CircuitBreaker", "熔断", "10 次采样，50% 失败率触发，开 30s"],
    ["@RateLimiter", "限流", "ai-generate: 10/秒，ai-image: 3/秒"],
    ["@TimeLimiter", "超时", "SocialFlow 没用（WebClient 自己有 timeout）"],
  ],
  [2000, 2800, 4560],
));
children.push(p("application.yml 里的配置（Wave 1.2）："));
children.push(...code([
  "resilience4j:",
  "  retry:",
  "    instances:",
  "      llm-deepseek:",
  "        max-attempts: 3",
  "        wait-duration: 1s",
  "        exponential-backoff-multiplier: 2",
  "        retry-exceptions:            # 只重试这些临时性错误",
  "          - java.io.IOException",
  "          - java.util.concurrent.TimeoutException",
  "          - com.socialflow.common.exception.AiCallException",
  "        ignore-exceptions:           # 这些业务异常不重试",
  "          - com.socialflow.common.exception.GuardrailException",
  "          - com.socialflow.common.exception.QuotaExceededException",
]));

children.push(h2("11.2  Spring Cache + Redis"));
children.push(p("Wave 1.3 的 CacheConfig 定义了 7 个 cache 命名空间，每个独立 TTL："));
children.push(table(
  ["Cache 名", "TTL", "用在哪"],
  [
    ["wechatAccessToken", "110 分钟", "微信公众号 API token（官方 2h 过期，提前 10 分钟）"],
    ["promptTemplate", "10 分钟", "PromptServiceImpl.getByCode"],
    ["guardrailDict", "30 分钟", "敏感词字典"],
    ["embeddingCache", "24 小时", "EmbeddingServiceImpl.embed（最大成本节约点）"],
    ["kbChunkSearch", "5 分钟", "知识库检索结果"],
    ["platformAccount", "5 分钟", "第三方平台账号信息"],
    ["dashboardOverview", "1 分钟", "Dashboard overview 聚合"],
  ],
  [2400, 1400, 5560],
));
children.push(p("使用方式："));
children.push(...code([
  "@Override",
  "@Cacheable(value = \"embeddingCache\", key = \"T(cn.hutool.crypto.SecureUtil).sha256(#text)\")",
  "@Retry(name = \"embedding-api\")",
  "public float[] embed(String text) {",
  "    // 真正调 DashScope 的代码...",
  "}",
]));
children.push(p("Spring AOP 拦截：先用 SHA-256(text) 查 Redis，命中直接返回 float[]，未命中才执行方法体 + 把结果写 Redis。"));

children.push(h2("11.3  Actuator + Prometheus（Wave 2.1）"));
children.push(p("Actuator 暴露一堆运维端点，在 application.yml 里配哪些对外："));
children.push(...code([
  "management:",
  "  endpoints:",
  "    web:",
  "      exposure:",
  "        include: health,info,prometheus,metrics,circuitbreakers,retries,ratelimiters,caches,flyway",
]));
children.push(p("关键端点："));
children.push(table(
  ["端点", "作用"],
  [
    ["/actuator/health", "健康检查（k8s probe 用）"],
    ["/actuator/prometheus", "所有指标按 Prometheus 格式暴露，供 Grafana 抓取"],
    ["/actuator/circuitbreakers", "Resilience4j 熔断器当前状态（CLOSED/OPEN/HALF_OPEN）"],
    ["/actuator/flyway", "已执行的 migration 列表"],
    ["/actuator/caches", "所有 cache 名 + 命中率"],
  ],
  [2800, 6560],
));
children.push(p("prod 环境在 application-prod.yml 里收紧：只暴露 health / info / prometheus。"));

children.push(h2("11.4  MDC traceId（Wave 2.2）"));
children.push(p("MDC = Mapped Diagnostic Context，是 SLF4J 提供的\"线程级变量\"。"));
children.push(p("MdcTraceIdFilter 在请求入口写 traceId 到 MDC，logback pattern 里的 %X{traceId} 自动取出来："));
children.push(...code([
  "<!-- logback-spring.xml -->",
  "<property name=\"LOG_PATTERN\" value=\"%d{HH:mm:ss.SSS} [%thread] [%X{traceId:-}] %-5level %logger{40} - %msg%n\"/>",
]));
children.push(p("效果："));
children.push(...code([
  "10:23:45.123 [http-nio-8080-exec-1] [a3b9e2f1...] INFO  c.s.w.c.ContentController - generate start",
  "10:23:45.145 [http-nio-8080-exec-1] [a3b9e2f1...] INFO  c.s.s.c.i.ContentServiceImpl - LLM call begin",
  "10:23:58.992 [http-nio-8080-exec-1] [a3b9e2f1...] INFO  c.s.s.c.i.ContentServiceImpl - LLM completed",
]));
children.push(p("排查问题时：用户报 error，把他看到的 X-Trace-Id 头复制过来，grep 日志一行就能捞出整条链路。"));
children.push(new Paragraph({ children: [new PageBreak()] }));

// ============================================================
// 第 12 章：前端基础
// ============================================================
children.push(h1("12  前端基础"));

children.push(h2("12.1  Vue 3 组合式 API（Composition API）"));
children.push(p("Vue 2 是选项式 API（data / computed / methods 分段）。Vue 3 推出组合式 API："));
children.push(...code([
  "<script setup lang=\"ts\">",
  "import { ref, computed, onMounted } from 'vue'",
  "import { contentApi } from '@/api/content'",
  "",
  "const loading = ref(false)                       // 响应式变量",
  "const list = ref<ContentVO[]>([])",
  "",
  "const count = computed(() => list.value.length) // 计算属性",
  "",
  "async function loadData() {",
  "  loading.value = true",
  "  try {",
  "    const res = await contentApi.list({ pageNum: 1, pageSize: 20 })",
  "    list.value = res.records",
  "  } finally {",
  "    loading.value = false",
  "  }",
  "}",
  "",
  "onMounted(loadData)                              // 生命周期钩子",
  "</script>",
]));
children.push(callout(".value 是怎么回事", [
  "ref() 把普通值包一层响应式。在 <script> 里要 .value 访问和赋值。",
  "在 <template> 里 Vue 自动解包，直接写 {{ loading }} 而不是 {{ loading.value }}。",
]));

children.push(h2("12.2  Pinia：状态管理"));
children.push(p("用户登录后的 token 要全局访问。Pinia 是 Vue 官方推荐的 store 库。"));
children.push(...code([
  "// stores/user.ts",
  "export const useUserStore = defineStore('user', () => {",
  "  const token = ref<string | null>(null)",
  "  const userInfo = ref<UserVO | null>(null)",
  "  ",
  "  async function login(dto: LoginDTO) {",
  "    const res = await authApi.login(dto)",
  "    token.value = res.token",
  "    userInfo.value = res.user",
  "    localStorage.setItem('token', res.token)",
  "  }",
  "  ",
  "  function clear() {",
  "    token.value = null",
  "    userInfo.value = null",
  "    localStorage.removeItem('token')",
  "  }",
  "  ",
  "  return { token, userInfo, login, clear }",
  "})",
]));

children.push(h2("12.3  Axios 拦截器"));
children.push(p("每次请求自动加 token、自动处理错误 —— 在 src/api/http.ts 集中配置，不用每个接口写一遍。"));
children.push(...code([
  "// 请求拦截器：自动塞 token",
  "http.interceptors.request.use((config) => {",
  "  const userStore = useUserStore()",
  "  if (userStore.token) {",
  "    config.headers.Authorization = `Bearer ${userStore.token}`",
  "  }",
  "  return config",
  "})",
  "",
  "// 响应拦截器（Wave FE-1 升级）",
  "http.interceptors.response.use(",
  "  (response) => {",
  "    const data = response.data as R<unknown>",
  "    if (data.code !== 200) {",
  "      ElMessage.error(data.message)            // 统一弹错误",
  "      return Promise.reject(new Error(data.message))",
  "    }",
  "    return response",
  "  },",
  "  (error) => {",
  "    const status = error.response?.status",
  "    if (status === 401) {                      // 未登录 → 跳登录页",
  "      useUserStore().clear()",
  "      window.location.href = '/login'",
  "    } else if (status === 429) {               // 限流 → 友好提示",
  "      ElMessage.warning('请求过于频繁，请稍后再试')",
  "    }",
  "    // Wave FE-1: 从响应头取 traceId 拼到错误里",
  "    const traceId = error.response?.headers['x-trace-id']",
  "    if (traceId) error.traceId = traceId",
  "    return Promise.reject(error)",
  "  }",
  ")",
]));

children.push(h2("12.4  Element Plus：UI 组件库"));
children.push(p("项目里看到的 <el-button>、<el-table>、<el-dialog> 都来自 Element Plus。它和 Vue 3 深度配合，开箱即用："));
children.push(bullet("el-form + rules 实现前端表单校验"));
children.push(bullet("el-table 支持分页、排序、批量选择"));
children.push(bullet("el-dialog 模态对话框"));
children.push(bullet("ElMessage.success / warning / error 全局提示"));
children.push(new Paragraph({ children: [new PageBreak()] }));

// ============================================================
// 第 13 章：部署
// ============================================================
children.push(h1("13  部署到生产"));

children.push(h2("13.1  生产拓扑"));
children.push(img("08_deploy.png", 600, 340));
children.push(caption("图 13-1：***REDACTED-PROD-IP*** 单机部署"));

children.push(h2("13.2  部署流程"));
children.push(p("完整步骤（从本地开发机到生产）："));
children.push(rich([{ text: "① ", bold: true }, "本地编译：", { text: "mvn -pl socialflow-admin -am clean package -DskipTests", mono: true }]));
children.push(rich([{ text: "② ", bold: true }, "前端构建：", { text: "cd socialflow-ui && npm run build", mono: true }]));
children.push(rich([{ text: "③ ", bold: true }, "MySQL 备份：", { text: "mysqldump -uroot -p${PWD} socialflow > backups/${TS}/mysql.sql", mono: true }]));
children.push(rich([{ text: "④ ", bold: true }, "停后端：", { text: "pkill -f 'java.*socialflow.jar'", mono: true }]));
children.push(rich([{ text: "⑤ ", bold: true }, "备份旧 jar 和 dist 到 backups/${TS}/"]));
children.push(rich([{ text: "⑥ ", bold: true }, "上传新 jar → /opt/socialflow/socialflow.jar"]));
children.push(rich([{ text: "⑦ ", bold: true }, "上传前端 dist/* → /opt/socialflow/web/"]));
children.push(rich([{ text: "⑧ ", bold: true }, "启动：", { text: "nohup SPRING_PROFILES_ACTIVE=prod java -jar socialflow.jar > logs/app.log 2>&1 &", mono: true }]));
children.push(rich([{ text: "⑨ ", bold: true }, "等 30-60s，curl /actuator/health 确认 200"]));
children.push(rich([{ text: "⑩ ", bold: true }, "Flyway 检查：", { text: "SELECT * FROM flyway_schema_history ORDER BY installed_rank", mono: true }]));

children.push(h2("13.3  Nginx 配置"));
children.push(...code([
  "# /etc/nginx/conf.d/socialflow.conf",
  "server {",
  "    listen 80;",
  "    server_name ***REDACTED-PROD-IP***;",
  "    client_max_body_size 50M;    # 允许上传大文件（知识库 PDF）",
  "",
  "    # 前端静态",
  "    location / {",
  "        root /opt/socialflow/web;",
  "        try_files $uri $uri/ /index.html;  # Vue Router history 模式兜底",
  "    }",
  "",
  "    # 后端 API",
  "    location /api/ {",
  "        proxy_pass http://127.0.0.1:8080;",
  "        proxy_set_header Host $host;",
  "        proxy_set_header X-Real-IP $remote_addr;",
  "        proxy_read_timeout 300s;    # SSE 流式生成需要长连接",
  "        proxy_buffering off;        # 不要缓冲 SSE",
  "    }",
  "}",
]));

children.push(h2("13.4  回滚"));
children.push(p("部署出问题 10 秒内回滚："));
children.push(...code([
  "pkill -9 -f 'java.*socialflow.jar'",
  "cp /opt/socialflow/backups/<TS>/socialflow.jar.bak /opt/socialflow/socialflow.jar",
  "mysql -uroot -p***REDACTED-DB-PASSWORD*** socialflow < /opt/socialflow/backups/<TS>/mysql.sql",
  "cd /opt/socialflow && nohup java -jar socialflow.jar > logs/app.log 2>&1 &",
]));
children.push(callout("最近一次部署的避坑经验", [
  "Wave 2 之后首次部署启动失败 —— logback 新版本不允许 TimeBasedRollingPolicy 的 fileNamePattern 里带 %i。",
  "修复：改用 SizeAndTimeBasedRollingPolicy（支持 %i 按大小+时间双轮转）。",
  "教训：重大依赖升级后，先在本地 SPRING_PROFILES_ACTIVE=prod 启动一次验证。",
]));
children.push(new Paragraph({ children: [new PageBreak()] }));

// ============================================================
// 第 14 章：学习路径
// ============================================================
children.push(h1("14  学习路径建议"));

children.push(h2("14.1  第一周：跑起来 + 能读懂"));
children.push(bullet("Day 1：本地环境搭建（JDK 21 / Maven 3.8+ / MySQL 8 / Redis / Node 18+）"));
children.push(bullet("Day 2：克隆代码 mvn package + npm run build，在本地跑起来，注册登录走一遍"));
children.push(bullet("Day 3：只看 socialflow-common（最小），理解 R<T> / 异常 / BaseEntity"));
children.push(bullet("Day 4：看 socialflow-model，认每个 Entity 对应哪张表"));
children.push(bullet("Day 5：看 UserController + UserServiceImpl，最简单的业务"));

children.push(h2("14.2  第二周：核心流程深入"));
children.push(bullet("Day 6-7：ContentController + ContentServiceImpl（843 行！分三天看 generate / list / update）"));
children.push(bullet("Day 8-9：RAG 流水线 RagPipelineServiceImpl，配合第 7 章看"));
children.push(bullet("Day 10：Publisher 策略模式 + ScheduledPublishExecutor"));

children.push(h2("14.3  第三周：基础设施 + 前端"));
children.push(bullet("Day 11：Wave 1.1-1.3 的改动（事务 + 弹性 + 缓存）"));
children.push(bullet("Day 12：MDC + Prometheus 指标，curl /actuator/prometheus 看真实指标"));
children.push(bullet("Day 13-14：前端入门（Vue + Pinia + Element Plus），改一个页面"));

children.push(h2("14.4  动手练习建议（按难度递增）"));
children.push(bullet("📝 easy：把某个 Controller 加一个 @Operation 描述，让 Knife4j 文档更友好"));
children.push(bullet("📝 easy：给 Content 加一个 views 字段（浏览量），写 Flyway V7__content_views.sql"));
children.push(bullet("📝 medium：实现一个新 Publisher（比如 Bilibili），照 XiaohongshuPublisher 抄 prepare()"));
children.push(bullet("📝 medium：给 Dashboard 增加一个\"Top 5 最常用模板\"的端点"));
children.push(bullet("📝 hard：实现 Wave 3.3 微博 auto-publish（需注册微博开放平台账号）"));
children.push(bullet("📝 hard：把 ContentPersister 的 softDelete 改造成\"软删除 + 30 天后自动清理\"（加 @Scheduled）"));

children.push(h2("14.5  排查问题的三板斧"));
children.push(rich([{ text: "① 看 traceId", bold: true }, "：前端错误页面应该显示 (traceId: xxx)，服务端 grep 日志。"]));
children.push(rich([{ text: "② 看 Actuator", bold: true }, "：curl /actuator/circuitbreakers 看是不是某个 LLM 熔断了；/actuator/caches 看缓存命中率。"]));
children.push(rich([{ text: "③ 看 MySQL", bold: true }, "：慢查询日志 + EXPLAIN 分析。Flyway 版本 SELECT * FROM flyway_schema_history。"]));

children.push(h2("14.6  推荐延伸阅读"));
children.push(bullet("《Spring Boot 实战》—— 基础"));
children.push(bullet("《MyBatis 从入门到精通》—— ORM 深入"));
children.push(bullet("Spring 官方文档（spring.io/projects）"));
children.push(bullet("Resilience4j 官方文档（resilience4j.readme.io）"));
children.push(bullet("Vue 3 官方文档（cn.vuejs.org）"));
children.push(bullet("《Designing Data-Intensive Applications》（强烈推荐，虽然难但打开你的眼界）"));

children.push(new Paragraph({ children: [new PageBreak()] }));

children.push(h1("15  结语"));
children.push(p("欢迎来到真实世界的 Java 开发。"));
children.push(p("SocialFlow 这个项目看起来庞大（32,000+ 行代码、6 个模块、18 张表、10+ 外部服务），但每一部分都是为了解决一个具体问题而引入的。"));
children.push(p("读代码时记住这个思维："));
children.push(bullet("为什么要有这个类 / 这个注解 / 这个层级？"));
children.push(bullet("去掉它会怎样？会有什么风险暴露出来？"));
children.push(bullet("这个设计避免了哪些其他项目常见的坑？"));
children.push(p("想清楚\"为什么\"，代码就不再是死的了。"));
children.push(p(""));
children.push(p("祝你学习顺利 🚀", { size: 24, bold: true, color: "1565C0", align: AlignmentType.CENTER }));
children.push(p(""));
children.push(p(""));
children.push(p("—— SocialFlow 项目组", { italic: true, color: "666666", align: AlignmentType.CENTER }));
children.push(p("2026.04", { italic: true, color: "666666", align: AlignmentType.CENTER }));

// ============================================================
// 组装 Document
// ============================================================
const doc = new Document({
  creator: "SocialFlow",
  title: "SocialFlow 小白学习手册",
  description: "给刚入行 Java 工程师的完整项目学习指南",
  styles: {
    default: { document: { run: { font: FONT, size: 22 } } },
    paragraphStyles: [
      { id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 36, bold: true, font: FONT, color: "1565C0" },
        paragraph: { spacing: { before: 400, after: 200 }, outlineLevel: 0 } },
      { id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 30, bold: true, font: FONT, color: "1976D2" },
        paragraph: { spacing: { before: 320, after: 160 }, outlineLevel: 1 } },
      { id: "Heading3", name: "Heading 3", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 26, bold: true, font: FONT, color: "2E7D32" },
        paragraph: { spacing: { before: 240, after: 120 }, outlineLevel: 2 } },
    ],
  },
  numbering: {
    config: [{
      reference: "bullets",
      levels: [
        { level: 0, format: LevelFormat.BULLET, text: "•", alignment: AlignmentType.LEFT,
          style: { paragraph: { indent: { left: 720, hanging: 360 } } } },
        { level: 1, format: LevelFormat.BULLET, text: "◦", alignment: AlignmentType.LEFT,
          style: { paragraph: { indent: { left: 1440, hanging: 360 } } } },
      ],
    }],
  },
  sections: [{
    properties: {
      page: {
        size: { width: 12240, height: 15840 },   // US Letter
        margin: { top: 1440, right: 1440, bottom: 1440, left: 1440 },
      },
    },
    headers: {
      default: new Header({
        children: [new Paragraph({
          alignment: AlignmentType.RIGHT,
          children: [new TextRun({ text: "SocialFlow 小白学习手册", font: FONT, size: 18, color: "888888" })],
        })],
      }),
    },
    footers: {
      default: new Footer({
        children: [new Paragraph({
          alignment: AlignmentType.CENTER,
          children: [
            new TextRun({ text: "— ", font: FONT, size: 18, color: "888888" }),
            new TextRun({ children: [PageNumber.CURRENT], font: FONT, size: 18, color: "888888" }),
            new TextRun({ text: " —", font: FONT, size: 18, color: "888888" }),
          ],
        })],
      }),
    },
    children,
  }],
});

Packer.toBuffer(doc).then(buf => {
  const out = path.join(__dirname, "SocialFlow小白学习手册.docx");
  fs.writeFileSync(out, buf);
  console.log("✓ 生成完成:", out);
  console.log("  文件大小:", (buf.length / 1024).toFixed(1), "KB");
});
