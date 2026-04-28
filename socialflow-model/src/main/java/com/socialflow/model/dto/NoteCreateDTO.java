package com.socialflow.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class NoteCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank
    @Size(max = 200)
    private String title;

    @NotBlank
    private String contentMd;

    private String summary;

    private Long categoryId;

    /** 标签名数组；后端按 user_id+name 自动 upsert 到 note_tag */
    private List<String> tags;

    private Integer isPinned;

    private Integer isPublic;

    /** 草稿态保存时传 2，否则默认 1 */
    private Integer status;
}
