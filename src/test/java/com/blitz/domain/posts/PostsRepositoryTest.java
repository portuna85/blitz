package com.blitz.domain.posts;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
class PostsRepositoryTest {

    @Autowired
    PostsRepository postsRepository;

    @AfterEach
    void cleanup() {
        postsRepository.deleteAll();
    }

    @Test
    @DisplayName("게시글저장_불러오기")
    void savesAndLoadsPost() {
        //given
        String title = "테스트 게시글";
        String content = "테스트 본문";

        postsRepository.save(Posts.builder()
                .title(title)
                .content(content)
                .author("jojoldu")
                .authorEmail("jojoldu@gmail.com")
                .authorUserId(1L)
                .build());

        //when
        List<Posts> postsList = postsRepository.findAll();

        //then
        Posts posts = postsList.get(0);
        assertThat(posts.getTitle()).isEqualTo(title);
        assertThat(posts.getContent()).isEqualTo(content);
    }

    @Test
    @DisplayName("BaseTimeEntity_등록")
    void baseTimeEntityIsPopulatedOnSave() {
        //given
        LocalDateTime now = LocalDateTime.of(2019, 6, 4, 0, 0, 0);
        postsRepository.save(Posts.builder()
                .title("title")
                .content("content")
                .author("author")
                .authorEmail("author@example.com")
                .authorUserId(1L)
                .build());

        //when
        List<Posts> postsList = postsRepository.findAll();

        //then
        Posts posts = postsList.get(0);

        assertThat(posts.getCreatedDate()).isAfter(now);
        assertThat(posts.getModifiedDate()).isAfter(now);
    }

    @Test
    @DisplayName("낙관적_락으로_동시_수정_충돌이_감지된다")
    void optimisticLockDetectsConcurrentUpdate() {
        //given
        Posts saved = postsRepository.save(Posts.builder()
                .title("title")
                .content("content")
                .author("author")
                .authorEmail("author@example.com")
                .authorUserId(1L)
                .build());

        // 서로 다른 두 요청이 같은 시점의 게시글을 각자 조회했다고 가정
        Posts copy1 = postsRepository.findById(saved.getId()).orElseThrow();
        Posts copy2 = postsRepository.findById(saved.getId()).orElseThrow();

        copy1.update("title-v2", "content-v2");
        postsRepository.saveAndFlush(copy1);

        //when & then
        copy2.update("title-v3", "content-v3");
        assertThatThrownBy(() -> postsRepository.saveAndFlush(copy2))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
