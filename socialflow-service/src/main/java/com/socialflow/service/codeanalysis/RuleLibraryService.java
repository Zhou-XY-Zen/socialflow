package com.socialflow.service.codeanalysis;

import com.socialflow.model.entity.RuleLibraryItem;
import com.socialflow.model.vo.RuleLibraryItemVO;

import java.util.List;

/**
 * 规约库 Service —— Wave 7。
 *
 * 提供：
 *   - 列表查询（按 topCategory / level / keyword 过滤）
 *   - 启停某条规约
 *   - 用户自定义规约 CRUD
 *   - 给 RuleLibraryHolder 提供启用规约的内存索引刷新
 */
public interface RuleLibraryService {

    /** 列出规约（条件可空表示不过滤） */
    List<RuleLibraryItemVO> list(String topCategory, String level, String keyword, Boolean enabledOnly);

    /** 启停某条规约 */
    void toggleEnabled(Long id, Integer enabled);

    /** 保存（自定义新增 / 编辑现有） */
    RuleLibraryItemVO save(RuleLibraryItem entity);

    /** 删除自定义规约（只能删 is_custom=1 的，黄山版自带的禁删） */
    void deleteCustom(Long id);

    /** 启动 / 升级时批量 upsert（保留用户对 enabled 的修改） */
    int upsertFromJsonResource(String classpathJson);

    /** 给 Holder 用：取出全部启用的规约用于 Prompt 拼装 */
    List<RuleLibraryItem> listAllEnabled();
}
