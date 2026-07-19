CREATE TABLE posts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_date DATETIME(6),
    modified_date DATETIME(6),
    title VARCHAR(500) NOT NULL,
    content TEXT NOT NULL,
    author VARCHAR(255) NOT NULL,
    author_email VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (id)
);
