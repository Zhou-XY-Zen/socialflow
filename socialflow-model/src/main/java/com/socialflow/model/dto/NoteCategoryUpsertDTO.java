package com.socialflow.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class NoteCategoryUpsertDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long parentId;

    @NotBlank
    @Size(max = 50)
    private String name;

    private Integer sortOrder;

    private String color;
}
