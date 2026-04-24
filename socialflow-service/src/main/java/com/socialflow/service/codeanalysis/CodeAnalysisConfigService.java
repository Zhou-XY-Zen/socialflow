package com.socialflow.service.codeanalysis;

import com.socialflow.model.dto.CodeAnalysisConfigDTO;
import com.socialflow.model.entity.CodeAnalysisConfig;

/**
 * 代码分析用户级 LLM 配置 Service。
 *
 * 设计原则：
 *   - 未配置时返回 null，上层（CodeAnalysisAsyncRunner）自行回退到 yml 默认值
 *   - upsert 语义：同一 userId 再次 save 则更新（unique(user_id) 约束）
 *   - reset 即删除记录，效果等同"恢复系统默认"
 */
public interface CodeAnalysisConfigService {

    /** 查用户配置；不存在返回 null（让调用方明确处理回退逻辑，避免意外覆盖）*/
    CodeAnalysisConfig findByUserId(Long userId);

    /** upsert：存在则更新，不存在则新建。返回保存后的记录 */
    CodeAnalysisConfig save(Long userId, CodeAnalysisConfigDTO dto);

    /** 删除该用户的配置，返回删除的行数（0 表示原本就没有）*/
    int reset(Long userId);
}
