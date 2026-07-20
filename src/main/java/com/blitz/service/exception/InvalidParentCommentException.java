package com.blitz.service.exception;

public class InvalidParentCommentException extends RuntimeException {

    public InvalidParentCommentException(Long parentId) {
        super("부모 댓글로 지정할 수 없습니다. parentId=" + parentId);
    }
}
