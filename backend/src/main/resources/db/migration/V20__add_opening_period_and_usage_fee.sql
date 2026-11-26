ALTER TABLE place_operating_info
    ADD COLUMN opening_period TEXT NULL,
    ADD COLUMN usage_fee TEXT NULL;

ALTER TABLE place_operating_change_candidate
    DROP CHECK ck_operating_candidate_field,
    ADD CONSTRAINT ck_operating_candidate_field CHECK (
        field_name IN ('operatingHours', 'restDays', 'contactPhone', 'openingPeriod', 'usageFee')
    );
