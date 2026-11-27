-- A current, verified permission fact is required before snorkeling is recommended.
CREATE VIEW snorkeling_allowed_place AS
SELECT DISTINCT place_id FROM place_detail_fact
WHERE field_key='snorkelingSafety' AND verification_status='VERIFIED' AND verified_at IS NOT NULL
  AND JSON_UNQUOTE(JSON_EXTRACT(value_json,'$.entryStatus'))='ALLOWED'
  AND valid_from IS NOT NULL AND valid_until IS NOT NULL
  AND valid_from <= CURRENT_DATE() AND valid_until >= CURRENT_DATE();
