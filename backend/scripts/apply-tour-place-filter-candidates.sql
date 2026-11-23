-- classify-tour-place-filters.mjs가 생성한 후보를 반영하는 SQL 템플릿.
SET NAMES utf8mb4;
CREATE TEMPORARY TABLE filter_candidates (
    content_id VARCHAR(50) PRIMARY KEY,
    place_name VARCHAR(50) NOT NULL,
    category_name VARCHAR(50) NOT NULL,
    activity_name VARCHAR(50) NOT NULL,
    environment_type VARCHAR(10),
    add_activity BOOLEAN NOT NULL
);
INSERT INTO filter_candidates VALUES
/* 후보 VALUES */;

START TRANSACTION;
SELECT lock_id FROM tour_api_import_lock WHERE lock_id = 1 FOR UPDATE;

CREATE TEMPORARY TABLE filter_candidate_targets AS
SELECT p.place_id, t.activity_type_id, s.url AS source_url, c.*
FROM filter_candidates c
JOIN tour_api_import i ON i.content_id = c.content_id
JOIN place p ON p.place_id = i.place_id AND BINARY p.name = BINARY c.place_name
JOIN source_record s ON s.source_record_id = i.source_record_id AND s.place_id = p.place_id
    AND s.type = 'TOUR_API' AND s.origin_id = i.content_id
JOIN (
    SELECT category.name AS category_name, activity.name AS activity_name,
           MIN(activity.activity_type_id) AS activity_type_id
    FROM activity_type activity
    JOIN activity_category category ON category.activity_category_id = activity.activity_category_id
    GROUP BY category.name, activity.name HAVING COUNT(*) = 1
) t ON t.category_name = c.category_name AND t.activity_name = c.activity_name
WHERE p.publish_status = 1;

CREATE TEMPORARY TABLE filter_new_activities AS
SELECT r.* FROM filter_candidate_targets r
WHERE r.add_activity AND NOT EXISTS (
    SELECT 1 FROM place_activity a WHERE a.place_id = r.place_id
);

INSERT INTO place_activity (place_id, activity_type_id, environment_type)
SELECT place_id, activity_type_id, environment_type FROM filter_new_activities;
SELECT ROW_COUNT() AS activities_added;

CREATE TEMPORARY TABLE filter_new_environments AS
SELECT a.place_activity_id, r.*
FROM filter_candidate_targets r
JOIN place_activity a ON a.place_id = r.place_id AND a.activity_type_id = r.activity_type_id
WHERE NOT r.add_activity AND r.environment_type IS NOT NULL AND a.environment_type IS NULL;

UPDATE place_activity a JOIN filter_new_environments r ON r.place_activity_id = a.place_activity_id
SET a.environment_type = r.environment_type
WHERE a.environment_type IS NULL;
SELECT ROW_COUNT() AS existing_environments_filled;

INSERT INTO source_record (place_id, type, origin_id, url, usage_terms_memo)
SELECT place_id, 'TOUR_API_NAME', content_id, source_url,
       CONCAT('TourAPI 장소명 기반 분류. 장소명: ', place_name,
              ', 종목: ', activity_name, ', 실내외: ', COALESCE(environment_type, '미확인'))
FROM filter_new_activities;

INSERT INTO source_record (place_id, type, origin_id, url, usage_terms_memo)
SELECT place_id, 'TOUR_API_NAME', content_id, source_url,
       CONCAT('TourAPI 장소명 기반 실내외 분류. 장소명: ', place_name,
              ', 종목: ', activity_name, ', 실내외: ', environment_type)
FROM filter_new_environments;

COMMIT;
