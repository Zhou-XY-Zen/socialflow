package com.socialflow.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记骨架代码 —— 接口已定义、入口已暴露，但内部实现仍为占位 / 兜底逻辑。
 *
 * <p>用法：当一个方法 / 类应当实现某个完整能力（如"长期 Memory 检索"），但当前
 * 仅返回兜底值（如空列表 / 默认提示词）时，加这个注解明确告知调用方"别期待真实行为"。
 * 区别于 {@link Experimental}：</p>
 * <ul>
 *   <li>{@code @Experimental}：API 可用但可能调整 —— 实现已经落地</li>
 *   <li>{@code @NotImplementedYet}：API 暂时不可用 —— 实现是骨架，调用结果不可信</li>
 * </ul>
 *
 * <p>这种标注比 {@code throw new UnsupportedOperationException} 更友好 ——
 * 系统会以兜底行为继续运行，但开发者读代码时立刻知道"这里需要补完"。</p>
 *
 * <p>跟踪建议：</p>
 * <ul>
 *   <li>同时在 README 的 Feature Map 中标记"interface only"</li>
 *   <li>建一个 GitHub Issue / Linear ticket，把 ticket 链接放在 {@link #tracking()}</li>
 * </ul>
 */
@Target({
        ElementType.TYPE,
        ElementType.METHOD,
        ElementType.CONSTRUCTOR,
        ElementType.FIELD
})
@Retention(RetentionPolicy.RUNTIME)
public @interface NotImplementedYet {

    /**
     * 简短说明当前的兜底行为，例如 "返回空列表" / "始终返回 PASS"。
     */
    String value() default "";

    /**
     * 跟踪 ticket 编号或 URL，便于读代码的人跳转上下文。
     */
    String tracking() default "";

    /**
     * 计划在哪个 Wave / 版本完成实现。
     */
    String plannedFor() default "";
}
