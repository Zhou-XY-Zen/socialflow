package com.socialflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.socialflow.model.entity.RepoAuthCredential;
import org.apache.ibatis.annotations.Mapper;

/**
 * Git 仓库访问凭证 Mapper。
 */
@Mapper
public interface RepoAuthCredentialMapper extends BaseMapper<RepoAuthCredential> {
}
