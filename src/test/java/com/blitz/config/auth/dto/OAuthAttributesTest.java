package com.blitz.config.auth.dto;

import com.blitz.domain.user.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthAttributesTest {

    @Test
    @DisplayName("Google 응답을 안정적인 제공자 식별자로 변환한다")
    void mapsGoogleAttributes() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("sub", "google-subject");
        response.put("name", "  Blitz User  ");
        response.put("email", "user@example.com");
        response.put("picture", "https://example.com/profile.png");

        OAuthAttributes attributes = OAuthAttributes.of("google", "sub", response);

        assertThat(attributes.getProvider()).isEqualTo("google");
        assertThat(attributes.getProviderId()).isEqualTo("google-subject");
        assertThat(attributes.getName()).isEqualTo("Blitz User");
        assertThat(attributes.toEntity().getRole()).isEqualTo(Role.USER);
        assertThatThrownBy(() -> attributes.getAttributes().put("sub", "changed"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Naver 중첩 응답을 사용자 속성으로 변환한다")
    void mapsNaverAttributes() {
        Map<String, Object> profile = Map.of(
                "id", "naver-subject",
                "name", "Naver User",
                "email", "naver@example.com",
                "profile_image", "https://example.com/naver.png");

        OAuthAttributes attributes = OAuthAttributes.of("naver", "response", Map.of("response", profile));

        assertThat(attributes.getProvider()).isEqualTo("naver");
        assertThat(attributes.getProviderId()).isEqualTo("naver-subject");
        assertThat(attributes.getNameAttributeKey()).isEqualTo("id");
        assertThat(attributes.getAttributes()).containsEntry("id", "naver-subject");
    }

    @Test
    @DisplayName("지원하지 않는 OAuth 제공자는 통제된 인증 오류로 거부한다")
    void rejectsUnsupportedProvider() {
        assertThatThrownBy(() -> OAuthAttributes.of("unknown", "id", Map.of("id", "subject")))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        error -> assertThat(error.getError().getErrorCode()).isEqualTo("unsupported_provider"));
    }

    @Test
    @DisplayName("필수 식별자가 없는 응답은 통제된 인증 오류로 거부한다")
    void rejectsMissingProviderId() {
        Map<String, Object> response = Map.of(
                "name", "User",
                "email", "user@example.com");

        assertThatThrownBy(() -> OAuthAttributes.of("google", "sub", response))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        error -> assertThat(error.getError().getErrorCode()).isEqualTo("missing_provider_id"));
    }
}
