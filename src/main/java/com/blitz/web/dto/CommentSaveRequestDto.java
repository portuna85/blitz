package com.blitz.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentSaveRequestDto(

        @NotBlank(message = "내용은 필수입니다.")
        @Size(max = 1000, message = "내용은 1000자를 넘을 수 없습니다.")
        String content,

        Long parentId) {
}
