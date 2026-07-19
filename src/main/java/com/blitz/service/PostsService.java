package com.blitz.service;

import com.blitz.config.auth.dto.SessionUser;
import com.blitz.domain.posts.Posts;
import com.blitz.domain.posts.PostsRepository;
import com.blitz.web.dto.PostsListResponseDto;
import com.blitz.web.dto.PostsResponseDto;
import com.blitz.web.dto.PostsSaveRequestDto;
import com.blitz.web.dto.PostsUpdateRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class PostsService {
    private final PostsRepository postsRepository;

    @Transactional
    public Long save(PostsSaveRequestDto requestDto, SessionUser user) {
        requireLogin(user);
        return postsRepository.save(requestDto.toEntity(user.getName(), user.getEmail())).getId();
    }

    @Transactional
    public Long update(Long id, PostsUpdateRequestDto requestDto, SessionUser user) {
        Posts posts = findPostsOrThrow(id);
        requireOwner(posts, user);

        posts.update(requestDto.getTitle(), requestDto.getContent());

        return id;
    }

    @Transactional
    public void delete(Long id, SessionUser user) {
        Posts posts = findPostsOrThrow(id);
        requireOwner(posts, user);

        postsRepository.delete(posts);
    }

    @Transactional(readOnly = true)
    public PostsResponseDto findById(Long id) {
        return new PostsResponseDto(findPostsOrThrow(id));
    }

    @Transactional(readOnly = true)
    public boolean isAuthor(Long id, String email) {
        return findPostsOrThrow(id).isAuthor(email);
    }

    @Transactional(readOnly = true)
    public List<PostsListResponseDto> findAllDesc() {
        return postsRepository.findAllDesc().stream()
                .map(PostsListResponseDto::new)
                .collect(Collectors.toList());
    }

    private Posts findPostsOrThrow(Long id) {
        return postsRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 게시글이 없습니다. id=" + id));
    }

    private void requireLogin(SessionUser user) {
        if (user == null) {
            throw new AccessDeniedException("로그인이 필요합니다.");
        }
    }

    private void requireOwner(Posts posts, SessionUser user) {
        requireLogin(user);
        if (!posts.isAuthor(user.getEmail())) {
            throw new AccessDeniedException("작성자만 수정하거나 삭제할 수 있습니다.");
        }
    }
}
