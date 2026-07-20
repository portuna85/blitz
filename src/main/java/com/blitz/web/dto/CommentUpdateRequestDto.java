package com.blitz.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CommentUpdateRequestDto(

        @NotBlank(message = "내용은 필수입니다.")
        @Size(max = 1000, message = "내용은 1000자를 넘을 수 없습니다.")
        String content,

        @NotNull(message = "댓글 버전은 필수입니다.")
        @PositiveOrZero(message = "댓글 버전은 0 이상이어야 합니다.")
        Long version) {
}
