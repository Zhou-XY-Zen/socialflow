package com.socialflow.common.result;

import lombok.Getter;

/**
 * 业务结果码枚举（全局错误码字典）
 *
 * 【作用】统一定义系统中所有可能出现的错误码和对应的提示信息。
 *   每个错误码由一个数字和一段描述文字组成，前端/调用方拿到错误码后
 *   就能明确知道出了什么问题。
 *
 * 【编码规则】按照数字段分组，便于快速定位问题所属模块：
 *   - 1xx~5xx：HTTP标准状态码（200成功、400参数错误、401未登录等）
 *   - 1xxx：通用业务错误（参数非法、数据不存在、数据重复等）
 *   - 2xxx：AI相关错误（调用失败、护栏拦截、配额超限等）
 *   - 3xxx：知识库相关错误（知识库不存在、文档解析失败等）
 *   - 4xxx：内容管理相关错误（内容不存在、发布失败等）
 *
 * 【使用场景】
 *   - 在 Controller 中：return R.fail(ResultCode.AI_CALL_FAILED);
 *   - 在异常中：throw new BaseException(ResultCode.NOT_FOUND);
 */
@Getter // Lombok注解：自动为 code 和 message 字段生成 getter 方法
public enum ResultCode {

    // ==================== HTTP 标准状态码 ====================

    /** 成功 */
    SUCCESS(200, "ok"),

    /** 请求参数不合法（如缺少必填字段、格式错误等） */
    BAD_REQUEST(400, "invalid request"),

    /** 未登录或登录已过期，需要重新登录 */
    UNAUTHORIZED(401, "not logged in"),

    /** 已登录但没有权限执行此操作 */
    FORBIDDEN(403, "forbidden"),

    /** 请求的资源不存在 */
    NOT_FOUND(404, "resource not found"),

    /** 服务器内部错误（未预料到的异常） */
    SERVER_ERROR(500, "internal server error"),

    // ==================== 通用业务错误（1xxx） ====================

    /** 参数校验失败（如手机号格式错误、长度超限等） */
    PARAM_INVALID(1000, "parameter invalid"),

    /** 查询的数据不存在（如根据ID查不到记录） */
    DATA_NOT_FOUND(1001, "data not found"),

    /** 数据已存在（如用户名重复、文案标题重复等） */
    DATA_DUPLICATED(1002, "data already exists"),

    // ==================== AI相关错误（2xxx） ====================

    /** 调用大模型（LLM）接口失败（如网络超时、模型返回异常等） */
    AI_CALL_FAILED(2001, "AI call failed"),

    /** 护栏（guardrail）拦截了请求（如输入包含敏感词、输出不符合平台规范等） */
    GUARDRAIL_BLOCKED(2002, "guardrail blocked the request"),

    /** 用户的AI调用配额已用完（按日/月限额控制） */
    QUOTA_EXCEEDED(2003, "quota exceeded"),

    /** API密钥无效或未配置（如OpenAI的Key过期了） */
    API_KEY_INVALID(2004, "API key invalid or missing"),

    /** 指定的模型不被支持（如系统不支持GPT-5） */
    MODEL_NOT_SUPPORTED(2005, "model not supported"),

    /** 提示词模板渲染失败（如模板中的占位符无法替换） */
    PROMPT_RENDER_FAILED(2006, "prompt rendering failed"),

    /** Agent多轮对话达到最大轮数限制，强制终止 */
    AGENT_MAX_ROUNDS_REACHED(2007, "agent max rounds reached"),

    // ==================== 知识库相关错误（3xxx） ====================

    /** 知识库不存在（如用户引用了已被删除的知识库） */
    KB_NOT_FOUND(3001, "knowledge base not found"),

    /** 知识库文档解析失败（如上传的PDF损坏、格式不支持等） */
    KB_DOC_PARSE_FAILED(3002, "document parse failed"),

    /** 向量数据库操作异常（如Milvus/Qdrant连接失败） */
    VECTOR_STORE_ERROR(3003, "vector store error"),

    /** 文本向量化（Embedding）失败（如Embedding模型调用出错） */
    EMBEDDING_FAILED(3004, "embedding failed"),

    // ==================== 内容管理相关错误（4xxx） ====================

    /** 内容（文案）不存在 */
    CONTENT_NOT_FOUND(4001, "content not found"),

    /** 发布内容到社交平台失败（如平台API报错） */
    PUBLISH_FAILED(4002, "publish failed"),

    /** 提示词模板不存在 */
    TEMPLATE_NOT_FOUND(4003, "template not found");

    /** 错误码数字，前端根据这个数字做不同的处理逻辑 */
    private final Integer code;

    /** 错误描述信息，可直接展示给用户或用于日志记录 */
    private final String message;

    /**
     * 枚举构造方法（枚举的构造方法默认是 private 的）
     *
     * @param code    错误码
     * @param message 错误描述
     */
    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
