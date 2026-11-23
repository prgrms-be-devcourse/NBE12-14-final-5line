-- TourAPI 장소 수집 후 명시적으로 실행하는 추가 분류 검수분.
-- 시즌은 탐색용 분류이며, 실제 방문일의 영업 여부는 시설 안내에서 확인한다.
SET NAMES utf8mb4;

CREATE TEMPORARY TABLE filter_review (
    content_id VARCHAR(50) PRIMARY KEY,
    place_name VARCHAR(50) NOT NULL,
    category_name VARCHAR(50) NOT NULL,
    activity_name VARCHAR(50) NOT NULL,
    add_if_unclassified BOOLEAN NOT NULL,
    environment_type VARCHAR(10) NOT NULL,
    seasons JSON NOT NULL,
    environment_source VARCHAR(100) NOT NULL,
    season_source VARCHAR(100) NOT NULL,
    evidence TEXT NOT NULL
);

INSERT INTO filter_review VALUES
('130978', '목동아이스링크(실내)', '실내', '아이스스케이팅', FALSE, 'INDOOR',
 JSON_ARRAY('SPRING', 'SUMMER', 'AUTUMN', 'WINTER'),
 'https://www.mdicerink.com/mdicerink/guide/guide_01.html',
 'https://www.mdicerink.com/mdicerink/guide/guide_01.html',
 '시설 일반개장 안내에 실내 빙상장과 사계절 스케이팅이 명시됨.'),
('131297', '롯데월드 아이스링크', '실내', '아이스스케이팅', FALSE, 'INDOOR',
 JSON_ARRAY('SPRING', 'SUMMER', 'AUTUMN', 'WINTER'),
 'https://japanese.visitkorea.or.kr/svc/whereToGo/locIntrdn/rgnContentsView.do?vcontsId=111564',
 'https://adventure.lotteworld.com/cmm/download.do?subPath=2022_Guidemap.pdf',
 '한국관광공사는 실내로 분류하며, 롯데월드 시설 가이드는 연중 이용할 수 있는 빙상장으로 안내함.'),
('663785', '서울광장 스케이트장', '체험', '아이스스케이팅', FALSE, 'OUTDOOR',
 JSON_ARRAY('WINTER'),
 'https://kids.seoul.go.kr/article/articleView.do?p_articleSn=805110',
 'https://plaza.seoul.go.kr/archives/18954',
 '서울시는 서울도서관 앞 야외 시설로 소개하며, 2026~27 시즌은 12~2월로 안내함.'),
('2361081', '가평 레일바이크', '체험', '레일바이크', TRUE, 'OUTDOOR',
 JSON_ARRAY('SPRING', 'AUTUMN', 'WINTER'),
 'https://www.railpark.co.kr/course/gapyeong/intro',
 'https://www.railpark.co.kr/course/gapyeong/schedule',
 '운영사 코스 안내에서 레일바이크와 야외 철교 구간 확인. 운행 안내의 5월과 10월 성수기 및 1월까지의 할인 안내를 계절 분류 근거로 사용함. 여름은 추가 확인 대상.');

START TRANSACTION;
SELECT lock_id FROM tour_api_import_lock WHERE lock_id = 1 FOR UPDATE;

-- 이름이 같은 종목이 여러 개라면 자동 선택하지 않는다.
CREATE TEMPORARY TABLE filter_places AS
SELECT p.place_id, t.activity_type_id, r.*
FROM filter_review r
JOIN tour_api_import i ON i.content_id = r.content_id
JOIN place p ON p.place_id = i.place_id AND p.name = r.place_name
JOIN (
    SELECT c.name AS category_name, a.name AS activity_name, MIN(a.activity_type_id) AS activity_type_id
    FROM activity_type a JOIN activity_category c ON c.activity_category_id = a.activity_category_id
    GROUP BY c.name, a.name HAVING COUNT(*) = 1
) t ON t.category_name = r.category_name AND t.activity_name = r.activity_name
WHERE p.publish_status = 1;

INSERT INTO place_activity (place_id, activity_type_id)
SELECT r.place_id, r.activity_type_id
FROM filter_places r
WHERE r.add_if_unclassified
  AND NOT EXISTS (SELECT 1 FROM place_activity a WHERE a.place_id = r.place_id);
SELECT ROW_COUNT() AS activities_added;

-- 기존에 값이 있는 항목은 증거가 달라도 그대로 둔다.
CREATE TEMPORARY TABLE filter_updates AS
SELECT a.place_activity_id, r.*,
       a.environment_type IS NULL AS fill_environment,
       NOT EXISTS (SELECT 1 FROM place_activity_season s
                   WHERE s.place_activity_id = a.place_activity_id) AS fill_seasons
FROM filter_places r
JOIN place_activity a ON a.place_id = r.place_id AND a.activity_type_id = r.activity_type_id;

UPDATE place_activity a JOIN filter_updates r ON r.place_activity_id = a.place_activity_id
SET a.environment_type = r.environment_type
WHERE r.fill_environment;
SELECT ROW_COUNT() AS environments_added;

INSERT INTO place_activity_season (place_activity_id, season)
SELECT r.place_activity_id, s.season
FROM filter_updates r
JOIN JSON_TABLE(r.seasons, '$[*]' COLUMNS (season VARCHAR(10) PATH '$')) s
WHERE r.fill_seasons;
SELECT ROW_COUNT() AS seasons_added;

INSERT INTO source_record (place_id, type, origin_id, url, verified_fields, verified_at, usage_terms_memo)
SELECT place_id, 'OFFICIAL', 'OUTPICK-FILTER-REVIEW-20261123', environment_source,
       IF(add_if_unclassified, 'activities,environmentType', 'environmentType'),
       '2026-11-23 00:00:00', CONCAT(activity_name, ': ', evidence)
FROM filter_updates WHERE fill_environment;

INSERT INTO source_record (place_id, type, origin_id, url, verified_fields, verified_at, usage_terms_memo)
SELECT place_id, 'OFFICIAL', 'OUTPICK-FILTER-REVIEW-20261123', season_source,
       'seasons', '2026-11-23 00:00:00', CONCAT(activity_name, ': ', evidence)
FROM filter_updates WHERE fill_seasons;

COMMIT;
DROP TEMPORARY TABLE filter_updates, filter_places, filter_review;
