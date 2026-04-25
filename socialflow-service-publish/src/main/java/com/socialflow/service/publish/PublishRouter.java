package com.socialflow.service.publish;

import com.socialflow.common.enums.PlatformType;
import com.socialflow.common.exception.BusinessException;
import com.socialflow.common.result.ResultCode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 发布路由器 —— 根据目标平台类型选择对应的 {@link Publisher} 实现。
 *
 * 这是策略模式中的"上下文（Context）"角色。它在启动时自动收集所有
 * {@link Publisher} 实现类，并建立"平台类型 -> 发布者实例"的映射关系。
 * 调用方只需传入平台类型，路由器就会返回正确的发布者实现。
 *
 * 工作原理：
 *     - Spring 容器启动时，自动发现所有实现了 {@link Publisher} 接口的 Bean
 *     - 通过构造函数注入，Spring 会把这些 Bean 以 List 形式传入
 *     - 构造函数遍历列表，调用每个 Publisher 的 platform() 方法获取其负责的平台
 *     - 以 平台类型 为 key、Publisher 实例为 value，存入内部的 HashMap
 *
 * 被 {@code PublishController} 和 {@code ContentServiceImpl} 等需要发布功能的组件调用。
 *
 * @see Publisher 发布者接口（被路由的策略对象）
 */
@Component  // Spring 注解：将本类注册为 Spring Bean，可被其他类自动注入
public class PublishRouter {

    /**
     * 平台类型到发布者实例的映射表。
     * 例如：{WECHAT -> WeChatPublisher, WEIBO -> WeiboPublisher, ...}
     */
    private final Map<PlatformType, Publisher> publishers = new HashMap<>();

    /**
     * 构造函数 —— Spring 会自动注入所有 Publisher 接口的实现类。
     *
     * Spring 的依赖注入特性：当构造函数参数是一个接口的 List 类型时，
     * Spring 会自动找到容器中所有实现了该接口的 Bean，组成列表传入。
     * 这样新增平台实现类时，无需修改这里的代码。
     *
     * @param publisherBeans Spring 容器中所有 Publisher 实现类的 Bean 列表
     */
    public PublishRouter(List<Publisher> publisherBeans) {
        // 遍历所有发布者 Bean，按平台类型存入映射表
        for (Publisher p : publisherBeans) publishers.put(p.platform(), p);
    }

    /**
     * 根据平台类型获取对应的发布者实例。
     *
     * 如果指定的平台没有对应的发布者实现，会抛出业务异常。
     * 这通常意味着该平台的 Publisher 实现类尚未开发。
     *
     * @param platform 目标平台类型枚举
     * @return 对应平台的 Publisher 实例
     * @throws BusinessException 如果找不到该平台的发布者实现
     */
    public Publisher get(PlatformType platform) {
        // 从映射表中查找对应平台的发布者
        Publisher p = publishers.get(platform);
        // 如果找不到，抛出业务异常，告知调用方该平台暂不支持
        if (p == null) {
            throw new BusinessException(ResultCode.PUBLISH_FAILED,
                    "no publisher for " + platform);
        }
        return p;
    }
}
