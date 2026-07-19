package com.blitz.domain.user;

import com.blitz.domain.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Objects;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_id"}))
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(length = 2048)
    private String picture;

    @Column(nullable = false, length = 255)
    private String provider;

    @Column(name = "provider_id", nullable = false, length = 255)
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Builder
    public User(String name, String email, String picture, String provider, String providerId, Role role) {
        this.name = requireText(name, "name", 255);
        this.email = requireText(email, "email", 255);
        this.picture = optionalText(picture, "picture", 2048);
        this.provider = requireText(provider, "provider", 255);
        this.providerId = requireText(providerId, "providerId", 255);
        this.role = Objects.requireNonNull(role, "role must not be null");
    }

    public User update(String name, String email, String picture) {
        this.name = requireText(name, "name", 255);
        this.email = requireText(email, "email", 255);
        this.picture = optionalText(picture, "picture", 2048);

        return this;
    }

    public String getRoleKey() {
        return this.role.getKey();
    }

    private static String requireText(String value, String field, int maxLength) {
        String normalized = optionalText(value, field, maxLength);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static String optionalText(String value, String field, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }
}
