package com.socialflow.service.note.importer;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.socialflow.dao.mapper.NoteImportItemMapper;
import com.socialflow.dao.mapper.NoteMapper;
import com.socialflow.model.entity.Note;
import com.socialflow.model.entity.NoteImportItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 笔记去重三件套
 *   L1：fileHash（SHA-256 of bytes）→ 同一文件再传？
 *   L2：contentHash（SHA-256 of normalized markdown）→ 不同文件名但内容相同？
 *   L3：语义相似度（pgvector），P3 阶段实现
 *
 * 命中后返回冲突的 note id（用于审阅页给用户决定 skip/overwrite/merge）
 */
@Service
@RequiredArgsConstructor
public class NoteDedupService {

    private final NoteImportItemMapper itemMapper;
    private final NoteMapper noteMapper;

    /** 二进制安全：直接 hash 原始字节，不经过 String 中转（new String(bytes) 会丢非 UTF-8 数据） */
    public String fileHash(byte[] bytes) {
        return DigestUtil.sha256Hex(bytes);
    }

    /**
     * 内容归一化后哈希
     *   - 去 BOM、CRLF 统一为 LF
     *   - 多空白合并为单空格
     *   - 全文 trim
     */
    public String contentHash(String md) {
        if (md == null) return SecureUtil.sha256("");
        String norm = md
                .replace("﻿", "")
                .replace("\r\n", "\n")
                .replaceAll("\\s+", " ")
                .trim();
        return SecureUtil.sha256(norm);
    }

    /**
     * L1：在同一用户的历史 import_item 里找相同 fileHash 且已落库的
     * @return 命中的 note.id 或 null
     */
    public Long findExistingByFileHash(Long userId, String fileHash) {
        if (fileHash == null) return null;
        NoteImportItem hit = itemMapper.selectOne(
                new LambdaQueryWrapper<NoteImportItem>()
                        .eq(NoteImportItem::getUserId, userId)
                        .eq(NoteImportItem::getFileHash, fileHash)
                        .isNotNull(NoteImportItem::getFinalNoteId)
                        .orderByDesc(NoteImportItem::getId)
                        .last("LIMIT 1"));
        return hit == null ? null : hit.getFinalNoteId();
    }

    /**
     * L2：用 content_hash 查同用户名下是否已有内容相同的 note
     * 通过 import_item 表反查（note 自身没存 content_hash —— 通过 history item 关联）
     */
    public Long findExistingByContentHash(Long userId, String contentHash) {
        if (contentHash == null) return null;
        NoteImportItem hit = itemMapper.selectOne(
                new LambdaQueryWrapper<NoteImportItem>()
                        .eq(NoteImportItem::getUserId, userId)
                        .eq(NoteImportItem::getContentHash, contentHash)
                        .isNotNull(NoteImportItem::getFinalNoteId)
                        .orderByDesc(NoteImportItem::getId)
                        .last("LIMIT 1"));
        if (hit == null) return null;
        // 校验 note 还存在（没被硬删）
        Note n = noteMapper.selectById(hit.getFinalNoteId());
        return n == null ? null : n.getId();
    }
}
