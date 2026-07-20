package com.blitz.web;

import com.blitz.config.auth.dto.SessionUser;
import com.blitz.domain.comments.Comments;
import com.blitz.domain.comments.CommentsRepository;
import com.blitz.domain.posts.Posts;
import com.blitz.domain.posts.PostsRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
class IndexControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private PostsRepository postsRepository;

    @Autowired
    private CommentsRepository commentsRepository;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @AfterEach
    void cleanUp() {
        commentsRepository.deleteAll();
        postsRepository.deleteAll();
    }

    @Test
    @DisplayName("메인페이지_로딩")
    void mainPageLoads() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<h1>게시글</h1>")));
    }

    @Test
    @DisplayName("게시글 상세 페이지는 인증 없이 본문을 렌더링한다")
    void publicDetailPageLoads() throws Exception {
        Posts post = savePost(1L);

        mvc.perform(get("/posts/{id}", post.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("public-title")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("public-content")));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("작성자는 게시글 수정 페이지를 열 수 있다")
    void ownerCanLoadEditPage() throws Exception {
        Posts post = savePost(1L);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                SessionUser.SESSION_ATTRIBUTE,
                new SessionUser(1L, "author", "author@example.com"));

        mvc.perform(get("/posts/{id}/edit", post.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"post-version\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("public-title")));
    }

    @Test
    @DisplayName("게시글 상세 페이지는 CSRF 메타 태그와 빈 댓글 상태를 렌더링한다")
    void detailPageRendersCsrfMetaAndEmptyCommentState() throws Exception {
        Posts post = savePost(1L);

        mvc.perform(get("/posts/{id}", post.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf_header\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("첫 댓글을 남겨보세요.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("댓글을 작성하려면 로그인하세요.")));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("로그인 사용자는 댓글 작성 폼을 보고 작성자만 수정삭제 버튼을 본다")
    void loggedInUserSeesCommentFormAndOwnerSeesEditDeleteButtons() throws Exception {
        Posts post = savePost(1L);
        commentsRepository.saveAndFlush(Comments.builder()
                .postId(post.getId())
                .parentId(null)
                .authorUserId(1L)
                .author("author")
                .content("owned comment")
                .build());
        commentsRepository.saveAndFlush(Comments.builder()
                .postId(post.getId())
                .parentId(null)
                .authorUserId(2L)
                .author("other")
                .content("other comment")
                .build());

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                SessionUser.SESSION_ATTRIBUTE,
                new SessionUser(1L, "author", "author@example.com"));

        mvc.perform(get("/posts/{id}", post.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"commentCreateForm\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("owned comment")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("other comment")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("comment-edit-btn")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("comment-delete-btn")));
    }

    @Test
    @DisplayName("삭제된 댓글은 tombstone 문구로 표시되고 남은 답글은 그대로 노출된다")
    void deletedCommentShowsTombstoneWhileRepliesRemain() throws Exception {
        Posts post = savePost(1L);
        Comments top = commentsRepository.saveAndFlush(Comments.builder()
                .postId(post.getId())
                .parentId(null)
                .authorUserId(1L)
                .author("author")
                .content("will be deleted")
                .build());
        top.delete(java.time.LocalDateTime.now());
        commentsRepository.saveAndFlush(top);
        commentsRepository.saveAndFlush(Comments.builder()
                .postId(post.getId())
                .parentId(top.getId())
                .authorUserId(2L)
                .author("replier")
                .content("still here")
                .build());

        mvc.perform(get("/posts/{id}", post.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("삭제된 댓글입니다.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("still here")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("will be deleted"))));
    }

    @Test
    @DisplayName("20개를 초과하는 상위 댓글은 다음 페이지 링크를 렌더링한다")
    void detailPageRendersNextPageLinkBeyond20Comments() throws Exception {
        Posts post = savePost(1L);
        for (int i = 0; i < 21; i++) {
            commentsRepository.save(Comments.builder()
                    .postId(post.getId())
                    .parentId(null)
                    .authorUserId(1L)
                    .author("author")
                    .content("comment-" + i)
                    .build());
        }
        commentsRepository.flush();

        mvc.perform(get("/posts/{id}", post.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("comment-pagination")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("commentPage=1")));
    }

    private Posts savePost(Long authorUserId) {
        return postsRepository.saveAndFlush(Posts.builder()
                .title("public-title")
                .content("public-content")
                .author("author")
                .authorEmail("author@example.com")
                .authorUserId(authorUserId)
                .build());
    }
}
