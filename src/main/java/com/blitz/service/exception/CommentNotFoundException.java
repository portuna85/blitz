package com.blitz.service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class CommentNotFoundException extends RuntimeException {

    public CommentNotFoundException(Long postId, Long commentId) {
        super("해당 댓글이 없습니다. postId=" + postId + ", commentId=" + commentId);
    }
}
