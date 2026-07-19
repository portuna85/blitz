package com.blitz.service.exception;

public class PostVersionConflictException extends RuntimeException {

    public PostVersionConflictException(Long id) {
        super("게시글이 다른 요청에 의해 변경되었습니다. id=" + id);
    }
}
