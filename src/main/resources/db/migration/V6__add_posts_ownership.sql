ALTER TABLE posts
    ADD COLUMN author_user_id BIGINT NULL AFTER author_email,
    MODIFY created_date DATETIME(6) NOT NULL,
    MODIFY modified_date DATETIME(6) NOT NULL,
    MODIFY version BIGINT NOT NULL DEFAULT 0;
