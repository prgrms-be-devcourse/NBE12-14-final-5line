ALTER TABLE place_operating_change_candidate
    ADD COLUMN decision VARCHAR(10) NULL,
    ADD COLUMN resolved_by BIGINT NULL,
    ADD COLUMN resolution_reason VARCHAR(500) NULL,
    ADD CONSTRAINT fk_operating_candidate_resolver FOREIGN KEY (resolved_by)
        REFERENCES `user` (user_id) ON DELETE SET NULL,
    ADD CONSTRAINT ck_operating_candidate_decision CHECK (
        decision IS NULL OR (decision IN ('APPROVED', 'REJECTED') AND resolved_at IS NOT NULL)
    ),
    ADD INDEX ix_operating_candidate_decision (place_id, field_name, values_hash, decision);
