-- Comments and single-level replies for posts. parent_id NULL = top-level comment,
-- non-NULL = reply to that top-level comment. Deletes are tombstoned (content/author
-- blanked, deleted=true) so thread structure and page ordering are preserved; only a
-- post deletion physically removes its comments (application-level cleanup, backed by
-- the ON DELETE CASCADE below as a defensive measure).
CREATE TABLE comments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    parent_id BIGINT NULL,
    author_user_id BIGINT NOT NULL,
    author VARCHAR(255) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at DATETIME(6) NULL,
    created_date DATETIME(6) NOT NULL,
    modified_date DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_comments_post
        FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
    CONSTRAINT fk_comments_parent
        FOREIGN KEY (parent_id) REFERENCES comments (id) ON DELETE CASCADE,
    CONSTRAINT fk_comments_author_user
        FOREIGN KEY (author_user_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX ix_comments_post_parent_created ON comments (post_id, parent_id, created_date, id);
CREATE INDEX ix_comments_author_user_id ON comments (author_user_id);
