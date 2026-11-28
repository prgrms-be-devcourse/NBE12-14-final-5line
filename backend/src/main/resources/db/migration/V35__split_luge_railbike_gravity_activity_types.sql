-- Keep the combined category and historical activity row; add three selectable detailed activities.
INSERT INTO activity_type(activity_category_id,name)
SELECT c.activity_category_id, v.name
FROM activity_category c
JOIN (SELECT '루지' name UNION ALL SELECT '레일바이크' UNION ALL SELECT '중력 레이싱') v
WHERE c.name='루지·레일바이크·중력 레이싱'
  AND NOT EXISTS (SELECT 1 FROM activity_type t WHERE t.activity_category_id=c.activity_category_id AND t.name=v.name);

-- Explicit TourAPI identities from the curated local seed; no name-based inference.
UPDATE place_activity pa
JOIN source_record s ON s.place_id=pa.place_id AND s.type='TOUR_API'
JOIN activity_type previous ON previous.activity_type_id=pa.activity_type_id
JOIN activity_category c ON c.activity_category_id=previous.activity_category_id
JOIN activity_type target ON target.activity_category_id=c.activity_category_id AND target.name='루지'
LEFT JOIN place_activity existing ON existing.place_id=pa.place_id AND existing.activity_type_id=target.activity_type_id
SET pa.activity_type_id=target.activity_type_id
WHERE c.name='루지·레일바이크·중력 레이싱' AND previous.name='루지·레일바이크·중력 레이싱'
  AND s.origin_id IN ('2612802','3032546','2826337','2774565','2727468','2488148','2662524','2722865','2673991','4109096')
  AND existing.place_activity_id IS NULL;

UPDATE place_activity pa
JOIN source_record s ON s.place_id=pa.place_id AND s.type='TOUR_API'
JOIN activity_type previous ON previous.activity_type_id=pa.activity_type_id
JOIN activity_category c ON c.activity_category_id=previous.activity_category_id
JOIN activity_type target ON target.activity_category_id=c.activity_category_id AND target.name='레일바이크'
LEFT JOIN place_activity existing ON existing.place_id=pa.place_id AND existing.activity_type_id=target.activity_type_id
SET pa.activity_type_id=target.activity_type_id
WHERE c.name='루지·레일바이크·중력 레이싱' AND previous.name='루지·레일바이크·중력 레이싱'
  AND s.origin_id IN ('2361081','1947118','2774559','2784734','1945729','1945438','2032322','1936362','2606233','2426995','2396259','1936389','2705373','1987215','2729978')
  AND existing.place_activity_id IS NULL;
