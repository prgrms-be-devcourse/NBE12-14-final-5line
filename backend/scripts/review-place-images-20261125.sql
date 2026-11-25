-- TourAPI detailCommon2의 장소 ID, 대표 사진, 공공누리 유형과 원본 응답을 확인한 목록.
-- 팀 수집 대상 500곳 중 공개 장소의 비어 있는 대표 사진만 채운다.
SET NAMES utf8mb4;
CREATE TEMPORARY TABLE reviewed_place_images (
    content_id VARCHAR(50) PRIMARY KEY,
    expected_name VARCHAR(50) NOT NULL,
    image_url VARCHAR(1000) NOT NULL,
    license_type VARCHAR(10) NOT NULL
);
INSERT INTO reviewed_place_images VALUES
('131297', '롯데월드 아이스링크',
 'https://tong.visitkorea.or.kr/cms/resource/94/3584594_image2_1.jpg', 'Type1'),
('663785', '서울광장 스케이트장',
 'https://tong.visitkorea.or.kr/cms/resource/00/3043800_image2_1.jpg', 'Type3'),
('131317', '한강시민공원 여의도수영장(실외)',
 'https://tong.visitkorea.or.kr/cms/resource/63/3557763_image2_1.jpg', 'Type3'),
('1148818', '한강공원 뚝섬 눈썰매장',
 'https://tong.visitkorea.or.kr/cms/resource/95/1985295_image2_1.jpg', 'Type3'),
('2361081', '가평 레일바이크',
 'https://tong.visitkorea.or.kr/cms/resource/45/3096245_image2_1.jpg', 'Type1'),
('2991028', '광안리 해양레포츠센터',
 'https://tong.visitkorea.or.kr/cms/resource/05/2991005_image2_1.jpg', 'Type3'),
('2750941', '난지캠핑장',
 'https://tong.visitkorea.or.kr/cms/resource/80/4103680_image2_1.jpg', 'Type1'),
('1017568', '강동그린웨이 가족캠핑장',
 'https://tong.visitkorea.or.kr/cms/resource/29/3548029_image2_1.jpg', 'Type3'),
('2743751', '뚝섬 윈드서핑장',
 'https://tong.visitkorea.or.kr/cms/resource/48/4129848_image2_1.jpg', 'Type1'),
('3058205', '응봉산 암벽등반공원',
 'https://tong.visitkorea.or.kr/cms/resource/30/3514930_image2_1.jpg', 'Type1'),
('3076754', '더베이101요트투어',
 'https://tong.visitkorea.or.kr/cms/resource/69/4089669_image2_1.jpg', 'Type1'),
('1622267', '염주실내수영장',
 'https://tong.visitkorea.or.kr/cms/resource/14/1587814_image2_1.jpg', 'Type3'),
('3061018', '동래아이스링크',
 'https://tong.visitkorea.or.kr/cms/resource/04/3061004_image2_1.JPG', 'Type3'),
('3080317', '부산광역시 부산실내빙상장',
 'https://tong.visitkorea.or.kr/cms/resource/11/3080311_image2_1.JPG', 'Type3');

START TRANSACTION;
SELECT lock_id FROM tour_api_import_lock WHERE lock_id = 1 FOR UPDATE;
INSERT INTO place_representative_image (place_id, url, attribution, source_url, license_type, verified_at)
SELECT p.place_id, r.image_url, CONCAT('한국관광공사 TourAPI 제공 · ', r.expected_name, ' 대표 사진'),
       'https://www.data.go.kr/data/15101578/openapi.do', r.license_type, '2026-11-25 00:00:00'
FROM reviewed_place_images r
JOIN tour_api_import i ON i.content_id = r.content_id
JOIN place p ON p.place_id = i.place_id AND BINARY p.name = BINARY r.expected_name
JOIN source_record s ON s.source_record_id = i.source_record_id AND s.place_id = p.place_id
    AND s.type = 'TOUR_API' AND s.origin_id = r.content_id
LEFT JOIN place_representative_image existing ON existing.place_id = p.place_id
WHERE p.publish_status = 1 AND existing.place_id IS NULL;
SELECT ROW_COUNT() AS images_added_this_run;
COMMIT;
DROP TEMPORARY TABLE reviewed_place_images;
