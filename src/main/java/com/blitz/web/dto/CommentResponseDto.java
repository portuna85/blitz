package com.blitz.web.dto;

import com.blitz.domain.comments.Comments;

import java.time.LocalDateTime;

public record CommentResponseDto(
        Long id,
        Long parentId,
        String content,
        String author,
        Long version,
        boolean deleted,
        boolean owner,
        LocalDateTime createdDate,
        LocalDateTime modifiedDate) {

    public static CommentResponseDto of(Comments entity, Long currentUserId) {
        if (entity.isDeleted()) {
            return new CommentResponseDto(
                    entity.getId(),
                    entity.getParentId(),
                    null,
                    null,
                    entity.getVersion(),
                    true,
                    false,
                    entity.getCreatedDate(),
                    entity.getModifiedDate());
        }

        return new CommentResponseDto(
                entity.getId(),
                entity.getParentId(),
                entity.getContent(),
                entity.getAuthor(),
                entity.getVersion(),
                false,
                entity.isOwnedBy(currentUserId),
                entity.getCreatedDate(),
                entity.getModifiedDate());
    }
}
