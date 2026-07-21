package com.blitz.service;

import com.blitz.config.auth.dto.SessionUser;
import com.blitz.domain.comments.Comments;
import com.blitz.domain.comments.CommentsRepository;
import com.blitz.domain.posts.PostsRepository;
import com.blitz.service.exception.CommentNotFoundException;
import com.blitz.service.exception.CommentVersionConflictException;
import com.blitz.service.exception.InvalidParentCommentException;
import com.blitz.service.exception.PostNotFoundException;
import com.blitz.web.dto.CommentPageResponse;
import com.blitz.web.dto.CommentResponseDto;
import com.blitz.web.dto.CommentSaveRequestDto;
import com.blitz.web.dto.CommentThreadDto;
import com.blitz.web.dto.CommentUpdateRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class CommentsService {

    private static final int PAGE_SIZE = 20;
    private static final Sort THREAD_SORT = Sort.by(Sort.Direction.ASC, "createdDate", "id");

    private final CommentsRepository commentsRepository;
    private final PostsRepository postsRepository;

    @Transactional(readOnly = true)
    public CommentPageResponse findPage(Long postId, int page, SessionUser user) {
        requirePostExists(postId);

        Pageable pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE, THREAD_SORT);
        Page<Comments> topLevel = commentsRepository.findByPostIdAndParentIdIsNull(postId, pageable);
        if (topLevel.getTotalPages() > 0 && pageable.getPageNumber() >= topLevel.getTotalPages()) {
            pageable = PageRequest.of(topLevel.getTotalPages() - 1, PAGE_SIZE, THREAD_SORT);
            topLevel = commentsRepository.findByPostIdAndParentIdIsNull(postId, pageable);
        }

        List<Long> parentIds = topLevel.getContent().stream().map(Comments::getId).toList();
        Map<Long, List<Comments>> repliesByParent = commentsRepository
                .findByPostIdAndParentIdIn(postId, parentIds, THREAD_SORT).stream()
                .collect(Collectors.groupingBy(Comments::getParentId, LinkedHashMap::new, Collectors.toList()));

        Long currentUserId = user == null ? null : user.userId();
        List<CommentThreadDto> content = topLevel.getContent().stream()
                .map(comment -> new CommentThreadDto(
                        CommentResponseDto.of(comment, currentUserId),
                        repliesByParent.getOrDefault(comment.getId(), List.of()).stream()
                                .map(reply -> CommentResponseDto.of(reply, currentUserId))
                                .toList()))
                .toList();

        long activeCount = commentsRepository.countByPostIdAndDeletedFalse(postId);
        return CommentPageResponse.of(content, topLevel, activeCount);
    }

    @Transactional
    public CommentCreateResult create(Long postId, CommentSaveRequestDto requestDto, SessionUser user) {
        requireLogin(user);
        requirePostExists(postId);

        Comments parent = null;
        if (requestDto.parentId() != null) {
            parent = commentsRepository.findByIdAndPostIdForUpdate(requestDto.parentId(), postId)
                    .orElseThrow(() -> new CommentNotFoundException(postId, requestDto.parentId()));
            if (parent.isReply() || parent.isDeleted()) {
                throw new InvalidParentCommentException(requestDto.parentId());
            }
        }

        Comments comment = Comments.builder()
                .postId(postId)
                .parentId(parent == null ? null : parent.getId())
                .authorUserId(user.userId())
                .author(user.name())
                .content(requestDto.content())
                .build();
        try {
            commentsRepository.save(comment);
        } catch (DataIntegrityViolationException ex) {
            throw new PostNotFoundException(postId);
        }

        long topLevelId = parent == null ? comment.getId() : parent.getId();
        long targetPage = pageIndexOf(postId, topLevelId);

        return new CommentCreateResult(CommentResponseDto.of(comment, user.userId()), targetPage);
    }

    @Transactional
    public CommentCreateResult update(Long postId, Long commentId, CommentUpdateRequestDto requestDto, SessionUser user) {
        Comments comment = findOrThrow(postId, commentId);
        requireOwner(comment, user);
        requireNotDeleted(postId, comment);
        requireCurrentVersion(comment, requestDto.version());

        comment.update(requestDto.content());
        flushOrThrowConflict(comment);

        long targetPage = pageIndexOf(postId, comment.isReply() ? comment.getParentId() : comment.getId());
        return new CommentCreateResult(CommentResponseDto.of(comment, user.userId()), targetPage);
    }

    @Transactional
    public CommentCreateResult delete(Long postId, Long commentId, Long expectedVersion, SessionUser user) {
        Comments comment = findOrThrow(postId, commentId);
        requireOwner(comment, user);
        requireNotDeleted(postId, comment);
        requireCurrentVersion(comment, expectedVersion);

        comment.delete(LocalDateTime.now());
        flushOrThrowConflict(comment);

        long targetPage = pageIndexOf(postId, comment.isReply() ? comment.getParentId() : comment.getId());
        return new CommentCreateResult(CommentResponseDto.of(comment, user.userId()), targetPage);
    }

    @Transactional
    public void deleteAllForPost(Long postId) {
        commentsRepository.deleteByPostId(postId);
    }

    private void flushOrThrowConflict(Comments comment) {
        try {
            commentsRepository.flush();
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new CommentVersionConflictException(comment.getId());
        }
    }

    private long pageIndexOf(Long postId, Long topLevelCommentId) {
        List<Long> ids = commentsRepository.findTopLevelIdsOrdered(postId);
        int index = ids.indexOf(topLevelCommentId);
        if (index < 0) {
            throw new CommentNotFoundException(postId, topLevelCommentId);
        }
        return index / PAGE_SIZE;
    }

    private void requirePostExists(Long postId) {
        if (!postsRepository.existsById(postId)) {
            throw new PostNotFoundException(postId);
        }
    }

    private Comments findOrThrow(Long postId, Long commentId) {
        return commentsRepository.findByIdAndPostId(commentId, postId)
                .orElseThrow(() -> new CommentNotFoundException(postId, commentId));
    }

    private void requireLogin(SessionUser user) {
        if (user == null || user.userId() == null) {
            throw new AuthenticationCredentialsNotFoundException("로그인이 필요합니다.");
        }
    }

    private void requireOwner(Comments comment, SessionUser user) {
        requireLogin(user);
        if (!comment.isOwnedBy(user.userId())) {
            throw new AccessDeniedException("작성자만 수정하거나 삭제할 수 있습니다.");
        }
    }

    private void requireNotDeleted(Long postId, Comments comment) {
        if (comment.isDeleted()) {
            throw new CommentNotFoundException(postId, comment.getId());
        }
    }

    private void requireCurrentVersion(Comments comment, Long expectedVersion) {
        if (!Objects.equals(comment.getVersion(), expectedVersion)) {
            throw new CommentVersionConflictException(comment.getId());
        }
    }

    public record CommentCreateResult(CommentResponseDto comment, long targetPage) {
    }
}
