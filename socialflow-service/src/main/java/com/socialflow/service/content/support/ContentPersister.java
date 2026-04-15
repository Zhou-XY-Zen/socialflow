package com.socialflow.service.content.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.socialflow.dao.mapper.ContentMapper;
import com.socialflow.dao.mapper.ContentVersionMapper;
import com.socialflow.model.entity.Content;
import com.socialflow.model.entity.ContentVersion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 内容持久化 Helper —— 将 content + content_version 多表写操作收敛到独立事务。
 *
 * <p>背景：ContentServiceImpl 的 generate/rewrite 方法在进入持久化前有耗时的 LLM 调用
 * （5-30s）。若把 @Transactional 直接加到这些方法上，事务会从方法开始就获取 DB 连接，
 * 整个 LLM 调用期间一直占用，在高并发下迅速耗尽连接池。</p>
 *
 * <p>解法：把真正的 DB 写操作下沉到本 Helper，事务边界仅覆盖 DB 操作本身。
 * LLM 调用在调用方（ContentServiceImpl）完成，不占用数据库事务。</p>
 *
 * <p>向量化（embedding + pgvector upsert）属于外部副作用，不在本组件内执行，
 * 由调用方在事务提交后（本方法返回后）自行调用，失败不回滚业务数据。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentPersister {

    private final ContentMapper contentMapper;
    private final ContentVersionMapper contentVersionMapper;

    /**
     * 插入新 Content 记录 + 初始版本快照（原子事务）。
     *
     * @param entity      已填充字段的 Content 实体（调用方组装好）
     * @param versionDesc 版本变更描述，例如 "AI_GENERATE - AI 首次生成"
     * @return 带上 DB 生成 ID 的 Content 实体
     */
    @Transactional(rollbackFor = Exception.class)
    public Content insertWithVersion(Content entity, String versionDesc) {
        contentMapper.insert(entity);
        saveVersion(entity.getId(), entity.getBody(), versionDesc);
        return entity;
    }

    /**
     * 更新 Content 记录 + 追加一条版本快照（原子事务）。
     *
     * @param entity      已更新字段的 Content 实体
     * @param versionDesc 版本变更描述，例如 "MANUAL_EDIT - 手动编辑"
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateWithVersion(Content entity, String versionDesc) {
        contentMapper.updateById(entity);
        saveVersion(entity.getId(), entity.getBody(), versionDesc);
    }

    /**
     * 软删除 Content（依赖实体的 @TableLogic 字段）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void softDelete(Long contentId) {
        contentMapper.deleteById(contentId);
    }

    /**
     * 保存版本快照记录。版本号按 content_id 维度自增。
     */
    private void saveVersion(Long contentId, String body, String changeDesc) {
        Long maxVersion = contentVersionMapper.selectCount(
                new LambdaQueryWrapper<ContentVersion>()
                        .eq(ContentVersion::getContentId, contentId)
        );

        ContentVersion version = new ContentVersion();
        version.setContentId(contentId);
        version.setVersionNum(maxVersion.intValue() + 1);
        version.setBodySnapshot(body);
        version.setChangeDesc(changeDesc);
        contentVersionMapper.insert(version);
    }
}
