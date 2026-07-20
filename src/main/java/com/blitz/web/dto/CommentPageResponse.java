package com.blitz.web.dto;

import com.blitz.domain.comments.Comments;
import org.springframework.data.domain.Page;

import java.util.List;

public record CommentPageResponse(
        List<CommentThreadDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasPrevious,
        boolean hasNext,
        long activeCount) {

    public static CommentPageResponse of(List<CommentThreadDto> content, Page<Comments> page, long activeCount) {
        return new CommentPageResponse(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasPrevious(),
                page.hasNext(),
                activeCount);
    }
}
