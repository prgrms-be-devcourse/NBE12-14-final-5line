-- Verified tags can support place detail and later tag-based search/recommendation.
CREATE TABLE place_tag (
    tag_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    place_id BIGINT NOT NULL,
    tag VARCHAR(50) NOT NULL,
    source_kind VARCHAR(30) NOT NULL,
    source_url VARCHAR(1000) NULL,
    verification_status VARCHAR(20) NOT NULL DEFAULT 'NEEDS_REVIEW',
    verified_at DATETIME NULL,
    valid_from DATE NULL,
    valid_until DATE NULL,
    evidence_key CHAR(64) NULL,
    CONSTRAINT fk_place_tag_place FOREIGN KEY (place_id) REFERENCES place(place_id),
    CONSTRAINT ck_place_tag_status CHECK (verification_status IN ('NEEDS_REVIEW','VERIFIED','REJECTED')),
    CONSTRAINT ck_place_tag_verified CHECK ((verification_status='VERIFIED' AND verified_at IS NOT NULL) OR verification_status<>'VERIFIED'),
    CONSTRAINT uk_place_tag_evidence UNIQUE (evidence_key),
    KEY ix_place_tag_search (tag, verification_status, place_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
