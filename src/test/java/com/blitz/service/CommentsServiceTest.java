package com.blitz.service;

import com.blitz.config.auth.dto.SessionUser;
import com.blitz.domain.comments.CommentsRepository;
import com.blitz.domain.posts.PostsRepository;
import com.blitz.service.exception.PostNotFoundException;
import com.blitz.web.dto.CommentSaveRequestDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentsServiceTest {

    private static final Long POST_ID = 1L;

    @Mock
    private CommentsRepository commentsRepository;

    @Mock
    private PostsRepository postsRepository;

    @InjectMocks
    private CommentsService commentsService;

    @Test
    @DisplayName("게시글이 삭제되어 FK 위반이 발생하면 post_not_found로 변환한다")
    void concurrentPostDeletionDuringCreateIsTranslatedToPostNotFound() {
        SessionUser user = new SessionUser(10L, "author", "author@example.com");
        CommentSaveRequestDto requestDto = new CommentSaveRequestDto("content", null);

        when(postsRepository.existsById(POST_ID)).thenReturn(true);
        when(commentsRepository.save(any())).thenThrow(new DataIntegrityViolationException("FK violation"));

        assertThatThrownBy(() -> commentsService.create(POST_ID, requestDto, user))
                .isInstanceOf(PostNotFoundException.class);
    }
}
