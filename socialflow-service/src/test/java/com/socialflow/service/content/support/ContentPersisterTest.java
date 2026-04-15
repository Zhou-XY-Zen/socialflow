package com.socialflow.service.content.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.socialflow.dao.mapper.ContentMapper;
import com.socialflow.dao.mapper.ContentVersionMapper;
import com.socialflow.model.entity.Content;
import com.socialflow.model.entity.ContentVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ContentPersister} 的单元测试 —— 验证 Wave 1.1 抽出的持久化 Helper
 * 在 insert/update/delete 三个事务方法中是否正确地：
 * <ol>
 *     <li>调用对应的 mapper 操作</li>
 *     <li>每次写入都追加一条 ContentVersion 快照（递增 version_num）</li>
 *     <li>把传入的 changeDesc 透传到 ContentVersion 实体</li>
 * </ol>
 *
 * <p>测试不依赖 Spring 容器，纯 Mockito + JUnit 5，运行毫秒级。</p>
 */
@ExtendWith(MockitoExtension.class)
class ContentPersisterTest {

    @Mock
    private ContentMapper contentMapper;

    @Mock
    private ContentVersionMapper contentVersionMapper;

    @InjectMocks
    private ContentPersister persister;

    private Content sampleContent;

    @BeforeEach
    void setup() {
        sampleContent = new Content();
        sampleContent.setId(100L);
        sampleContent.setUserId(42L);
        sampleContent.setBody("hello world");
        sampleContent.setPlatform("XIAOHONGSHU");
        sampleContent.setStatus("DRAFT");
    }

    @Test
    @DisplayName("insertWithVersion 同时写入 content + 第一条版本快照")
    void insertWithVersion_writesContentAndInitialVersion() {
        // 模拟当前没有任何版本（新建场景）
        when(contentVersionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        Content result = persister.insertWithVersion(sampleContent, "AI_GENERATE - test");

        // 1. content 主表写入
        verify(contentMapper, times(1)).insert(sampleContent);

        // 2. 版本快照写入，且版本号是 1（0+1）
        ArgumentCaptor<ContentVersion> versionCaptor = ArgumentCaptor.forClass(ContentVersion.class);
        verify(contentVersionMapper, times(1)).insert(versionCaptor.capture());
        ContentVersion saved = versionCaptor.getValue();
        assertThat(saved.getContentId()).isEqualTo(100L);
        assertThat(saved.getVersionNum()).isEqualTo(1);
        assertThat(saved.getBodySnapshot()).isEqualTo("hello world");
        assertThat(saved.getChangeDesc()).isEqualTo("AI_GENERATE - test");

        // 3. 返回值是入参实体本身（带 ID）
        assertThat(result).isSameAs(sampleContent);
    }

    @Test
    @DisplayName("updateWithVersion 在已有 N 条版本时追加为 N+1")
    void updateWithVersion_appendsIncrementalVersion() {
        // 模拟已有 3 条历史版本
        when(contentVersionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);

        sampleContent.setBody("updated body");
        persister.updateWithVersion(sampleContent, "MANUAL_EDIT - 手动编辑");

        verify(contentMapper, times(1)).updateById(sampleContent);

        ArgumentCaptor<ContentVersion> versionCaptor = ArgumentCaptor.forClass(ContentVersion.class);
        verify(contentVersionMapper, times(1)).insert(versionCaptor.capture());
        ContentVersion saved = versionCaptor.getValue();
        assertThat(saved.getVersionNum()).isEqualTo(4);
        assertThat(saved.getBodySnapshot()).isEqualTo("updated body");
        assertThat(saved.getChangeDesc()).isEqualTo("MANUAL_EDIT - 手动编辑");
    }

    @Test
    @DisplayName("softDelete 委托给 ContentMapper.deleteById（依赖 @TableLogic 转 UPDATE）")
    void softDelete_callsDeleteById() {
        persister.softDelete(100L);

        verify(contentMapper, times(1)).deleteById(100L);
        // 删除不写版本快照
        verify(contentVersionMapper, times(0)).insert(any(ContentVersion.class));
    }
}
