-- 읽기 전용: TourAPI 수집 장소의 종목/실내외/시즌 분류 누락을 확인한다.
-- 시즌은 영업일/현재 운영 여부가 아니라 장소별 활동의 탐색 분류다.
-- 전체 수집 장소와 공개 장소를 구분하고, 복수 활동의 누락도 각각 센다.
SET NAMES utf8mb4;
START TRANSACTION READ ONLY;

WITH activity_coverage AS (
    SELECT a.place_id, COUNT(*) AS activity_count,
           SUM(a.environment_type IN ('INDOOR', 'OUTDOOR')) AS environment_count,
           SUM(EXISTS(SELECT 1 FROM place_activity_season s
                      WHERE s.place_activity_id = a.place_activity_id
                        AND s.season IN ('SPRING', 'SUMMER', 'AUTUMN', 'WINTER'))) AS season_count
    FROM place_activity a GROUP BY a.place_id
)
SELECT COUNT(*) AS imported_places,
       COALESCE(SUM(p.publish_status = 1), 0) AS public_places,
       COALESCE(SUM(p.publish_status = 1 AND c.place_id IS NULL), 0) AS public_without_activities,
       COALESCE(SUM(p.publish_status = 1 AND COALESCE(c.environment_count, 0) = 0), 0) AS public_without_environment,
       COALESCE(SUM(p.publish_status = 1 AND COALESCE(c.season_count, 0) = 0), 0) AS public_without_seasons,
       COALESCE(SUM(p.publish_status = 1 AND c.environment_count > 0 AND c.environment_count < c.activity_count), 0) AS public_partial_environment,
       COALESCE(SUM(p.publish_status = 1 AND c.season_count > 0 AND c.season_count < c.activity_count), 0) AS public_partial_seasons
FROM tour_api_import i JOIN place p ON p.place_id = i.place_id
LEFT JOIN activity_coverage c ON c.place_id = p.place_id;

-- 한 행은 장소별 활동 하나다. 활동 자체가 없는 장소도 한 행 반환한다.
SELECT i.content_id, p.place_id, p.name, p.publish_status,
       a.place_activity_id, c.name AS category_name, t.name AS activity_name,
       a.environment_type,
       (SELECT GROUP_CONCAT(s.season ORDER BY s.season SEPARATOR ',')
        FROM place_activity_season s WHERE s.place_activity_id = a.place_activity_id) AS seasons,
       (a.place_activity_id IS NULL) AS missing_activity,
       (a.environment_type IS NULL OR a.environment_type NOT IN ('INDOOR', 'OUTDOOR')) AS missing_environment,
       NOT EXISTS(SELECT 1 FROM place_activity_season s
                  WHERE s.place_activity_id = a.place_activity_id
                    AND s.season IN ('SPRING', 'SUMMER', 'AUTUMN', 'WINTER')) AS missing_seasons,
       p.homepage
FROM tour_api_import i JOIN place p ON p.place_id = i.place_id
LEFT JOIN place_activity a ON a.place_id = p.place_id
LEFT JOIN activity_type t ON t.activity_type_id = a.activity_type_id
LEFT JOIN activity_category c ON c.activity_category_id = t.activity_category_id
ORDER BY i.content_id, a.place_activity_id;

COMMIT;
