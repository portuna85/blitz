package com.blitz.config.auth.dto;

import com.blitz.domain.user.Role;
import com.blitz.domain.user.User;
import lombok.Builder;
import lombok.Getter;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import java.util.Map;

@Getter
public class OAuthAttributes {
    private Map<String, Object> attributes;
    private String nameAttributeKey;
    private String provider;
    private String providerId;
    private String name;
    private String email;
    private String picture;

    @Builder
    public OAuthAttributes(Map<String, Object> attributes, String nameAttributeKey, String provider,
                            String providerId, String name, String email, String picture) {
        this.attributes = attributes;
        this.nameAttributeKey = nameAttributeKey;
        this.provider = provider;
        this.providerId = providerId;
        this.name = name;
        this.email = email;
        this.picture = picture;
    }

    public static OAuthAttributes of(String registrationId, String userNameAttributeName, Map<String, Object> attributes) {
        if ("naver".equals(registrationId)) {
            return ofNaver(registrationId, "id", attributes);
        }

        return ofGoogle(registrationId, userNameAttributeName, attributes);
    }

    private static OAuthAttributes ofGoogle(String provider, String userNameAttributeName, Map<String, Object> attributes) {
        OAuthAttributes result = OAuthAttributes.builder()
                .name((String) attributes.get("name"))
                .email((String) attributes.get("email"))
                .picture((String) attributes.get("picture"))
                .attributes(attributes)
                .nameAttributeKey(userNameAttributeName)
                .provider(provider)
                .providerId(stringOrNull(attributes.get(userNameAttributeName)))
                .build();

        return result.validate();
    }

    private static OAuthAttributes ofNaver(String provider, String userNameAttributeName, Map<String, Object> attributes) {
        Object responseAttribute = attributes.get("response");
        if (!(responseAttribute instanceof Map)) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_user_info_response"),
                    "네이버 사용자 정보 응답에 response 필드가 없습니다.");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) responseAttribute;

        OAuthAttributes result = OAuthAttributes.builder()
                .name((String) response.get("name"))
                .email((String) response.get("email"))
                .picture((String) response.get("profile_image"))
                .attributes(response)
                .nameAttributeKey(userNameAttributeName)
                .provider(provider)
                .providerId(stringOrNull(response.get(userNameAttributeName)))
                .build();

        return result.validate();
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private OAuthAttributes validate() {
        if (providerId == null || providerId.isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("missing_provider_id"),
                    provider + " 로그인 응답에 사용자 식별자가 없습니다.");
        }
        if (name == null || name.isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("missing_name"),
                    provider + " 로그인 응답에 이름이 없습니다.");
        }
        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("missing_email"),
                    provider + " 로그인 응답에 이메일이 없습니다.");
        }
        return this;
    }

    public User toEntity() {
        return User.builder()
                .name(name)
                .email(email)
                .picture(picture)
                .provider(provider)
                .providerId(providerId)
                .role(Role.USER)
                .build();
    }
}
