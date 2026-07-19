package com.blitz.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostsUpdateRequestDto(

        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 500, message = "제목은 500자를 넘을 수 없습니다.")
        String title,

        @NotBlank(message = "내용은 필수입니다.")
        @Size(max = 10000, message = "내용은 10000자를 넘을 수 없습니다.")
        String content) {
}
