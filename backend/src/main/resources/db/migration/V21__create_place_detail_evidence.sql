-- Review data is isolated from published place, favorite, and review rows.
CREATE TABLE place_import_candidate (
    candidate_id VARCHAR(255) NOT NULL PRIMARY KEY,
    place_id BIGINT NULL,
    category_name VARCHAR(80) NOT NULL,
    facility_name VARCHAR(150) NOT NULL,
    content_id VARCHAR(50) NULL,
    review_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    recommendable BOOLEAN NOT NULL DEFAULT FALSE,
    raw_evidence JSON NOT NULL,
    imported_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_import_candidate_place FOREIGN KEY (place_id) REFERENCES place(place_id),
    CONSTRAINT ck_import_candidate_review CHECK (review_status IN ('PENDING','VERIFIED','REJECTED')),
    KEY ix_import_candidate_place (place_id),
    KEY ix_import_candidate_category (category_name, review_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE place_detail_fact (
    fact_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    place_id BIGINT NOT NULL,
    field_key VARCHAR(80) NOT NULL,
    value_json JSON NOT NULL,
    source_kind VARCHAR(30) NOT NULL,
    source_url VARCHAR(1000) NULL,
    source_content_id VARCHAR(50) NULL,
    checked_at DATETIME NULL,
    verified_at DATETIME NULL,
    verification_status VARCHAR(20) NOT NULL DEFAULT 'NEEDS_REVIEW',
    valid_from DATE NULL,
    valid_until DATE NULL,
    CONSTRAINT fk_detail_fact_place FOREIGN KEY (place_id) REFERENCES place(place_id),
    CONSTRAINT ck_detail_fact_status CHECK (verification_status IN ('NEEDS_REVIEW','VERIFIED','REJECTED')),
    CONSTRAINT ck_detail_fact_verified CHECK ((verification_status='VERIFIED' AND verified_at IS NOT NULL) OR verification_status<>'VERIFIED'),
    KEY ix_detail_fact_public (place_id, verification_status, valid_from, valid_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE place_price (
    price_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    place_id BIGINT NOT NULL,
    price_kind VARCHAR(30) NOT NULL,
    age_band VARCHAR(30) NULL,
    day_type VARCHAR(30) NULL,
    season_label VARCHAR(80) NULL,
    amount_krw INT NULL,
    free_of_charge BOOLEAN NOT NULL DEFAULT FALSE,
    source_kind VARCHAR(30) NOT NULL,
    source_url VARCHAR(1000) NULL,
    source_content_id VARCHAR(50) NULL,
    checked_at DATETIME NULL,
    verified_at DATETIME NULL,
    verification_status VARCHAR(20) NOT NULL DEFAULT 'NEEDS_REVIEW',
    valid_from DATE NULL,
    valid_until DATE NULL,
    CONSTRAINT fk_price_place FOREIGN KEY (place_id) REFERENCES place(place_id),
    CONSTRAINT ck_price_amount CHECK (amount_krw IS NULL OR amount_krw >= 0),
    CONSTRAINT ck_price_free CHECK (free_of_charge=FALSE OR amount_krw=0),
    CONSTRAINT ck_price_status CHECK (verification_status IN ('NEEDS_REVIEW','VERIFIED','REJECTED')),
    CONSTRAINT ck_price_verified CHECK ((verification_status='VERIFIED' AND verified_at IS NOT NULL) OR verification_status<>'VERIFIED'),
    KEY ix_place_price_public (place_id, verification_status, valid_from, valid_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
