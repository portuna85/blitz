package com.blitz.web.dto;

import com.blitz.domain.posts.Posts;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostsSaveRequestDto(

        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 500, message = "제목은 500자를 넘을 수 없습니다.")
        String title,

        @NotBlank(message = "내용은 필수입니다.")
        @Size(max = 10000, message = "내용은 10000자를 넘을 수 없습니다.")
        String content) {

    public Posts toEntity(String author, String authorEmail) {
        return Posts.builder()
                .title(title)
                .content(content)
                .author(author)
                .authorEmail(authorEmail)
                .build();
    }
}
