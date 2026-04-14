package com.socialflow.service.ai.tools;

import com.socialflow.common.exception.NotFoundException;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册中心——按名称管理所有可用的 {@link SfTool} 工具 Bean。
 *
 * 【什么是工具注册与发现？】
 *
 * 在 Function Calling 场景中，系统需要知道当前有哪些工具可用、
 * 每个工具的名称和能力是什么。ToolRegistry（工具注册中心）就是一个集中管理
 * 所有工具的"登记簿"：
 *     - 注册：所有实现 {@link SfTool} 的 Spring Bean 在启动时自动注册
 *     - 发现：通过 {@link #all()} 获取所有可用工具，构建传给 LLM 的工具列表
 *     - 查找：通过 {@link #get(String)} 按名称查找特定工具并执行
 *
 * 【工作流程】
 *     - 开发者创建新工具类（实现 SfTool 接口，添加 @Component 注解）
 *     - Spring 容器启动时自动将所有 SfTool Bean 注入到本类的构造函数中
 *     - 本类将每个工具按 name 存入 HashMap
 *     - Agent 通过 {@link #all()} 获取所有工具信息传给 LLM
 *     - 当 LLM 决定调用某个工具时，Agent 通过 {@link #get(String)} 查找并执行
 */
@Component
public class ToolRegistry {

    /** 工具名称 → 工具实例的映射表，用于 O(1) 查找 */
    private final Map<String, SfTool> tools = new HashMap<>();

    /**
     * 构造函数：Spring 自动注入所有 {@link SfTool} 实现 Bean。
     *
     * 遍历所有工具 Bean，按各自的 {@link SfTool#name()} 注册到映射表中。
     *
     * @param toolBeans Spring 容器中所有 SfTool 实现的列表
     */
    public ToolRegistry(List<SfTool> toolBeans) {
        for (SfTool t : toolBeans) tools.put(t.name(), t);
    }

    /**
     * 根据名称查找工具。
     *
     * 当 LLM 发起 Function Calling 并指定了工具名称时，调用此方法获取工具实例。
     *
     * @param name 工具名称（如 {@code knowledge_search}）
     * @return 工具实例
     * @throws NotFoundException 如果指定名称的工具未注册
     */
    public SfTool get(String name) {
        SfTool t = tools.get(name);
        if (t == null) throw new NotFoundException("tool not found: " + name);
        return t;
    }

    /**
     * 获取所有已注册工具的不可变视图。
     *
     * 通常在构建传给 LLM 的工具列表时使用，将每个工具的 name、description
     * 和 parametersSchema 序列化为 API 要求的格式。
     * 返回不可变 Map 以防止外部意外修改注册表。
     *
     * @return 工具名称 → 工具实例的不可变映射表
     */
    public Map<String, SfTool> all() {
        return Collections.unmodifiableMap(tools);
    }
}
