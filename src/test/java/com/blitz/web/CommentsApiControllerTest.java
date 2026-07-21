package com.blitz.web;

import com.blitz.config.auth.dto.SessionUser;
import com.blitz.domain.comments.Comments;
import com.blitz.domain.comments.CommentsRepository;
import com.blitz.domain.posts.Posts;
import com.blitz.domain.posts.PostsRepository;
import com.blitz.web.dto.CommentSaveRequestDto;
import com.blitz.web.dto.CommentUpdateRequestDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
class CommentsApiControllerTest {

    private static final Long LOGIN_USER_ID = 101L;
    private static final Long OTHER_USER_ID = 202L;
    private static final String SHARED_EMAIL = "shared@example.com";

    @Autowired
    private PostsRepository postsRepository;

    @Autowired
    private CommentsRepository commentsRepository;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mvc;
    private MockHttpSession session;
    private SessionUser loginUser;
    private Posts post;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        loginUser = new SessionUser(LOGIN_USER_ID, "author", SHARED_EMAIL);
        session = new MockHttpSession();
        session.setAttribute(SessionUser.SESSION_ATTRIBUTE, loginUser);

        post = postsRepository.saveAndFlush(Posts.builder()
                .title("post-title")
                .content("post-content")
                .author("author")
                .authorEmail(SHARED_EMAIL)
                .authorUserId(LOGIN_USER_ID)
                .build());
    }

    @AfterEach
    void tearDown() {
        commentsRepository.deleteAll();
        postsRepository.deleteAll();
    }

    private Comments newComment(Long postId, Long parentId, Long authorUserId, String content) {
        return Comments.builder()
                .postId(postId)
                .parentId(parentId)
                .authorUserId(authorUserId)
                .author("author-" + authorUserId)
                .content(content)
                .build();
    }

    private Comments saveComment(Long postId, Long parentId, Long authorUserId, String content) {
        return commentsRepository.saveAndFlush(newComment(postId, parentId, authorUserId, content));
    }

    @Test
    @DisplayName("익명 사용자도 댓글 페이지를 조회할 수 있다")
    void anonymousCanViewCommentPage() throws Exception {
        saveComment(post.getId(), null, LOGIN_USER_ID, "top comment");

        mvc.perform(get("/api/v1/posts/{postId}/comments", post.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].comment.owner").value(false))
                .andExpect(jsonPath("$.activeCount").value(1));
    }

    @Test
    @DisplayName("범위를 벗어난 페이지를 요청하면 마지막 유효 페이지를 반환한다")
    void outOfRangePageClampsToLastValidPage() throws Exception {
        for (int i = 0; i < 21; i++) {
            saveComment(post.getId(), null, LOGIN_USER_ID, "top" + i);
        }

        mvc.perform(get("/api/v1/posts/{postId}/comments", post.getId()).queryParam("page", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    @DisplayName("익명 작성 요청은 401을 반환한다")
    void anonymousCreationIsUnauthorized() throws Exception {
        CommentSaveRequestDto requestDto = new CommentSaveRequestDto("content", null);

        mvc.perform(post("/api/v1/posts/{postId}/comments", post.getId())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(requestDto)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("인증됐지만 CSRF가 없는 작성 요청은 403을 반환한다")
    void authenticatedCreationWithoutCsrfIsForbidden() throws Exception {
        CommentSaveRequestDto requestDto = new CommentSaveRequestDto("content", null);

        mvc.perform(post("/api/v1/posts/{postId}/comments", post.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(requestDto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("화면에 렌더링된 실제 CSRF 토큰으로 댓글을 작성한다")
    void renderedCsrfTokenAuthorizesCommentCreation() throws Exception {
        var formPage = mvc.perform(get("/posts/save").session(session))
                .andExpect(status().isOk())
                .andReturn();
        String html = formPage.getResponse().getContentAsString();
        String token = metaContent(html, "_csrf");
        String headerName = metaContent(html, "_csrf_header");
        CommentSaveRequestDto requestDto = new CommentSaveRequestDto("content", null);

        mvc.perform(post("/api/v1/posts/{postId}/comments", post.getId())
                        .session(session)
                        .header(headerName, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.comment.content").value("content"))
                .andExpect(jsonPath("$.targetPage").value(0));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("같은 이메일이어도 사용자 ID가 다르면 수정할 수 없다")
    void sameEmailDifferentUserCannotUpdateComment() throws Exception {
        Comments comment = saveComment(post.getId(), null, OTHER_USER_ID, "content");
        CommentUpdateRequestDto requestDto = new CommentUpdateRequestDto("attacker-content", comment.getVersion());

        mvc.perform(put("/api/v1/posts/{postId}/comments/{commentId}", post.getId(), comment.getId())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(requestDto)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_denied"));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("같은 이메일이어도 사용자 ID가 다르면 삭제할 수 없다")
    void sameEmailDifferentUserCannotDeleteComment() throws Exception {
        Comments comment = saveComment(post.getId(), null, OTHER_USER_ID, "content");

        mvc.perform(delete("/api/v1/posts/{postId}/comments/{commentId}", post.getId(), comment.getId())
                        .queryParam("version", comment.getVersion().toString())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_denied"));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("다른 게시글의 댓글을 부모로 지정하면 404를 반환한다")
    void parentFromDifferentPostIsNotFound() throws Exception {
        Posts otherPost = postsRepository.saveAndFlush(Posts.builder()
                .title("other")
                .content("other")
                .author("author")
                .authorEmail(SHARED_EMAIL)
                .authorUserId(LOGIN_USER_ID)
                .build());
        Comments otherPostComment = saveComment(otherPost.getId(), null, LOGIN_USER_ID, "top");
        CommentSaveRequestDto requestDto = new CommentSaveRequestDto("reply", otherPostComment.getId());

        mvc.perform(post("/api/v1/posts/{postId}/comments", post.getId())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(requestDto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("comment_not_found"));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("답글을 부모로 지정하면 400을 반환한다")
    void replyAsParentIsInvalid() throws Exception {
        Comments top = saveComment(post.getId(), null, LOGIN_USER_ID, "top");
        Comments reply = saveComment(post.getId(), top.getId(), LOGIN_USER_ID, "reply");
        CommentSaveRequestDto requestDto = new CommentSaveRequestDto("nested", reply.getId());

        mvc.perform(post("/api/v1/posts/{postId}/comments", post.getId())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(requestDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_parent_comment"));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("삭제된 댓글을 부모로 지정하면 400을 반환한다")
    void deletedParentIsInvalid() throws Exception {
        Comments top = saveComment(post.getId(), null, LOGIN_USER_ID, "top");
        top.delete(java.time.LocalDateTime.now());
        commentsRepository.saveAndFlush(top);
        CommentSaveRequestDto requestDto = new CommentSaveRequestDto("reply", top.getId());

        mvc.perform(post("/api/v1/posts/{postId}/comments", post.getId())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(requestDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_parent_comment"));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("없는 댓글을 수정하면 404를 반환한다")
    void updatingNonexistentCommentIsNotFound() throws Exception {
        CommentUpdateRequestDto requestDto = new CommentUpdateRequestDto("content", 0L);

        mvc.perform(put("/api/v1/posts/{postId}/comments/{commentId}", post.getId(), 999_999L)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(requestDto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("comment_not_found"));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("오래된 버전의 수정 요청은 409를 반환한다")
    void staleUpdateReturnsConflict() throws Exception {
        Comments comment = saveComment(post.getId(), null, LOGIN_USER_ID, "content");
        Long staleVersion = comment.getVersion();

        CommentUpdateRequestDto first = new CommentUpdateRequestDto("content-v2", staleVersion);
        mvc.perform(put("/api/v1/posts/{postId}/comments/{commentId}", post.getId(), comment.getId())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(first)))
                .andExpect(status().isOk());

        CommentUpdateRequestDto stale = new CommentUpdateRequestDto("content-v3", staleVersion);
        mvc.perform(put("/api/v1/posts/{postId}/comments/{commentId}", post.getId(), comment.getId())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(stale)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("comment_version_conflict"));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("삭제된 댓글의 응답은 본문과 작성자를 마스킹하고 authorUserId를 노출하지 않는다")
    void tombstoneResponseMasksContentAndAuthor() throws Exception {
        Comments comment = saveComment(post.getId(), null, LOGIN_USER_ID, "content");

        mvc.perform(delete("/api/v1/posts/{postId}/comments/{commentId}", post.getId(), comment.getId())
                        .queryParam("version", comment.getVersion().toString())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comment.content").doesNotExist())
                .andExpect(jsonPath("$.comment.author").doesNotExist())
                .andExpect(jsonPath("$.comment.deleted").value(true))
                .andExpect(jsonPath("$.comment.owner").value(false))
                .andExpect(jsonPath("$.comment.authorUserId").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("삭제된 댓글을 재수정하면 404를 반환한다")
    void editingDeletedCommentIsNotFound() throws Exception {
        Comments comment = saveComment(post.getId(), null, LOGIN_USER_ID, "content");
        comment.delete(java.time.LocalDateTime.now());
        commentsRepository.saveAndFlush(comment);

        CommentUpdateRequestDto requestDto = new CommentUpdateRequestDto("new-content", comment.getVersion());

        mvc.perform(put("/api/v1/posts/{postId}/comments/{commentId}", post.getId(), comment.getId())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(requestDto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("comment_not_found"));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("상위 댓글 생성 응답의 targetPage는 전체 상위 댓글 수를 기준으로 계산한다")
    void topLevelCreationReturnsCorrectTargetPage() throws Exception {
        for (int i = 0; i < 20; i++) {
            saveComment(post.getId(), null, LOGIN_USER_ID, "top" + i);
        }
        CommentSaveRequestDto requestDto = new CommentSaveRequestDto("21st", null);

        mvc.perform(post("/api/v1/posts/{postId}/comments", post.getId())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetPage").value(1));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("답글 생성 응답의 targetPage는 부모가 속한 페이지를 반환한다")
    void replyCreationReturnsParentTargetPage() throws Exception {
        Comments top = null;
        for (int i = 0; i < 21; i++) {
            Comments created = saveComment(post.getId(), null, LOGIN_USER_ID, "top" + i);
            if (i == 20) {
                top = created;
            }
        }
        CommentSaveRequestDto requestDto = new CommentSaveRequestDto("reply", top.getId());

        mvc.perform(post("/api/v1/posts/{postId}/comments", post.getId())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetPage").value(1));
    }

    private String metaContent(String html, String name) {
        Pattern pattern = Pattern.compile("<meta[^>]*name=\\\"" + Pattern.quote(name)
                + "\\\"[^>]*content=\\\"([^\\\"]+)\\\"[^>]*>");
        Matcher matcher = pattern.matcher(html);
        assertThat(matcher.find()).as("meta[%s] should be rendered", name).isTrue();
        return matcher.group(1);
    }
}
