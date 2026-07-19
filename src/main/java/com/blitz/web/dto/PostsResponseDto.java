package com.blitz.web.dto;

import com.blitz.domain.posts.Posts;

import java.time.LocalDateTime;

public record PostsResponseDto(
        Long id,
        String title,
        String content,
        String author,
        Long version,
        LocalDateTime createdDate,
        LocalDateTime modifiedDate) {

    public PostsResponseDto(Posts entity) {
        this(
                entity.getId(),
                entity.getTitle(),
                entity.getContent(),
                entity.getAuthor(),
                entity.getVersion(),
                entity.getCreatedDate(),
                entity.getModifiedDate());
    }
}
