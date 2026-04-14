package com.socialflow.service.user.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.socialflow.common.enums.LlmProvider;
import com.socialflow.common.util.AesGcmUtil;
import com.socialflow.dao.mapper.UserApiKeyMapper;
import com.socialflow.model.entity.UserApiKey;
import com.socialflow.service.user.ApiKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ApiKeyService 的默认实现 —— 用户 API 密钥的增删查改与加解密处理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyServiceImpl implements ApiKeyService {

    private final UserApiKeyMapper userApiKeyMapper;

    @Value("${socialflow.ai.encryption-key}")
    private String encryptionKey;

    @Value("${socialflow.ai.default-provider:deepseek}")
    private String defaultProvider;

    @Override
    @Transactional
    public void saveKey(Long userId, LlmProvider provider, String plaintextKey, String baseUrl, boolean isDefault) {
        // 加密明文密钥
        String encrypted = AesGcmUtil.encrypt(plaintextKey, encryptionKey);

        // 查询该用户 + 供应商是否已有记录
        LambdaQueryWrapper<UserApiKey> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserApiKey::getUserId, userId)
               .eq(UserApiKey::getProvider, provider.name());
        UserApiKey existing = userApiKeyMapper.selectOne(wrapper);

        if (existing != null) {
            // 更新已有记录
            existing.setApiKeyEncrypted(encrypted);
            existing.setBaseUrl(baseUrl);
            existing.setIsDefault(isDefault ? 1 : 0);
            userApiKeyMapper.updateById(existing);
        } else {
            // 新增记录
            UserApiKey entity = new UserApiKey();
            entity.setUserId(userId);
            entity.setProvider(provider.name());
            entity.setApiKeyEncrypted(encrypted);
            entity.setBaseUrl(baseUrl);
            entity.setIsDefault(isDefault ? 1 : 0);
            userApiKeyMapper.insert(entity);
        }

        // 如果设为默认，清除其他供应商的默认标记
        if (isDefault) {
            LambdaUpdateWrapper<UserApiKey> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(UserApiKey::getUserId, userId)
                         .ne(UserApiKey::getProvider, provider.name())
                         .set(UserApiKey::getIsDefault, 0);
            userApiKeyMapper.update(null, updateWrapper);
        }

        log.info("API Key 已保存, userId={}, provider={}, isDefault={}", userId, provider, isDefault);
    }

    @Override
    public String getDecryptedKey(Long userId, LlmProvider provider) {
        LambdaQueryWrapper<UserApiKey> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserApiKey::getUserId, userId)
               .eq(UserApiKey::getProvider, provider.name());
        UserApiKey entity = userApiKeyMapper.selectOne(wrapper);
        if (entity == null) {
            return null;
        }
        return AesGcmUtil.decrypt(entity.getApiKeyEncrypted(), encryptionKey);
    }

    @Override
    public List<Map<String, Object>> listMasked(Long userId) {
        LambdaQueryWrapper<UserApiKey> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserApiKey::getUserId, userId);
        List<UserApiKey> keys = userApiKeyMapper.selectList(wrapper);

        return keys.stream().map(key -> {
            Map<String, Object> map = new HashMap<>();
            map.put("provider", key.getProvider());
            map.put("maskedKey", maskKey(key.getApiKeyEncrypted(), key));
            map.put("isDefault", key.getIsDefault() != null && key.getIsDefault() == 1);
            map.put("baseUrl", key.getBaseUrl());
            return map;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(Long userId, LlmProvider provider) {
        LambdaQueryWrapper<UserApiKey> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserApiKey::getUserId, userId)
               .eq(UserApiKey::getProvider, provider.name());
        userApiKeyMapper.delete(wrapper);
        log.info("API Key 已删除, userId={}, provider={}", userId, provider);
    }

    @Override
    public LlmProvider resolveDefaultProvider(Long userId) {
        LambdaQueryWrapper<UserApiKey> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserApiKey::getUserId, userId)
               .eq(UserApiKey::getIsDefault, 1);
        UserApiKey defaultKey = userApiKeyMapper.selectOne(wrapper);
        if (defaultKey != null) {
            return LlmProvider.valueOf(defaultKey.getProvider());
        }
        return LlmProvider.valueOf(defaultProvider.toUpperCase());
    }

    /**
     * 对密钥进行脱敏：解密后只显示最后 4 位，其余用 * 遮盖。
     */
    private String maskKey(String encrypted, UserApiKey key) {
        try {
            String decrypted = AesGcmUtil.decrypt(encrypted, encryptionKey);
            if (decrypted.length() <= 4) {
                return "****";
            }
            return "****" + decrypted.substring(decrypted.length() - 4);
        } catch (Exception e) {
            log.warn("密钥解密失败, provider={}", key.getProvider(), e);
            return "****";
        }
    }
}
