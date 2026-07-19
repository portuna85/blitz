package com.blitz.domain.posts;

import com.blitz.domain.BaseTimeEntity;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;

@Getter
@NoArgsConstructor
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

    @Version
    private Long version;

    @Builder
    public Posts(String title, String content, String author, String authorEmail) {
        this.title = title;
        this.content = content;
        this.author = author;
        this.authorEmail = authorEmail;
    }

    public boolean isAuthor(String email) {
        return email != null && this.authorEmail.equals(email);
    }

    public void update(String title, String content) {
        this.title = title;
        this.content = content;
    }
}
