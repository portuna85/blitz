package com.blitz.config.auth;

import com.blitz.config.auth.dto.SessionUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class LoginUserArgumentResolverTest {

    private final LoginUserArgumentResolver resolver = new LoginUserArgumentResolver();

    @Test
    @DisplayName("익명 요청을 해석할 때 새 세션을 만들지 않는다")
    void doesNotCreateSessionForAnonymousRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();

        Object resolved = resolver.resolveArgument(parameter(), null, new ServletWebRequest(request), null);

        assertThat(resolved).isNull();
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    @DisplayName("기존 세션의 로그인 사용자를 반환한다")
    void returnsUserFromExistingSession() throws Exception {
        SessionUser user = new SessionUser(1L, "user", "user@example.com");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(SessionUser.SESSION_ATTRIBUTE, user);

        assertThat(resolver.supportsParameter(parameter())).isTrue();
        assertThat(resolver.resolveArgument(parameter(), null, new ServletWebRequest(request), null))
                .isEqualTo(user);
    }

    private MethodParameter parameter() throws NoSuchMethodException {
        Method method = Fixture.class.getDeclaredMethod("handle", SessionUser.class);
        return new MethodParameter(method, 0);
    }

    private static class Fixture {
        @SuppressWarnings("unused")
        void handle(@LoginUser SessionUser user) {
        }
    }
}
