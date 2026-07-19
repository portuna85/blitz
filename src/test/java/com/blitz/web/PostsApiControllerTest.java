package com.blitz.web;

import com.blitz.config.auth.dto.SessionUser;
import com.blitz.domain.posts.Posts;
import com.blitz.domain.posts.PostsRepository;
import com.blitz.web.dto.PostsSaveRequestDto;
import com.blitz.web.dto.PostsUpdateRequestDto;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
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
class PostsApiControllerTest {

    private static final Long LOGIN_USER_ID = 101L;
    private static final Long OTHER_USER_ID = 202L;
    private static final String SHARED_EMAIL = "shared@example.com";

    @Autowired
    private PostsRepository postsRepository;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mvc;
    private MockHttpSession session;
    private SessionUser loginUser;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        loginUser = new SessionUser(LOGIN_USER_ID, "author", SHARED_EMAIL);
        session = new MockHttpSession();
        session.setAttribute(SessionUser.SESSION_ATTRIBUTE, loginUser);
    }

    @AfterEach
    void tearDown() {
        postsRepository.deleteAll();
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("게시글 등록은 201과 Location을 반환하고 세션 사용자 ID를 소유자로 저장한다")
    void postIsCreatedForAuthenticatedSessionUser() throws Exception {
        PostsSaveRequestDto requestDto = new PostsSaveRequestDto("title", "content");

        MvcResult result = mvc.perform(post("/api/v1/posts")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andReturn();

        Long createdId = objectMapper.readValue(result.getResponse().getContentAsByteArray(), Long.class);
        Posts created = postsRepository.findById(createdId).orElseThrow();

        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
                .endsWith("/api/v1/posts/" + createdId);
        assertThat(created.getTitle()).isEqualTo("title");
        assertThat(created.getContent()).isEqualTo("content");
        assertThat(created.getAuthorEmail()).isEqualTo(loginUser.email());
        assertThat(created.getAuthorUserId()).isEqualTo(loginUser.userId());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("제목이 비어 있으면 구조화된 400 응답을 반환한다")
    void postCreationFailsWhenTitleIsBlank() throws Exception {
        PostsSaveRequestDto requestDto = new PostsSaveRequestDto("", "content");

        mvc.perform(post("/api/v1/posts")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(requestDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.errors.title").isArray());

        assertThat(postsRepository.findAll()).isEmpty();
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("인증된 쓰기 요청도 CSRF 토큰이 없으면 403을 반환한다")
    void postCreationWithoutCsrfIsForbidden() throws Exception {
        PostsSaveRequestDto requestDto = new PostsSaveRequestDto("title", "content");

        mvc.perform(post("/api/v1/posts")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(requestDto)))
                .andExpect(status().isForbidden());

        assertThat(postsRepository.findAll()).isEmpty();
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("화면에 렌더링된 실제 CSRF 토큰과 헤더로 게시글을 등록한다")
    void renderedCsrfTokenAuthorizesBrowserRequest() throws Exception {
        MvcResult formPage = mvc.perform(get("/posts/save").session(session))
                .andExpect(status().isOk())
                .andReturn();
        String html = formPage.getResponse().getContentAsString();
        String token = metaContent(html, "_csrf");
        String headerName = metaContent(html, "_csrf_header");
        PostsSaveRequestDto requestDto = new PostsSaveRequestDto("browser-title", "browser-content");

        mvc.perform(post("/api/v1/posts")
                        .session(session)
                        .header(headerName, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(requestDto)))
                .andExpect(status().isCreated());

        assertThat(postsRepository.findAll())
                .singleElement()
                .extracting(Posts::getAuthorUserId)
                .isEqualTo(LOGIN_USER_ID);
    }

    @Test
    @DisplayName("세션 DTO만 있고 Security 인증이 없으면 쓰기 요청은 401을 반환한다")
    void postCreationWithoutSecurityAuthenticationIsUnauthorized() throws Exception {
        PostsSaveRequestDto requestDto = new PostsSaveRequestDto("title", "content");

        mvc.perform(post("/api/v1/posts")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(requestDto)))
                .andExpect(status().isUnauthorized());

        assertThat(postsRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("게시글 상세는 인증 없이 공개되고 현재 버전 ETag를 반환한다")
    void postDetailIsPublic() throws Exception {
        Posts saved = savePost(LOGIN_USER_ID, SHARED_EMAIL, "public-title");

        mvc.perform(get("/api/v1/posts/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"" + saved.getVersion() + "\""))
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.title").value("public-title"))
                .andExpect(jsonPath("$.version").value(saved.getVersion()))
                .andExpect(jsonPath("$.authorEmail").doesNotExist())
                .andExpect(jsonPath("$.authorUserId").doesNotExist());
    }

    @Test
    @DisplayName("레거시 목록 경로도 인증 없이 페이지 결과를 반환한다")
    void legacyPostListIsPublicAndPaginated() throws Exception {
        for (int i = 0; i < 3; i++) {
            savePost(LOGIN_USER_ID, SHARED_EMAIL, "title-" + i);
        }

        mvc.perform(get("/api/v1/posts/list")
                        .queryParam("page", "0")
                        .queryParam("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasPrevious").value(false))
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    @DisplayName("공개 목록은 크기를 50으로 제한하고 요청 정렬 대신 ID 내림차순을 사용한다")
    void canonicalPostListClampsSizeAndSanitizesSort() throws Exception {
        List<Posts> posts = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            posts.add(newPost(LOGIN_USER_ID, SHARED_EMAIL, "title-%02d".formatted(i)));
        }
        List<Posts> saved = postsRepository.saveAllAndFlush(posts);
        Posts newest = saved.getLast();

        mvc.perform(get("/api/v1/posts")
                        .queryParam("page", "0")
                        .queryParam("size", "500")
                        .queryParam("sort", "title,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.content.length()").value(50))
                .andExpect(jsonPath("$.content[0].id").value(newest.getId()))
                .andExpect(jsonPath("$.content[0].title").value("title-59"))
                .andExpect(jsonPath("$.totalElements").value(60))
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("작성자는 현재 버전으로 게시글을 수정하고 증가한 버전을 받는다")
    void postIsUpdatedByOwner() throws Exception {
        Posts saved = savePost(LOGIN_USER_ID, SHARED_EMAIL, "title");
        Long initialVersion = saved.getVersion();
        PostsUpdateRequestDto requestDto = new PostsUpdateRequestDto("title-2", "content-2", initialVersion);

        mvc.perform(put("/api/v1/posts/{id}", saved.getId())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(requestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.title").value("title-2"))
                .andExpect(jsonPath("$.content").value("content-2"))
                .andExpect(jsonPath("$.version").value(initialVersion + 1));

        Posts updated = postsRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getTitle()).isEqualTo("title-2");
        assertThat(updated.getContent()).isEqualTo("content-2");
        assertThat(updated.getVersion()).isEqualTo(initialVersion + 1);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("같은 이메일이어도 사용자 ID가 다르면 수정할 수 없다")
    void sameEmailDifferentUserCannotUpdatePost() throws Exception {
        Posts saved = savePost(OTHER_USER_ID, SHARED_EMAIL, "other-title");
        PostsUpdateRequestDto requestDto =
                new PostsUpdateRequestDto("attacker-title", "attacker-content", saved.getVersion());

        mvc.perform(put("/api/v1/posts/{id}", saved.getId())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(requestDto)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_denied"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.errors").isMap());

        Posts unchanged = postsRepository.findById(saved.getId()).orElseThrow();
        assertThat(unchanged.getTitle()).isEqualTo("other-title");
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("순차적인 오래된 수정 요청은 409를 반환하고 첫 수정 내용을 보존한다")
    void staleSequentialUpdateReturnsConflict() throws Exception {
        Posts saved = savePost(LOGIN_USER_ID, SHARED_EMAIL, "title");
        Long staleVersion = saved.getVersion();

        PostsUpdateRequestDto first = new PostsUpdateRequestDto("first-title", "first-content", staleVersion);
        mvc.perform(put("/api/v1/posts/{id}", saved.getId())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(first)))
                .andExpect(status().isOk());

        PostsUpdateRequestDto stale = new PostsUpdateRequestDto("stale-title", "stale-content", staleVersion);
        mvc.perform(put("/api/v1/posts/{id}", saved.getId())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(stale)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("post_version_conflict"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.errors").isMap());

        Posts current = postsRepository.findById(saved.getId()).orElseThrow();
        assertThat(current.getTitle()).isEqualTo("first-title");
        assertThat(current.getContent()).isEqualTo("first-content");
        assertThat(current.getVersion()).isEqualTo(staleVersion + 1);
    }

    @Test
    @DisplayName("존재하지 않는 공개 게시글 조회는 구조화된 404를 반환한다")
    void findByIdReturnsStructured404WhenPostDoesNotExist() throws Exception {
        mvc.perform(get("/api/v1/posts/{id}", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("post_not_found"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.errors").isMap());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("작성자는 현재 버전으로 게시글을 삭제하고 204를 받는다")
    void ownerCanDeleteCurrentPostVersion() throws Exception {
        Posts saved = savePost(LOGIN_USER_ID, SHARED_EMAIL, "delete-title");

        mvc.perform(delete("/api/v1/posts/{id}", saved.getId())
                        .queryParam("version", saved.getVersion().toString())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(postsRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("오래된 버전의 삭제 요청은 409를 반환하고 게시글을 보존한다")
    void staleDeleteReturnsConflict() throws Exception {
        Posts saved = savePost(LOGIN_USER_ID, SHARED_EMAIL, "delete-title");
        Long staleVersion = saved.getVersion();
        PostsUpdateRequestDto update = new PostsUpdateRequestDto("new-title", "new-content", staleVersion);

        mvc.perform(put("/api/v1/posts/{id}", saved.getId())
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(update)))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/v1/posts/{id}", saved.getId())
                        .queryParam("version", staleVersion.toString())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("post_version_conflict"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        assertThat(postsRepository.findById(saved.getId())).isPresent();
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("같은 이메일이어도 사용자 ID가 다르면 삭제할 수 없다")
    void sameEmailDifferentUserCannotDeletePost() throws Exception {
        Posts saved = savePost(OTHER_USER_ID, SHARED_EMAIL, "other-title");

        mvc.perform(delete("/api/v1/posts/{id}", saved.getId())
                        .queryParam("version", saved.getVersion().toString())
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_denied"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        assertThat(postsRepository.findById(saved.getId())).isPresent();
    }

    private Posts savePost(Long authorUserId, String authorEmail, String title) {
        return postsRepository.saveAndFlush(newPost(authorUserId, authorEmail, title));
    }

    private Posts newPost(Long authorUserId, String authorEmail, String title) {
        return Posts.builder()
                .title(title)
                .content(title + "-content")
                .author("author-" + authorUserId)
                .authorEmail(authorEmail)
                .authorUserId(authorUserId)
                .build();
    }

    private String metaContent(String html, String name) {
        Pattern pattern = Pattern.compile("<meta[^>]*name=\\\"" + Pattern.quote(name)
                + "\\\"[^>]*content=\\\"([^\\\"]+)\\\"[^>]*>");
        Matcher matcher = pattern.matcher(html);
        assertThat(matcher.find()).as("meta[%s] should be rendered", name).isTrue();
        return matcher.group(1);
    }
}
