package com.socialflow.common.constant;

/**
 * 全局常量定义类
 *
 * 【作用】集中管理整个应用中使用的常量值，避免"魔法字符串/数字"散落在代码各处。
 *   所有需要跨模块共享的常量都定义在这里。
 *
 * 【为什么用 final class + 私有构造方法】
 *   - final class：禁止被继承，因为常量类不需要子类
 *   - 私有构造方法：禁止被实例化，因为只需要通过 CommonConstants.XXX 访问静态常量
 *
 * 【命名约定】
 *   - API_PREFIX：API路径前缀
 *   - SSE_EVENT_*：SSE事件类型
 *   - CK_*：缓存Key模板（CK = Cache Key）
 *   - VC_*：向量数据库集合名（VC = Vector Collection）
 *   - TL_*：线程本地变量Key（TL = Thread Local）
 */
public final class CommonConstants {

    /** API统一路径前缀，所有REST接口都以这个前缀开头，如 /api/v1/content、/api/v1/user */
    public static final String API_PREFIX = "/api/v1";

    // ==================== SSE 事件类型 ====================
    // SSE（Server-Sent Events）是一种服务器向浏览器推送实时消息的技术。
    // AI生成文案时，后端通过SSE逐步推送生成进度，前端实时显示"打字机效果"。
    // 下面定义了不同的事件类型，前端根据事件类型做不同处理。

    /** SSE事件：普通消息（AI生成的文本片段），前端收到后追加显示 */
    public static final String SSE_EVENT_MESSAGE = "message";

    /** SSE事件：阶段变化（如"正在规划"->"正在写作"->"正在审核"），前端更新进度状态 */
    public static final String SSE_EVENT_STAGE = "stage";

    /** SSE事件：护栏检查通知（通知前端某条护栏规则的检查结果） */
    public static final String SSE_EVENT_GUARDRAIL = "guardrail";

    /** SSE事件：生成完成，前端收到后关闭SSE连接并展示最终结果 */
    public static final String SSE_EVENT_DONE = "done";

    /** SSE事件：发生错误，前端收到后展示错误提示并关闭连接 */
    public static final String SSE_EVENT_ERROR = "error";

    // ==================== Redis缓存Key模板 ====================
    // 使用 String.format() 填充占位符：%d 是数字，%s 是字符串。
    // 前缀 "sf:" 代表 SocialFlow，用于在 Redis 中区分不同应用的Key。

    /**
     * 用户会话缓存Key模板
     * 格式：sf:session:{用户ID}:{会话ID}
     * 用途：缓存用户的AI对话会话信息
     */
    public static final String CK_USER_SESSION = "sf:session:%d:%s";

    /**
     * 用户每日配额缓存Key模板
     * 格式：sf:quota:daily:{用户ID}:{日期}
     * 用途：记录用户当天已使用的AI调用次数
     */
    public static final String CK_USER_QUOTA_DAILY = "sf:quota:daily:%d:%s";

    /**
     * 用户每月配额缓存Key模板
     * 格式：sf:quota:monthly:{用户ID}:{月份}
     * 用途：记录用户当月已使用的Token数量
     */
    public static final String CK_USER_QUOTA_MONTHLY = "sf:quota:monthly:%d:%s";

    /**
     * 用户级别速率限制Key模板
     * 格式：sf:rl:user:{用户ID}（rl = rate limit）
     * 用途：限制单个用户的请求频率，防止恶意刷接口
     */
    public static final String CK_RATE_LIMIT_USER = "sf:rl:user:%d";

    /**
     * AI调用速率限制Key模板
     * 格式：sf:rl:ai:{用户ID}
     * 用途：限制单个用户调用AI接口的频率，避免短时间内大量消耗AI资源
     */
    public static final String CK_RATE_LIMIT_AI = "sf:rl:ai:%d";

    /**
     * 提示词模板缓存Key模板
     * 格式：sf:pt:{模板ID}（pt = prompt template）
     * 用途：缓存从数据库加载的提示词模板，避免每次都查库
     */
    public static final String CK_PROMPT_TEMPLATE = "sf:pt:%d";

    // ==================== 向量数据库集合名 ====================
    // 向量数据库（如Milvus、Qdrant）用来存储文本的向量表示，
    // 支持"语义相似搜索"——比如搜"好吃的面条"也能找到"美味的拉面"。
    // 不同类型的向量数据存在不同的"集合"（类似关系数据库中的"表"）中。

    /** 知识库文档分块的向量集合——存储知识库中每个文档切片的向量 */
    public static final String VC_KB_CHUNKS = "kb_chunks";

    /** 内容（文案）的向量集合——存储已生成文案的向量，用于相似文案检索 */
    public static final String VC_CONTENT_VECTORS = "content_vectors";

    /** 媒体资源的向量集合——存储图片/视频描述的向量 */
    public static final String VC_MEDIA_VECTORS = "media_vectors";

    /** 记忆向量集合——存储AI对话历史的向量，用于长期记忆检索 */
    public static final String VC_MEMORY_VECTORS = "memory_vectors";

    // ==================== 请求线程本地变量Key ====================
    // ThreadLocal 是Java中的线程局部变量，每个请求在一个线程中处理，
    // 通过 ThreadLocal 可以在请求的整个处理链路中传递用户ID和追踪ID，
    // 不需要在每个方法参数中都传递这些信息。

    /** 线程本地变量Key：当前请求的用户ID */
    public static final String TL_USER_ID = "tl:userId";

    /** 线程本地变量Key：当前请求的追踪ID（用于链路追踪和日志关联） */
    public static final String TL_TRACE_ID = "tl:traceId";

    /** 私有构造方法，禁止实例化此工具类 */
    private CommonConstants() {}
}
