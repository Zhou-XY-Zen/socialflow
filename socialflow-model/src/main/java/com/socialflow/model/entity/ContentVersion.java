package com.socialflow.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文案版本历史实体类 —— 对应数据库表 `content_version`
 *
 * 【作用】记录文案内容的每一次修改历史。
 *   每当用户编辑文案或 AI 重写文案时，系统会保存当前版本的快照到本表，
 *   实现类似"版本控制"的功能，用户可以查看历史版本或回滚。
 *
 * 【为什么需要它】
 *   文案可能经过多次编辑和改写，用户需要能够：
 *   1. 查看文案的修改历史
 *   2. 对比不同版本之间的差异
 *   3. 在不满意当前版本时回滚到之前的版本
 *
 * 【关联关系】
 *   - content_version.content_id → content.id （所属文案）
 *
 * 【使用场景】
 *   - 文案被编辑保存时，自动创建一条版本记录
 *   - 用户在文案详情页查看"历史版本"列表
 *   - 用户选择某个历史版本进行回滚
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("content_version")
public class ContentVersion extends BaseEntity {

    /**
     * 所属文案 ID
     *
     * 关联 content.id，标识该版本属于哪篇文案。
     */
    private Long contentId;

    /**
     * 版本号
     *
     * 从 1 开始递增。每次保存新版本时自动加 1。
     * 用于按时间顺序排列版本。
     */
    private Integer versionNum;

    /**
     * 文案正文快照
     *
     * 保存该版本时文案的完整正文内容（body 字段的副本）。
     * 即使原文案后续被修改，快照内容也不会变化。
     */
    private String bodySnapshot;

    /**
     * 变更描述
     *
     * 简要说明本次修改的内容或原因。
     * 示例："AI 重写 - 调整为专业语气"、"手动修改标题和开头段落"
     * 可为空。
     */
    private String changeDesc;

    /**
     * 版本写入时的标题快照（V22）。
     * 让历史版本能完整还原 title，不再受当前 content.title 影响。
     */
    private String titleSnapshot;

    /**
     * 版本写入时的标签快照（V22）。
     */
    private String tagsSnapshot;

    /**
     * 版本写入时的状态快照（V22）。
     * 比如某次保存时是 "DRAFT"，发布后是 "PUBLISHED"，回滚时一目了然。
     */
    private String statusSnapshot;

    /**
     * 本次变更涉及的字段名，逗号分隔（V22）。
     * 例：{@code "title,body"} 表示本次同时改了标题和正文。
     */
    private String changedFields;

    /**
     * 变更摘要 JSON（V22）。
     * 可选填，结构示例：
     * <pre>{@code
     * {
     *   "body": {"beforeLen": 1280, "afterLen": 1342, "delta": 62},
     *   "title": {"before": "旧标题", "after": "新标题"}
     * }
     * }</pre>
     */
    private String changeSummary;
}
