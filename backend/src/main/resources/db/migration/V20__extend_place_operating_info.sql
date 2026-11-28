-- Allow locally collected public facility contact details and separate admission fee facts.
ALTER TABLE place_operating_info MODIFY source_content_id VARCHAR(50) NULL;
ALTER TABLE place_operating_info MODIFY source_url VARCHAR(1000) NULL;
ALTER TABLE place_operating_info ADD COLUMN fee_info TEXT NULL AFTER rest_days;
