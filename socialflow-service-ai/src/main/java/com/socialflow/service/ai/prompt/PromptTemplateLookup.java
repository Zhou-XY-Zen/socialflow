package com.socialflow.service.ai.prompt;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.socialflow.dao.mapper.PromptTemplateMapper;
import com.socialflow.model.entity.PromptTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * 提示词模板查询的缓存层 —— 把 DB 访问从 {@link PromptServiceImpl} 抽到独立 Bean，
 * 让 Spring AOP 能正确代理 {@code @Cacheable} 注解。
 *
 * <p>为什么不直接给 PromptServiceImpl 的私有方法加 @Cacheable？
 * Spring AOP 基于代理工作，类内部 self-call（{@code this.method()}）不经过代理，
 * 注解会被绕过。把方法拆到独立 Bean 后，调用方持有的是代理对象，缓存才会生效。</p>
 *
 * <p>缓存策略：</p>
 * <ul>
 *   <li>cache name = {@code promptTemplate}（TTL 10 分钟，配置在 CacheConfig）</li>
 *   <li>key = {@code byId:{id}} 或 {@code default:{platform}}，避免两类查询互相串号</li>
 *   <li>{@code findById} / {@code findDefaultByPlatform} 任一返回 null 时不缓存（默认配置）</li>
 *   <li>模板增删改时调用 {@link #invalidateAll()} 清空整个 cache —— 改动频次低，不必精细 evict</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class PromptTemplateLookup {

    private final PromptTemplateMapper promptTemplateMapper;

    /**
     * 按 ID 查模板。命中缓存 → 直接返回；未命中 → 走 DB 后入缓存。
     * 不存在的模板返回 null（不写缓存）。
     */
    @Cacheable(value = "promptTemplate", key = "'byId:' + #templateId")
    public PromptTemplate findById(Long templateId) {
        if (templateId == null) return null;
        return promptTemplateMapper.selectById(templateId);
    }

    /**
     * 按平台查询默认系统模板（is_system=1，按 sort_order 升序取第一条）。
     */
    @Cacheable(value = "promptTemplate", key = "'default:' + #platform")
    public PromptTemplate findDefaultByPlatform(String platform) {
        if (platform == null || platform.isBlank()) return null;
        return promptTemplateMapper.selectOne(
                new LambdaQueryWrapper<PromptTemplate>()
                        .eq(PromptTemplate::getPlatform, platform)
                        .eq(PromptTemplate::getIsSystem, 1)
                        .orderByAsc(PromptTemplate::getSortOrder)
                        .last("LIMIT 1"));
    }

    /**
     * 模板增删改时调用 —— 把整个 promptTemplate cache 清掉。
     *
     * <p>选择全清而不是按 key 精细 evict 的原因：</p>
     * <ul>
     *   <li>修改某个 templateId 时只需 evict {@code byId:{id}}，但…</li>
     *   <li>修改默认模板的 sort_order 会影响 {@code default:{platform}} 的命中结果</li>
     *   <li>更新 is_system=0→1 也可能改变默认模板</li>
     * </ul>
     * <p>模板表写操作极少（管理员才操作），全清成本可接受。</p>
     */
    @CacheEvict(value = "promptTemplate", allEntries = true)
    public void invalidateAll() {
        // 仅作为切面入口，方法体留空
    }
}
