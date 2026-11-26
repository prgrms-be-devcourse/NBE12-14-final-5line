# 수집된 운영 안내 — 플라이스테이션 1차 연결

## 현재 범위

한 장소의 이용시간·휴무일·문의전화·개장 기간·이용요금을 TourAPI `detailIntro2`에서 수동 수집해 저장하고 상세에 참고 안내로 표시한다. 개장 기간(`openperiod`)과 이용요금(`usefeeleports`)은 제공처의 안내 문구다. 장소의 `operationStatus`(운영 여부)는 별도로 관리하며 TourAPI에서 갱신하지 않는다.

최초 수집 명령을 다시 실행하면 이미 저장된 운영 안내는 SKIPPED로 처리한다. 기존 운영 안내는 아래 변경 후보 확인 명령으로 비교한다.

## 로컬 실행

Java 25, 실행 중인 로컬 MySQL 및 루트 `.env`의 기존 MYSQL 설정과 `TOUR_API_KEY`(Decoding)를 사용한다.

```powershell
powershell -ExecutionPolicy Bypass -File ./backend/scripts/import-tourapi.ps1 -Task importPlaceOperatingInfo
```

맥·Linux에서는 프로젝트 루트에서 로컬용 `.env`의 환경변수를 적용한 뒤 실행한다. 기존 장소 수집이 먼저 완료되어 있어야 한다.

```bash
set -a
source .env
set +a
./backend/gradlew -p backend importPlaceOperatingInfo --no-daemon
```

공식 안내와의 대조 기록은 `backend/scripts/review-flystation-operating-info.sql`을 로컬 DB에 별도로 실행한다. 2026-11-16에 확인한 값에 대한 기록이며 서버 시작이나 수집 명령이 자동 적용하지 않는다.

플라이스테이션의 외부 콘텐츠 ID `2623831`만 호출한다. 외부 HTTP는 DB 트랜잭션·잠금 밖에서 실행한다. 기존 전송 계층의 공식 호스트 제한·리다이렉트 금지·타임아웃·응답 크기 제한·키가 포함된 예외 제거 정책을 재사용한다. 제공기관 성공 코드와 항목의 콘텐츠 ID·유형(28)을 확인하고 HTML을 일반 텍스트로 변환한다. 빈 항목은 null이며 모든 항목이 비었거나 길이 제한을 넘는 응답은 저장하지 않는다. 서버 시작과 CI에서는 외부 API를 호출하지 않는다.

V10은 `place_operating_info`를 추가한다. 장소 PK/FK와 TourAPI 수집 이력 FK로 최초 수집 장소에 연결하고, 수집 값과 수집일을 저장한다. HTTP 실패는 기존 데이터를 변경하지 않고, DB 저장 실패는 해당 트랜잭션을 롤백한다. 초기 수집은 UNVERIFIED이며 `verified_at`은 null이다. 기존 장소·사진 마이그레이션과 후기·관리자 API는 수정하지 않는다. 팀 ERD에 이 추가 테이블을 반영해야 한다.

## 운영 안내 변경 후보 확인

공개 장소 중 이미 TourAPI 운영 안내가 저장된 장소를 대상으로, 기존 DB 값과 최신 `detailIntro2` 응답의 이용시간·휴무일·문의전화·개장 기간·이용요금을 비교한다. 장소 ID 순으로 기본 20곳을 확인하며 한 번에 1~500곳을 지정할 수 있다. 비교 명령은 별도 JSON 파일을 만든다.

Windows PowerShell에서 프로젝트 루트의 `.env`와 실행 중인 로컬 DB를 사용한다.

```powershell
powershell -ExecutionPolicy Bypass -File ./backend/scripts/import-tourapi.ps1 -Task comparePlaceOperatingInfo -ReportPath "$env:TEMP/outpick-operating-review.json" -Limit 20
```

macOS 또는 Linux에서는 기존 DB 환경 변수와 TourAPI 키를 설정한 다음 실행한다.

```bash
TOUR_OPERATING_REPORT_PATH=/tmp/outpick-operating-review.json TOUR_OPERATING_REPORT_LIMIT=20 \
  ./backend/gradlew -p backend comparePlaceOperatingInfo --no-daemon
```

보고서에는 확인 대상 수, 실제 요청 수, 변경 후보와 오류 내역이 들어간다. 검수자는 `candidates`에서 장소와 변경 항목의 이전 값·새 값·출처를 확인한다. 공백과 줄바꿈만 다른 값은 같은 값으로 처리한다. 기존 값이 있는데 새 응답에서 항목이 빠지면 `MISSING_FIELDS`, 제공자가 정상 응답했지만 운영 항목이 비어 있으면 `NO_DATA`로 남긴다. 둘 다 삭제 후보로 올리지 않는다.

HTTP 429가 발생하면 후속 요청을 멈추고 그때까지의 결과를 저장한다. 다음 범위를 확인할 때는 새 출력 파일 이름을 사용하고 `lastCompletedPlaceId`를 PowerShell의 `-AfterPlaceId` 또는 환경 변수 `TOUR_OPERATING_REPORT_AFTER_ID`에 넣는다. 요청 실패는 보고서를 기록한 뒤 종료 코드 1을 반환하므로, 일반 실패 항목도 `issues`에서 확인한다.

