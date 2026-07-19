package com.blitz.web.dto;

import com.blitz.domain.posts.PostsSummary;

import java.time.LocalDateTime;

public record PostsListResponseDto(Long id, String title, String author, LocalDateTime modifiedDate) {

    public PostsListResponseDto(PostsSummary summary) {
        this(summary.getId(), summary.getTitle(), summary.getAuthor(), summary.getModifiedDate());
    }
}
