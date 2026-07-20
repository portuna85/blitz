package com.blitz.service;

import com.blitz.config.auth.dto.SessionUser;
import com.blitz.domain.posts.Posts;
import com.blitz.domain.posts.PostsRepository;
import com.blitz.service.exception.PostNotFoundException;
import com.blitz.service.exception.PostVersionConflictException;
import com.blitz.web.dto.PostsListResponseDto;
import com.blitz.web.dto.PostsResponseDto;
import com.blitz.web.dto.PostsSaveRequestDto;
import com.blitz.web.dto.PostsUpdateRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@RequiredArgsConstructor
@Service
public class PostsService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "id");

    private final PostsRepository postsRepository;
    private final CommentsService commentsService;

    @Transactional
    public Long save(PostsSaveRequestDto requestDto, SessionUser user) {
        requireLogin(user);
        Posts post = Posts.builder()
                .title(requestDto.title())
                .content(requestDto.content())
                .author(user.name())
                .authorEmail(user.email())
                .authorUserId(user.userId())
                .build();

        return postsRepository.save(post).getId();
    }

    @Transactional
    public PostsResponseDto update(Long id, PostsUpdateRequestDto requestDto, SessionUser user) {
        Posts posts = findPostsOrThrow(id);
        requireOwner(posts, user);
        requireCurrentVersion(posts, requestDto.version());

        posts.update(requestDto.title(), requestDto.content());
        postsRepository.flush();

        return new PostsResponseDto(posts);
    }

    @Transactional
    public void delete(Long id, Long expectedVersion, SessionUser user) {
        Posts posts = findPostsOrThrow(id);
        requireOwner(posts, user);
        requireCurrentVersion(posts, expectedVersion);

        commentsService.deleteAllForPost(id);
        postsRepository.delete(posts);
    }

    @Transactional(readOnly = true)
    public PostsResponseDto findById(Long id) {
        return new PostsResponseDto(findPostsOrThrow(id));
    }

    @Transactional(readOnly = true)
    public PostView findDetail(Long id, SessionUser user) {
        Posts posts = findPostsOrThrow(id);
        boolean isOwner = user != null && posts.isOwnedBy(user.userId());
        return new PostView(new PostsResponseDto(posts), isOwner);
    }

    @Transactional(readOnly = true)
    public PostsResponseDto findOwnedById(Long id, SessionUser user) {
        Posts posts = findPostsOrThrow(id);
        requireOwner(posts, user);
        return new PostsResponseDto(posts);
    }

    @Transactional(readOnly = true)
    public Page<PostsListResponseDto> findAllDesc(Pageable pageable) {
        return postsRepository.findAllProjectedBy(clamp(pageable)).map(PostsListResponseDto::new);
    }

    private Pageable clamp(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequest.of(0, DEFAULT_PAGE_SIZE, DEFAULT_SORT);
        }

        int size = Math.max(1, Math.min(pageable.getPageSize(), MAX_PAGE_SIZE));
        return PageRequest.of(pageable.getPageNumber(), size, DEFAULT_SORT);
    }

    private Posts findPostsOrThrow(Long id) {
        return postsRepository.findById(id)
                .orElseThrow(() -> new PostNotFoundException(id));
    }

    private void requireLogin(SessionUser user) {
        if (user == null || user.userId() == null) {
            throw new AuthenticationCredentialsNotFoundException("로그인이 필요합니다.");
        }
    }

    private void requireOwner(Posts posts, SessionUser user) {
        requireLogin(user);
        if (!posts.isOwnedBy(user.userId())) {
            throw new AccessDeniedException("작성자만 수정하거나 삭제할 수 있습니다.");
        }
    }

    private void requireCurrentVersion(Posts posts, Long expectedVersion) {
        if (!Objects.equals(posts.getVersion(), expectedVersion)) {
            throw new PostVersionConflictException(posts.getId());
        }
    }

    public record PostView(PostsResponseDto post, boolean owner) {
    }
}
