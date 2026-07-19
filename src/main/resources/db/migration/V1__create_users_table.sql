CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_date DATETIME(6),
    modified_date DATETIME(6),
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    picture VARCHAR(255),
    provider VARCHAR(255) NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    role ENUM('GUEST', 'USER') NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_provider_provider_id UNIQUE (provider, provider_id)
);
