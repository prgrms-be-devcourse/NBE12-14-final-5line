-- Preserve old place IDs while resolving a verified duplicate to its canonical place.
CREATE TABLE place_duplicate_alias (
    duplicate_place_id BIGINT NOT NULL PRIMARY KEY,
    canonical_place_id BIGINT NOT NULL,
    evidence JSON NOT NULL,
    verified_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_duplicate_alias_place FOREIGN KEY (duplicate_place_id) REFERENCES place(place_id),
    CONSTRAINT fk_canonical_alias_place FOREIGN KEY (canonical_place_id) REFERENCES place(place_id),
    CONSTRAINT ck_duplicate_alias_distinct CHECK (duplicate_place_id <> canonical_place_id),
    KEY ix_canonical_alias_place (canonical_place_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