검수 후보를 DB에 보관하려면 백엔드 시작으로 Flyway V21까지 적용한 뒤, 위 명령에 PowerShell `-SaveCandidates` 또는 macOS/Linux 환경 변수 `TOUR_OPERATING_REPORT_SAVE_CANDIDATES=true`를 추가한다. JSON은 먼저 기록하고 장소·항목별 후보를 `place_operating_change_candidate`에 저장한다.

같은 장소·항목·이전 값·새 값 조합의 대기 후보나 반려 이력이 있으면 후보 저장을 건너뛴다. 저장 직전에 공개 상태와 현재 값을 다시 확인해 비교 후 바뀐 후보는 건너뛴다. 실행 결과의 `saved`, `duplicate`, `stale`은 각각 저장·중복 또는 동일 반려 이력·기준 변경으로 제외된 항목 수다.

운영자 검수 화면은 `GET /api/v1/admin/place-operating-changes?page=0&size=20`으로 공개 장소의 미처리 후보를 장소 단위로 조회한다. `items`에는 장소 ID·이름과 해당 장소의 전체 미처리 `candidates`를 담는다. 후보마다 후보 ID, 변경 필드, 이전 값·새 값, 각각의 출처 URL, 탐지 시각을 반환한다. `size`와 `totalElements`의 단위는 장소 수다. 장소는 가장 최근인 후보의 탐지 시각순, 후보는 탐지 시각과 후보 ID의 내림차순으로 정렬한다. 운영자 세션이 필요하다.

운영자 메뉴의 **운영 정보 검수**에서 이전 값·새 값과 출처를 보고 처리한다. `PATCH /api/v1/admin/place-operating-changes/{candidateId}/resolution`에 운영자 세션과 CSRF 헤더를 보내고, 본문에 `decision`(`APPROVED` 또는 `REJECTED`)과 `reason`(최대 500자, 반려 시 필수)을 넣는다. 성공 응답은 `204 No Content`다.

승인은 공개 장소의 현재 값이 후보의 이전 값과 일치할 때 해당 항목만 갱신한다. 반려는 공개 값을 유지한다. 둘 다 결정·처리자·처리 시각·의견을 기록하며, 동일 후보를 다시 처리하려 하면 `409 Conflict`를 반환한다. 공개 값이 바뀐 후보도 승인 시 `409 Conflict`를 반환하며 사유를 남겨 반려할 수 있다.

## 상세 API와 화면

`GET /api/v1/places/{placeId}`에 다음 필드를 가진 nullable `operatingInfo`가 추가됐다.

- `operatingHours`, `restDays`, `contactPhone`: 수집한 참고 값. 제공되지 않은 개별 항목은 null이다.
- `openingPeriod`, `usageFee`: 수집한 개장 기간·이용요금. 미제공 값은 null이며, 화면에서 `—`로 표시한다. 운영 여부가 비어 있을 때도 화면에서 `—`로 표시한다.
- `sourceUrl`: 키가 포함되지 않은 공공데이터 서비스 안내 페이지.
- `collectedAt`: 최초 수집 결과가 저장된 서울 시간. 최신성이 확인된 시각이 아니다.
- `verificationStatus`: UNVERIFIED(미검수), NEEDS_REVIEW(확인 필요), VERIFIED(검수된 안내).
- `verifiedAt`: 값의 검수가 완료된 시각. VERIFIED일 때만 값이 존재하도록 DB CHECK를 적용했다. 현재 파일럿은 null이다.
- `reviewNote`, `officialSourceUrl`, `comparedAt`: 공식 안내와 대조한 결과·공식 안내 링크·대조일. 대조했다는 사실만으로 값이 검수 완료된 것으로 처리하지 않는다.

운영 안내가 없으면 ‘아직 수집된 운영 안내가 없습니다’로, 일부 항목만 비었으면 ‘제공된 정보 없음’으로 구분한다. ‘운영 정보’와 ‘운영 공지’를 분리하고, 기존 고정 문구 ‘운영시간·이용 조건·휴장 여부는 미확인’은 제거했다. 공지가 비어 있으면 우리 DB 기준 ‘등록된 운영 공지가 없습니다’로 표시한다. 운영 안내는 장소 조회에서 함께 LEFT JOIN해 상세 쿼리 수 5개를 유지한다. 비공개·검수 전 장소의 운영 안내도 공개하지 않는다.

## 2026-11-16 대조 결과

수집 응답에는 평일 10:30~19:30, 주말/공휴일 09:30~19:30, 매주 월요일 휴무, 문의전화 1855-3946이 있다. [공식 체험 안내](https://flystation.kr/usage/usage_020100.html)의 평일 예약 시간은 11:30~20:30이며, 같은 페이지 하단 운영시간도 본문과 다르다. 전화번호는 일치한다. 어느 시간이 최신인지와 월요일 실제 운영 여부는 확정하지 않는다.

이 차이는 `scripts/review-flystation-operating-info.sql`로 명시적으로 로컬에 기록한다. 해당 응답 값과 일치하고 아직 대조·검수하지 않은 행만 NEEDS_REVIEW로 변경한다. 공개 값이나 공지를 덮어쓰지 않는다. 대조일은 날짜만 확인한 기록이라 2026-11-16 00:00:00으로 저장하고 화면에는 날짜만 표시한다. 이 기록은 팀 운영자 계정의 승인 이력이 아니다. 실제 검수일은 null로 남긴다.

검증은 공급자 응답 검증·HTML 변환·결측 처리, 최초 저장·재실행 시 보존·롤백, 수집일과 검수일의 분리, 비공개 장소 접근 제한 및 화면 상태 표시를 대상으로 한다.
