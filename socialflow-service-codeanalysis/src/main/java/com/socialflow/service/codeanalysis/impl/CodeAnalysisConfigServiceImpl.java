package com.socialflow.service.codeanalysis.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.socialflow.dao.mapper.CodeAnalysisConfigMapper;
import com.socialflow.model.dto.CodeAnalysisConfigDTO;
import com.socialflow.model.entity.CodeAnalysisConfig;
import com.socialflow.service.codeanalysis.CodeAnalysisConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class CodeAnalysisConfigServiceImpl implements CodeAnalysisConfigService {

    private final CodeAnalysisConfigMapper mapper;

    @Override
    public CodeAnalysisConfig findByUserId(Long userId) {
        if (userId == null) return null;
        return mapper.selectOne(new LambdaQueryWrapper<CodeAnalysisConfig>()
                .eq(CodeAnalysisConfig::getUserId, userId)
                .last("LIMIT 1"));
    }

    @Override
    public CodeAnalysisConfig save(Long userId, CodeAnalysisConfigDTO dto) {
        CodeAnalysisConfig existing = findByUserId(userId);
        BigDecimal temp = dto.getTemperature() != null
                ? dto.getTemperature()
                : new BigDecimal("0.30");
        if (existing == null) {
            CodeAnalysisConfig fresh = new CodeAnalysisConfig();
            fresh.setUserId(userId);
            fresh.setProvider(dto.getProvider());
            fresh.setModel(dto.getModel());
            fresh.setTemperature(temp);
            mapper.insert(fresh);
            return fresh;
        }
        existing.setProvider(dto.getProvider());
        existing.setModel(dto.getModel());
        existing.setTemperature(temp);
        mapper.updateById(existing);
        return existing;
    }

    @Override
    public int reset(Long userId) {
        if (userId == null) return 0;
        return mapper.delete(new LambdaQueryWrapper<CodeAnalysisConfig>()
                .eq(CodeAnalysisConfig::getUserId, userId));
    }
}
