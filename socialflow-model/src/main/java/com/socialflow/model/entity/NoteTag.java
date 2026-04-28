package com.socialflow.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 笔记标签 —— 按用户隔离 + 名字唯一
 *
 * usage_count 是冗余字段，只在 commit/delete 时增量更新；
 * 如果数据漂移用 cron 任务定期回填即可。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("note_tag")
public class NoteTag extends BaseEntity {

    private Long userId;

    private String name;

    private Integer usageCount;
}
