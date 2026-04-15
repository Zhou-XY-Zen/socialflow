package com.socialflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.socialflow.model.entity.ImageAssetCache;
import org.apache.ibatis.annotations.Mapper;

/**
 * 图像生成去重缓存 Mapper（Wave 4.2）。
 *
 * <p>查询用 LambdaQueryWrapper 按 (user_id, prompt_hash) 唯一索引精确命中。</p>
 */
@Mapper
public interface ImageAssetCacheMapper extends BaseMapper<ImageAssetCache> {
}
