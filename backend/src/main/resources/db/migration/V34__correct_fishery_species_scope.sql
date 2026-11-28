-- Annex 2 distinguishes Chionoecetes opilio (대게) from the broader closed-season group.
INSERT INTO fishery_resource(name,scientific_name,category)
VALUES('대게','Chionoecetes opilio','CRUSTACEAN');
UPDATE fishery_protection_rule r JOIN fishery_resource f ON f.resource_id=r.resource_id
JOIN fishery_resource target ON target.name='대게'
SET r.resource_id=target.resource_id,r.applicability='대게에 한함',r.source_excerpt='대게 Chionoecetes opilio: 두흉갑장 9cm 이하'
WHERE f.name='대게류(붉은대게 제외)' AND r.rule_kind='MIN_SIZE';
UPDATE fishery_resource SET scientific_name='Sulculus diversicolor aquatilis'
WHERE name='오분자기';
