CREATE TABLE refresh_token (
    refresh_token_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    family_id VARCHAR(36) NOT NULL,
    auth_version BIGINT NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    PRIMARY KEY (refresh_token_id),
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES `user` (user_id),
    INDEX idx_refresh_token_user (user_id),
    INDEX idx_refresh_token_family (family_id)
);
