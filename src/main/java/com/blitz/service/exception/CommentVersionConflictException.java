package com.blitz.service.exception;

public class CommentVersionConflictException extends RuntimeException {

    public CommentVersionConflictException(Long id) {
        super("댓글이 다른 요청에 의해 변경되었습니다. id=" + id);
    }
}
