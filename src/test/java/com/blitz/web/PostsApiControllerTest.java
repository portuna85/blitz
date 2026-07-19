package com.blitz.web;

import com.blitz.config.auth.dto.SessionUser;
import com.blitz.domain.posts.Posts;
import com.blitz.domain.posts.PostsRepository;
import com.blitz.domain.user.Role;
import com.blitz.domain.user.User;
import com.blitz.web.dto.PostsSaveRequestDto;
import com.blitz.web.dto.PostsUpdateRequestDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// For mockMvc

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PostsApiControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private PostsRepository postsRepository;

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;
    private MockHttpSession session;
    private SessionUser loginUser;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        loginUser = new SessionUser(User.builder()
                .name("author")
                .email("author@example.com")
                .provider("test")
                .providerId("test-provider-id")
                .role(Role.USER)
                .build());

        session = new MockHttpSession();
        session.setAttribute("user", loginUser);
    }

    @AfterEach
    void tearDown() {
        postsRepository.deleteAll();
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Posts_등록된다")
    void postIsCreated() throws Exception {
        //given
        String title = "title";
        String content = "content";
        PostsSaveRequestDto requestDto = new PostsSaveRequestDto(title, content);

        String url = "http://localhost:" + port + "/api/v1/posts";

        //when
        mvc.perform(post(url)
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(requestDto)))
                .andExpect(status().isOk());

        //then
        List<Posts> all = postsRepository.findAll();
        assertThat(all.get(0).getTitle()).isEqualTo(title);
        assertThat(all.get(0).getContent()).isEqualTo(content);
        assertThat(all.get(0).getAuthorEmail()).isEqualTo(loginUser.email());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("제목이_비어있으면_등록이_거부된다")
    void postCreationFailsWhenTitleIsBlank() throws Exception {
        //given
        PostsSaveRequestDto requestDto = new PostsSaveRequestDto("", "content");

        String url = "http://localhost:" + port + "/api/v1/posts";

        //when
        mvc.perform(post(url)
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(requestDto)))
                .andExpect(status().isBadRequest());

        //then
        assertThat(postsRepository.findAll()).isEmpty();
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("게시글_목록이_페이지네이션되어_조회된다")
    void postListIsPaginated() throws Exception {
        //given
        for (int i = 0; i < 3; i++) {
            postsRepository.save(Posts.builder()
                    .title("title" + i)
                    .content("content" + i)
                    .author(loginUser.name())
                    .authorEmail(loginUser.email())
                    .build());
        }

        String url = "http://localhost:" + port + "/api/v1/posts/list?page=0&size=2";

        //when & then
        mvc.perform(get(url).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Posts_수정된다")
    void postIsUpdated() throws Exception {
        //given
        Posts savedPosts = postsRepository.save(Posts.builder()
                .title("title")
                .content("content")
                .author(loginUser.name())
                .authorEmail(loginUser.email())
                .build());

        Long updateId = savedPosts.getId();
        String expectedTitle = "title2";
        String expectedContent = "content2";

        PostsUpdateRequestDto requestDto = new PostsUpdateRequestDto(expectedTitle, expectedContent);

        String url = "http://localhost:" + port + "/api/v1/posts/" + updateId;

        //when
        mvc.perform(put(url)
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(requestDto)))
                .andExpect(status().isOk());

        //then
        List<Posts> all = postsRepository.findAll();
        assertThat(all.get(0).getTitle()).isEqualTo(expectedTitle);
        assertThat(all.get(0).getContent()).isEqualTo(expectedContent);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("존재하지_않는_게시글_조회시_404가_반환된다")
    void findByIdReturns404WhenPostDoesNotExist() throws Exception {
        //given
        String url = "http://localhost:" + port + "/api/v1/posts/999999";

        //when & then
        mvc.perform(get(url).session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("작성자가_아니면_수정시_403이_반환된다")
    void updateReturns403WhenNotOwner() throws Exception {
        //given
        Posts savedPosts = postsRepository.save(Posts.builder()
                .title("title")
                .content("content")
                .author("other")
                .authorEmail("other@example.com")
                .build());

        PostsUpdateRequestDto requestDto = new PostsUpdateRequestDto("title2", "content2");
        String url = "http://localhost:" + port + "/api/v1/posts/" + savedPosts.getId();

        //when & then
        mvc.perform(put(url)
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(requestDto)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").exists());
    }
}
