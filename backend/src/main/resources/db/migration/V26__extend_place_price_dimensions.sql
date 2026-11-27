ALTER TABLE place_price
  ADD COLUMN ticket_name VARCHAR(160) NULL,
  ADD COLUMN price_type VARCHAR(40) NULL,
  ADD COLUMN price_unit VARCHAR(20) NULL,
  ADD COLUMN duration_minutes INT NULL,
  ADD COLUMN use_count INT NULL,
  ADD COLUMN passenger_count INT NULL,
  ADD COLUMN equipment_type VARCHAR(100) NULL,
  ADD COLUMN equipment_included BOOLEAN NULL,
  ADD COLUMN mandatory_extra_cost BOOLEAN NULL,
  ADD COLUMN residency_condition VARCHAR(160) NULL,
  ADD COLUMN raw_price_text TEXT NULL,
  ADD COLUMN additional_conditions JSON NULL,
  ADD CONSTRAINT ck_place_price_unit CHECK (price_unit IS NULL OR price_unit IN ('PERSON','VEHICLE','EQUIPMENT','GROUP')),
  ADD CONSTRAINT ck_place_price_dimensions CHECK (
    (duration_minutes IS NULL OR duration_minutes > 0) AND
    (use_count IS NULL OR use_count > 0) AND
    (passenger_count IS NULL OR passenger_count > 0));
