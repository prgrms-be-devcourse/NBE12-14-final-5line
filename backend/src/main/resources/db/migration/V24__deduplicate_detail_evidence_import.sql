-- An import may be resumed without duplicating the same source fact or price candidate.
ALTER TABLE place_detail_fact
    ADD COLUMN evidence_key CHAR(64) NULL,
    ADD CONSTRAINT uk_detail_fact_evidence UNIQUE (evidence_key);

ALTER TABLE place_price
    ADD COLUMN evidence_key CHAR(64) NULL,
    ADD CONSTRAINT uk_place_price_evidence UNIQUE (evidence_key);
