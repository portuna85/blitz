package com.blitz.web;

import com.blitz.config.auth.LoginUser;
import com.blitz.config.auth.dto.SessionUser;
import com.blitz.service.CommentsService;
import com.blitz.web.dto.CommentPageResponse;
import com.blitz.web.dto.CommentSaveRequestDto;
import com.blitz.web.dto.CommentUpdateRequestDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/posts/{postId}/comments")
@Validated
public class CommentsApiController {

    private final CommentsService commentsService;

    @GetMapping
    public CommentPageResponse findPage(
            @PathVariable @Positive Long postId,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @LoginUser SessionUser user) {
        return commentsService.findPage(postId, page, user);
    }

    @PostMapping
    public ResponseEntity<CommentsService.CommentCreateResult> create(
            @PathVariable @Positive Long postId,
            @Valid @RequestBody CommentSaveRequestDto requestDto,
            @LoginUser SessionUser user) {
        CommentsService.CommentCreateResult result = commentsService.create(postId, requestDto, user);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{commentId}")
                .buildAndExpand(result.comment().id())
                .toUri();
        return ResponseEntity.created(location).body(result);
    }

    @PutMapping("/{commentId}")
    public CommentsService.CommentCreateResult update(
            @PathVariable @Positive Long postId,
            @PathVariable @Positive Long commentId,
            @Valid @RequestBody CommentUpdateRequestDto requestDto,
            @LoginUser SessionUser user) {
        return commentsService.update(postId, commentId, requestDto, user);
    }

    @DeleteMapping("/{commentId}")
    public CommentsService.CommentCreateResult delete(
            @PathVariable @Positive Long postId,
            @PathVariable @Positive Long commentId,
            @RequestParam @PositiveOrZero Long version,
            @LoginUser SessionUser user) {
        return commentsService.delete(postId, commentId, version, user);
    }
}
