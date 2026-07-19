package com.blitz.web;

import com.blitz.config.auth.dto.SessionUser;
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

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @AfterEach
    void cleanUp() {
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
