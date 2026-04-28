package com.socialflow.model.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class NoteUrlClipDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 一行一条 URL，支持批量 */
    @NotEmpty
    private List<String> urls;

    /** 是否走 AI 富化（默认 true） */
    private Boolean enrichEnabled;
}
