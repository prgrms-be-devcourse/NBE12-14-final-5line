ALTER TABLE place_notice
    ADD COLUMN publication_request_id VARCHAR(36) NULL,
    ADD CONSTRAINT uk_place_notice_publication_request UNIQUE (place_id, publication_request_id);
