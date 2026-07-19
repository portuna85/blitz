package com.blitz.domain.posts;

import com.blitz.domain.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;

import java.util.Objects;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Posts extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 500, nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(nullable = false)
    private String author;

    @Column(nullable = false)
    private String authorEmail;

    @Column(name = "author_user_id")
    private Long authorUserId;

    @Version
    private Long version;

    @Builder
    public Posts(String title, String content, String author, String authorEmail, Long authorUserId) {
        this.title = requireText(title, "title", 500).strip();
        this.content = requireText(content, "content", 10_000);
        this.author = requireText(author, "author", 255).strip();
        this.authorEmail = requireText(authorEmail, "authorEmail", 255).strip();
        this.authorUserId = Objects.requireNonNull(authorUserId, "authorUserId must not be null");
    }

    public boolean isOwnedBy(Long userId) {
        return userId != null && userId.equals(authorUserId);
    }

    public void update(String title, String content) {
        this.title = requireText(title, "title", 500).strip();
        this.content = requireText(content, "content", 10_000);
    }

    private static String requireText(String value, String field, int maxLength) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(field + " must not exceed " + maxLength + " characters");
        }
        return value;
    }
}
