CREATE TABLE place_operating_change_candidate (
    candidate_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    place_id BIGINT NOT NULL,
    field_name VARCHAR(30) NOT NULL,
    previous_value TEXT NULL,
    new_value TEXT NOT NULL,
    previous_source_url VARCHAR(1000) NOT NULL,
    new_source_url VARCHAR(1000) NOT NULL,
    detected_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    resolved_at DATETIME(6) NULL,
    values_hash BINARY(32) NOT NULL,
    pending_hash BINARY(32) GENERATED ALWAYS AS (
        CASE WHEN resolved_at IS NULL THEN values_hash ELSE NULL END
    ) STORED,
    CONSTRAINT fk_operating_candidate_info FOREIGN KEY (place_id)
        REFERENCES place_operating_info (place_id) ON DELETE CASCADE,
    CONSTRAINT ck_operating_candidate_field CHECK (field_name IN ('operatingHours', 'restDays', 'contactPhone')),
    CONSTRAINT uk_operating_pending_change UNIQUE (place_id, field_name, pending_hash),
    KEY ix_operating_candidate_review (resolved_at, detected_at, candidate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
