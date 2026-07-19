ALTER TABLE posts
    ADD CONSTRAINT fk_posts_author_user
        FOREIGN KEY (author_user_id) REFERENCES users (id) ON DELETE RESTRICT;
