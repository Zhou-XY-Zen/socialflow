package com.socialflow.common.enums;

/**
 * 内容（文案）状态枚举
 *
 * 【作用】定义一篇文案从创建到发布的完整生命周期状态。
 *   数据库中存储的是枚举名称字符串（如 "DRAFT"），前端根据状态展示不同的UI。
 *
 * 【状态流转】文案的状态按以下流程变化：
 *   DRAFT（草稿）→ SCHEDULED（已排期）→ PUBLISHING（发布中）→ PUBLISHED（已发布）
 *                                                              ↘ FAILED（发布失败）
 *
 *   - 用户创建文案后，默认是 DRAFT 状态
 *   - 用户设置发布时间后，变成 SCHEDULED
 *   - 到达发布时间，系统开始发布，变成 PUBLISHING
 *   - 发布成功变成 PUBLISHED，发布失败变成 FAILED
 *   - FAILED 状态的文案可以重新发布
 */
public enum ContentStatus {

    /** 草稿——文案刚创建或还在编辑中，未安排发布 */
    DRAFT,

    /** 已排期——文案已设置发布时间，等待定时任务触发发布 */
    SCHEDULED,

    /** 发布中——系统正在调用社交平台API进行发布，这是一个中间状态 */
    PUBLISHING,

    /** 已发布——文案已成功发布到目标社交平台 */
    PUBLISHED,

    /** 发布失败——发布过程中出现错误（如平台API报错、网络超时等） */
    FAILED
}
