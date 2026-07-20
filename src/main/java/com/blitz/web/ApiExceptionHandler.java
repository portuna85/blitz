package com.blitz.web;

import com.blitz.service.exception.CommentNotFoundException;
import com.blitz.service.exception.CommentVersionConflictException;
import com.blitz.service.exception.InvalidParentCommentException;
import com.blitz.service.exception.PostNotFoundException;
import com.blitz.service.exception.PostVersionConflictException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice(assignableTypes = {PostsApiController.class, CommentsApiController.class})
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, List<String>> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.computeIfAbsent(error.getField(), ignored -> new ArrayList<>())
                    .add(error.getDefaultMessage());
        }

        return response(HttpStatus.BAD_REQUEST, "validation_failed", "입력값이 올바르지 않습니다.", fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            errors.computeIfAbsent(violation.getPropertyPath().toString(), ignored -> new ArrayList<>())
                    .add(violation.getMessage());
        }
        return response(HttpStatus.BAD_REQUEST, "validation_failed", "입력값이 올바르지 않습니다.", errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleMethodValidation(HandlerMethodValidationException ex) {
        return response(HttpStatus.BAD_REQUEST, "validation_failed", "요청 경로나 매개변수가 올바르지 않습니다.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableMessage(HttpMessageNotReadableException ex) {
        return response(HttpStatus.BAD_REQUEST, "malformed_json", "요청 본문을 읽을 수 없습니다.");
    }

    @ExceptionHandler(PostNotFoundException.class)
    public ResponseEntity<ApiError> handlePostNotFound(PostNotFoundException ex) {
        return response(HttpStatus.NOT_FOUND, "post_not_found", ex.getMessage());
    }

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<ApiError> handleAuthenticationRequired(AuthenticationCredentialsNotFoundException ex) {
        return response(HttpStatus.UNAUTHORIZED, "authentication_required", "로그인이 필요합니다.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return response(HttpStatus.FORBIDDEN, "access_denied", ex.getMessage());
    }

    @ExceptionHandler({PostVersionConflictException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ApiError> handleOptimisticLockingFailure(RuntimeException ex) {
        return response(
                HttpStatus.CONFLICT,
                "post_version_conflict",
                "다른 사용자가 먼저 이 게시글을 변경했습니다. 새로고침 후 다시 시도해 주세요.");
    }

    @ExceptionHandler(CommentNotFoundException.class)
    public ResponseEntity<ApiError> handleCommentNotFound(CommentNotFoundException ex) {
        return response(HttpStatus.NOT_FOUND, "comment_not_found", ex.getMessage());
    }

    @ExceptionHandler(InvalidParentCommentException.class)
    public ResponseEntity<ApiError> handleInvalidParentComment(InvalidParentCommentException ex) {
        return response(HttpStatus.BAD_REQUEST, "invalid_parent_comment", ex.getMessage());
    }

    @ExceptionHandler(CommentVersionConflictException.class)
    public ResponseEntity<ApiError> handleCommentVersionConflict(CommentVersionConflictException ex) {
        return response(
                HttpStatus.CONFLICT,
                "comment_version_conflict",
                "다른 사용자가 먼저 이 댓글을 변경했습니다. 새로고침 후 다시 시도해 주세요.");
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String code, String message) {
        return response(status, code, message, Map.of());
    }

    private ResponseEntity<ApiError> response(
            HttpStatus status,
            String code,
            String message,
            Map<String, List<String>> errors) {
        return ResponseEntity.status(status).body(new ApiError(code, message, errors));
    }

    public record ApiError(String code, String message, Map<String, List<String>> errors) {

        public ApiError {
            errors = Map.copyOf(errors);
        }
    }
}
