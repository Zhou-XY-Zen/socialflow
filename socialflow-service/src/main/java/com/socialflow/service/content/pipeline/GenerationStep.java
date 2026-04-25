package com.socialflow.service.content.pipeline;

/**
 * 内容生成 pipeline 的单个步骤接口。
 *
 * <p>每一步从 {@link GenerationContext} 读取它需要的输入字段，运行业务逻辑，
 * 然后把结果写回 context。Pipeline 按 {@link #order()} 升序依次执行。</p>
 *
 * <p>对比之前的硬编码 5 步：</p>
 * <ul>
 *   <li>新加 step（如"运行平台限流前置检查"）只要 implements + @Component，自动加入链</li>
 *   <li>step 顺序由 order() 决定，而不是源码行号</li>
 *   <li>替换某个 step（如换一种 RAG 实现）只改一个文件，不动 ContentServiceImpl</li>
 * </ul>
 */
public interface GenerationStep {

    /** 用作日志前缀，便于跟踪每一步耗时 / 错误 */
    String name();

    /**
     * 执行该步骤。
     * 抛出异常时整条 pipeline 立即中止 —— 业务异常（GuardrailException 等）由
     * 上层 GlobalExceptionHandler 捕获并按既定错误码返回。
     */
    void apply(GenerationContext ctx);

    /**
     * 执行顺序，数值小的先执行。建议 10 一档，留出插入空间：
     * <ul>
     *   <li>10 — 输入护栏</li>
     *   <li>20 — RAG 检索</li>
     *   <li>30 — 提示词渲染</li>
     *   <li>40 — LLM 调用</li>
     *   <li>50 — 输出护栏</li>
     *   <li>60 — 持久化</li>
     * </ul>
     */
    int order();
}
