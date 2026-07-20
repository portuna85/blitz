package com.blitz.domain.comments;

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

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Comments extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "author_user_id", nullable = false)
    private Long authorUserId;

    @Column(length = 255, nullable = false)
    private String author;

    @Column(length = 1000, nullable = false)
    private String content;

    @Column(nullable = false)
    private boolean deleted;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Version
    private Long version;

    @Builder
    public Comments(Long postId, Long parentId, Long authorUserId, String author, String content) {
        this.postId = Objects.requireNonNull(postId, "postId must not be null");
        this.parentId = parentId;
        this.authorUserId = Objects.requireNonNull(authorUserId, "authorUserId must not be null");
        this.author = requireText(author, "author", 255).strip();
        this.content = requireContent(content);
    }

    public boolean isOwnedBy(Long userId) {
        return userId != null && userId.equals(authorUserId);
    }

    public boolean isReply() {
        return parentId != null;
    }

    public void update(String content) {
        this.content = requireContent(content);
    }

    public void delete(LocalDateTime deletedAt) {
        this.content = "";
        this.author = "";
        this.deleted = true;
        this.deletedAt = deletedAt;
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

    private static String requireContent(String value) {
        Objects.requireNonNull(value, "content must not be null");
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        if (trimmed.length() > 1000) {
            throw new IllegalArgumentException("content must not exceed 1000 characters");
        }
        return trimmed;
    }
}
