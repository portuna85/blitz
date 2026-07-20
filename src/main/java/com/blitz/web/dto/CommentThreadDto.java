package com.blitz.web.dto;

import java.util.List;

public record CommentThreadDto(CommentResponseDto comment, List<CommentResponseDto> replies) {
}
