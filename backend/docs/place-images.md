# 장소 대표 사진 연결

## 저장·API 계약

V9는 `place_representative_image`를 추가한다. `place_id`가 PK이자 장소 FK여서 장소당 대표 사진 한 장만 저장한다. URL, 제공 출처 문구, 출처 페이지 URL, 공공누리 유형, 확인일이 모두 필수다. 후기 업로드·MediaAsset·사진 갤러리 구조는 변경하지 않았다. 기존 ERD v3 SQL에 없던 추가 구현이므로 팀 ERD에도 이 테이블을 반영해야 한다.

목록 `items[]`와 장소 상세는 동일한 네 필드를 반환한다.

- `representativeImage`: 원본 이미지 HTTPS URL 또는 null. 기존 상세의 문자열 계약을 유지했다.
- `imageAttribution`: 제공기관과 사진 제목 표시 문구 또는 null.
- `imageSourceUrl`: 사진 제공 서비스의 공개 안내 페이지 또는 null. 인증키가 든 API 요청 URL은 저장하지 않는다.
- `imageLicense`: `Type1`, `Type3` 또는 null.

사진 없는 장소는 네 필드가 null이다. 상세는 장소 조회 시 함께 조인하며 목록은 LEFT JOIN으로 조회해 사진 없는 장소를 누락시키지 않는다. 대표 사진 PK로 장소 행 중복도 방지한다. 테스트에서 상세 5쿼리, 목록 2쿼리를 유지하는지 확인한다.

## 초기 4장 선택과 로컬 적용

2026-11-16 인증된 TourAPI `detailCommon2`와 `detailImage2` 응답을 확인하고, 선택한 원본 파일을 직접 열어 해당 시설의 사진임을 대조했다. 이미지별 작성자·촬영연도는 응답에 없으므로 추정하지 않고 `한국관광공사 TourAPI 제공`과 응답의 사진 제목을 표시한다.

- 웨이브파크: 공통정보의 대표 사진 `3493831_image2_1.JPG`, Type3. 서프코브 전경이다. 이미지 추가 목록은 비어 있었지만 공통정보 대표 사진과 이용조건은 확인됐다.
- 부산 루지: 이미지 목록의 `3495812_image2_1.jpg`, Type1. 루지 트랙 전경이다.
- 정동진: `4136314_image2_1.jpg`, Type1. 해안 레일바이크 사진이다.
- 플라이스테이션: `4090100_image2_1.jpg`, Type1. 이미지 목록의 첫 접수대 사진 대신 윈드터널 체험 시설을 확인할 수 있는 사진을 선택했다.
- 곤지암: 확인한 공통정보와 이미지 목록에 사진이 없어 등록하지 않는다. 다른 시설의 사진을 사용하지 않는다.

V9를 적용한 로컬 DB에서 `scripts/review-initial-place-images.sql`을 명시적으로 실행한다. 기존 수집 이력에 연결된 공개 장소에만 등록하며 기존 대표 사진을 덮어쓰지 않는다. 첫 실행 4장, 재실행 0장을 확인했다. 일반 서버 시작이나 `importTourPlaces`가 자동으로 사진을 등록하는 것은 아니다.

## 표시·장애 처리

2026-11-25에는 팀 기준 500곳 중 스케이트장, 수영장, 레일바이크 등 우선 확인한 18곳에서 14곳의 대표 사진을 추가로 선정했다. `detailCommon2`의 콘텐츠 ID·장소명·`firstimage`·`cpyrhtDivCd`를 확인하고, 원본 HTTPS 응답의 이미지 형식도 확인했다. 4곳은 허용된 원본 URL 또는 이용 유형을 확인할 수 없어 보류했다.

저장소 루트에서 초기 사진 SQL을 적용한 뒤 추가 사진 SQL을 실행한다. 공개 장소와 TourAPI 수집 출처가 일치할 때 대표 사진이 비어 있는 행에만 적용한다.

```bash
docker compose -f compose.yaml exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --user="$MYSQL_USER" --database="$MYSQL_DATABASE" --default-character-set=utf8mb4' < backend/scripts/review-initial-place-images.sql
docker compose -f compose.yaml exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --user="$MYSQL_USER" --database="$MYSQL_DATABASE" --default-character-set=utf8mb4' < backend/scripts/review-place-images-20261125.sql
```

PowerShell에서는 각 SQL을 `Get-Content -Raw -Encoding utf8`로 읽고 같은 Compose 명령에 파이프로 전달한다.

이미지 파일을 DB/S3/프런트 저장소로 복사하지 않고 확인한 원본 HTTPS URL을 참조한다. 원본을 자르거나 변환하지 않으며, 모든 사진에 `object-fit: contain`을 사용한다. Type3도 같은 방식으로 표시한다. 필터·글씨·워터마크를 사진 위에 추가하지 않는다. 원본에 포함된 표시는 유지한다.

카드와 상세 사진 아래에 제공 출처와 공공누리 유형의 링크를 표시한다. 카드의 사진 출처 링크는 선택 버튼 밖에 두어 링크/버튼 중첩을 방지한다. 로딩은 지연 처리하고 사진 URL·출처·이용조건이 빠졌거나 허용하지 않은 URL인 경우, 또는 이미지 전송이 실패한 경우 대체 일러스트를 표시한다. 허용 이미지 호스트는 `tong.visitkorea.or.kr`의 `/cms/resource/` 경로로 제한한다. 프런트에서 원본 이미지를 요청하므로 외부 제공 서버 장애의 영향을 받을 수 있다.

사진 API 동기화, 변경·삭제 감지, 관리자 사진 변경 화면과 갤러리는 이번 범위에 포함하지 않는다.

근거: [TourAPI 서비스 안내](https://www.data.go.kr/data/15101578/openapi.do), [공공누리 제1유형](https://www.kogl.or.kr/info/licenseType1.do), [공공누리 제3유형](https://www.kogl.or.kr/info/licenseType3.do).
