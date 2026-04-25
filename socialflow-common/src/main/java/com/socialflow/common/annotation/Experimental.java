package com.socialflow.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记处于试验阶段、API 可能在后续版本变化的类型 / 方法 / 字段。
 *
 * <p>不同于 {@code @Deprecated}：</p>
 * <ul>
 *   <li>{@code @Deprecated}：即将被移除，调用方应迁移到替代实现</li>
 *   <li>{@code @Experimental}：当前可用但未稳定，跨版本可能不兼容修改 ——
 *       自用项目代码请谨慎依赖，第三方调用方完全不应依赖</li>
 * </ul>
 *
 * <p>典型使用场景：</p>
 * <ul>
 *   <li>Wave 4.x 抢先发的能力（评测、Multi-Agent、长期 Memory），等用户反馈后再稳定 API</li>
 *   <li>骨架代码已落地、但实现仍在迭代的接口</li>
 *   <li>仅供 demo 暂未投生产的入口</li>
 * </ul>
 *
 * <p>编译器不会因为这个注解给出警告 —— 如果希望产生 warning，配合 {@code @SuppressWarnings}
 * 检查或自定义 IDE 检查器使用。它是给读代码的人看的标记，不是强制约束。</p>
 */
@Target({
        ElementType.TYPE,
        ElementType.METHOD,
        ElementType.CONSTRUCTOR,
        ElementType.FIELD,
        ElementType.PACKAGE
})
@Retention(RetentionPolicy.RUNTIME)
public @interface Experimental {

    /**
     * 标记起始版本。便于追溯"这个能力是从哪个版本开始试验的"。
     * 默认空字符串表示未指定。
     */
    String since() default "";

    /**
     * 简短说明：该试验性 API 的边界（哪些行为已稳定 / 哪些可能变）和是否有迁移计划。
     */
    String value() default "";
}
