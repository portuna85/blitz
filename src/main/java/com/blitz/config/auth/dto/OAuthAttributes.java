package com.blitz.config.auth.dto;

import com.blitz.domain.user.Role;
import com.blitz.domain.user.User;
import lombok.Getter;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
public class OAuthAttributes {
    private final Map<String, Object> attributes;
    private final String nameAttributeKey;
    private final String provider;
    private final String providerId;
    private final String name;
    private final String email;
    private final String picture;

    private OAuthAttributes(Map<String, Object> attributes, String nameAttributeKey, String provider,
                            String providerId, String name, String email, String picture) {
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
        this.nameAttributeKey = nameAttributeKey;
        this.provider = provider;
        this.providerId = stripToNull(providerId);
        this.name = stripToNull(name);
        this.email = stripToNull(email);
        this.picture = stripToNull(picture);
    }

    public static OAuthAttributes of(String registrationId, String userNameAttributeName, Map<String, Object> attributes) {
        if (registrationId == null || attributes == null) {
            throw authenticationError("invalid_user_info_response", "OAuth 사용자 정보 응답이 올바르지 않습니다.");
        }

        return switch (registrationId) {
            case "google" -> ofGoogle(registrationId, userNameAttributeName, attributes);
            case "naver" -> ofNaver(registrationId, "id", attributes);
            default -> throw authenticationError(
                    "unsupported_provider",
                    "지원하지 않는 OAuth 제공자입니다: " + registrationId);
        };
    }

    private static OAuthAttributes ofGoogle(String provider, String userNameAttributeName, Map<String, Object> attributes) {
        OAuthAttributes result = new OAuthAttributes(
                attributes,
                userNameAttributeName,
                provider,
                stringOrNull(attributes.get(userNameAttributeName)),
                stringOrNull(attributes.get("name")),
                stringOrNull(attributes.get("email")),
                stringOrNull(attributes.get("picture")));

        return result.validate();
    }

    private static OAuthAttributes ofNaver(String provider, String userNameAttributeName, Map<String, Object> attributes) {
        Object responseAttribute = attributes.get("response");
        if (!(responseAttribute instanceof Map)) {
            throw authenticationError(
                    "invalid_user_info_response",
                    "네이버 사용자 정보 응답에 response 필드가 없습니다.");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) responseAttribute;

        OAuthAttributes result = new OAuthAttributes(
                response,
                userNameAttributeName,
                provider,
                stringOrNull(response.get(userNameAttributeName)),
                stringOrNull(response.get("name")),
                stringOrNull(response.get("email")),
                stringOrNull(response.get("profile_image")));

        return result.validate();
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String stripToNull(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    private OAuthAttributes validate() {
        if (providerId == null || providerId.isBlank()) {
            throw authenticationError(
                    "missing_provider_id",
                    provider + " 로그인 응답에 사용자 식별자가 없습니다.");
        }
        if (name == null || name.isBlank()) {
            throw authenticationError("missing_name", provider + " 로그인 응답에 이름이 없습니다.");
        }
        if (email == null || email.isBlank()) {
            throw authenticationError("missing_email", provider + " 로그인 응답에 이메일이 없습니다.");
        }
        requireMaxLength(provider, 255, "provider_too_long");
        requireMaxLength(providerId, 255, "provider_id_too_long");
        requireMaxLength(name, 255, "name_too_long");
        requireMaxLength(email, 255, "email_too_long");
        if (picture != null) {
            requireMaxLength(picture, 2048, "picture_too_long");
        }
        return this;
    }

    private static void requireMaxLength(String value, int maxLength, String code) {
        if (value.length() > maxLength) {
            throw authenticationError(code, "OAuth 사용자 정보가 허용 길이를 초과했습니다.");
        }
    }

    private static OAuth2AuthenticationException authenticationError(String code, String message) {
        return new OAuth2AuthenticationException(new OAuth2Error(code), message);
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
