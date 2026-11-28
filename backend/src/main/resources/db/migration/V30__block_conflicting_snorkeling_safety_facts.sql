CREATE OR REPLACE VIEW snorkeling_allowed_place AS
SELECT DISTINCT f.place_id FROM place_detail_fact f
WHERE f.field_key='snorkelingSafety' AND f.verification_status='VERIFIED' AND f.verified_at IS NOT NULL
  AND JSON_UNQUOTE(JSON_EXTRACT(f.value_json,'$.entryStatus'))='ALLOWED'
  AND f.valid_from IS NOT NULL AND f.valid_until IS NOT NULL
  AND f.valid_from <= CURRENT_DATE() AND f.valid_until >= CURRENT_DATE()
  AND NOT EXISTS (
    SELECT 1 FROM place_detail_fact b
    WHERE b.place_id=f.place_id AND b.field_key='snorkelingSafety'
      AND b.verification_status='VERIFIED' AND b.verified_at IS NOT NULL
      AND (b.valid_from IS NULL OR b.valid_from <= CURRENT_DATE())
      AND (b.valid_until IS NULL OR b.valid_until >= CURRENT_DATE())
      AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(b.value_json,'$.entryStatus')),'UNKNOWN') <> 'ALLOWED'
  );
