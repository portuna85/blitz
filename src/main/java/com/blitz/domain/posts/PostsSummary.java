package com.blitz.domain.posts;

import java.time.LocalDateTime;

/**
 * Closed projection used by list queries so large post bodies are not loaded.
 */
public interface PostsSummary {

    Long getId();

    String getTitle();

    String getAuthor();

    LocalDateTime getModifiedDate();
}
