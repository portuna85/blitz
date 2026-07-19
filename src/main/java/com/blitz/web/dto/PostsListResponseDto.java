package com.blitz.web.dto;

import com.blitz.domain.posts.Posts;

import java.time.LocalDateTime;

public record PostsListResponseDto(Long id, String title, String author, LocalDateTime modifiedDate) {

    public PostsListResponseDto(Posts entity) {
        this(entity.getId(), entity.getTitle(), entity.getAuthor(), entity.getModifiedDate());
    }
}
