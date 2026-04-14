package com.socialflow.common.exception;

import com.socialflow.common.result.ResultCode;

/**
 * 配额超限异常
 *
 * 【作用】当用户的AI调用次数/Token用量超出系统设定的配额限制时，抛出此异常。
 *   错误码固定为 2003（QUOTA_EXCEEDED）。
 *
 * 【配额机制说明】
 *   系统为每个用户设置了AI调用的使用配额，包括：
 *   - 每日配额（daily quota）：每天最多调用多少次AI
 *   - 每月配额（monthly quota）：每月最多使用多少Token
 *   当配额用完后，用户需要等到下个周期或联系管理员提升配额。
 *
 * 【使用场景举例】
 *   throw new QuotaExceededException("您今日的AI调用次数已用完，请明天再试");
 *   throw new QuotaExceededException("本月Token用量已达上限");
 *
 * 【前端处理建议】前端收到此异常后，可以提示用户配额已用完，
 *   并展示剩余配额信息或引导用户升级套餐。
 */
public class QuotaExceededException extends BaseException {

    /**
     * 构造配额超限异常
     * 错误码自动设为 2003（QUOTA_EXCEEDED）
     *
     * @param message 配额超限的具体描述，如"您今日的AI调用次数已用完"
     */
    public QuotaExceededException(String message) {
        super(ResultCode.QUOTA_EXCEEDED, message);
    }
}
