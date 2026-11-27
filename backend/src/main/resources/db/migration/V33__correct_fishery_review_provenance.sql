-- The 2026-01-01 extracted tables are review material, not a citation to the current decree text.
UPDATE fishery_protection_rule SET official_url=NULL WHERE verification_status='NEEDS_REVIEW';
UPDATE fishery_protection_rule r JOIN fishery_resource f ON f.resource_id=r.resource_id
SET r.applicability='대게에 한함'
WHERE f.name='대게류(붉은대게 제외)' AND r.rule_kind='MIN_SIZE';
