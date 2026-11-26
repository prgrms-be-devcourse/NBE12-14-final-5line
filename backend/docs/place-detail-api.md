# 공개 장소 상세 API

## 주소와 접근 범위

`GET /api/v1/places/{placeId}`는 인증 없이 공개 장소 한 곳을 조회한다. `publish_status=1(PUBLISHED)`만 조회하며 검수 전·비공개·존재하지 않는 장소는 동일한 `404 PLACE_NOT_FOUND`로 응답한다. 0 이하 ID는 입력 검증 오류이고 숫자로 변환할 수 없는 ID도 공통 400 응답이다. 오류는 `application/problem+json`이며 `requestId`를 포함한다.

기존 장소 상세 명세의 필드 이름을 사용하고 엔티티를 직접 직렬화하지 않는다. 공개 DTO에 저장 버전, 검토자 ID, 이용 조건 내부 메모를 넣지 않는다.

## 응답 필드

- 기본 정보: `id`, `name`, `nameAlias`, `description`, `address`, `latitude`, `longitude`, `homepage`, `operationStatus`, `reviewedAt`.
- `operatingInfo`: 별도로 수집한 참고 운영 안내 또는 null. 이용시간·휴무일·문의전화, 수집일, 검수 상태·검수일 및 공식 대조 메모를 포함한다. `operationStatus`나 검수된 공지와 구분하며 [수집 운영 안내 계약](place-operating-info.md)을 따른다.
- DB의 `introduce`는 응답의 `description`으로 변환한다. 미확인 기본 정보는 NULL을 유지한다. `operationStatus`를 근거 없이 영업 중으로 채우지 않는다.
- `representativeImage`: 검수된 대표 사진의 원본 URL 문자열이며 사진이 없으면 NULL이다. 출처를 나타내는 `imageAttribution`, `imageSourceUrl`, `imageLicense`가 함께 반환된다. [대표 사진 저장·표시 정책](place-images.md)을 참고한다.
- `activities`: `placeActivityId`, `activityTypeId`, `activityTypeName`, `environmentType`, `seasons` 목록. 시즌은 장소 전체가 아닌 해당 장소별 종목에 묶는다. 활동은 연결 ID 오름차순, 시즌은 DB 문자열 오름차순으로 정렬한다.
- `sources`: 확인일(`verifiedAt`)이 있는 해당 장소의 출처만 반환한다. 공지 전용 출처(`verifiedFields: "notice"`)는 장소 상세에 표시되는 공지가 참조할 때만 반환한다. `sourceRecordId`, `type`, `url`, `verifiedFields`, `originUpdatedAt`, `verifiedAt`을 포함한다. 확인일 내림차순, 같은 시각은 ID 내림차순이다. 내부 필드인 `verifierId`, `usageTermsMemo`, `originId`는 제외한다.
- `notices`: 공개 상태이며 유효기간 안에 있고 출처 확인일이 있는 해당 장소의 공지만 반환한다. `placeNoticeId`, `title`, `content`, `validFrom`, `validUntil`, `sourceRecordId`를 포함한다. 시작 시각 내림차순, 같은 시각은 ID 내림차순이다.

활동·시즌·공지·출처가 없으면 빈 배열을 반환한다. 대표 사진·운영시간·이용 조건 중 확보하지 못한 정보를 만들어 반환하지 않는다. 장소 기본 정보가 NULL이어도 저장된 공개 상태만으로 조회 가능한 현재 한계는 등록/공개 검수 서비스에서 후속 보완해야 한다.

## 공지 상태와 시각 정책

사용자와 합의하여 공지도 장소와 같은 `0=DRAFT(기본값)`, `1=PUBLISHED`, `2=HIDDEN`으로 정했다. 기존 `PlacePublishStatus`와 Converter를 공지에도 적용한다. V3는 기존 공지 상태 NULL을 0으로 전환하고 `NOT NULL DEFAULT 0` 및 0·1·2 CHECK를 추가한다. 이전 마이그레이션은 수정하지 않았다.

다음은 이번 구현에서 구체화한 상세 조회 정책이다.

- 출처 확인일이 없으면 공개 응답의 출처/관련 공지에서 제외한다.
- 공지 유효기간은 `[validFrom, validUntil)`이다. 시작 시각은 포함하고 종료 시각은 제외한다. NULL 경계는 제한이 없다.
- DATETIME 공지 시각은 `Asia/Seoul`을 기준으로 비교한다. `placeClock`을 주입해 배포 서버의 기본 시간대나 테스트 실행 시각에 의존하지 않게 했다. 향후 등록/수집 시각도 같은 규칙으로 저장해야 한다. API의 LocalDateTime은 오프셋이 없는 한국 현지 시각이다.
- 출처 확인일은 확인 여부의 기록이며 이번 API가 외부 공식 사이트를 다시 검수하거나 데이터 정확성을 보장하는 것은 아니다.

## 응답 예시

아래 값은 설명용이며 실제 데이터로 자동 적재하지 않는다.

```json
{
  "id": 1,
  "name": "예시 장소",
  "nameAlias": null,
  "description": "확인한 장소 소개",
  "address": "확인한 주소",
  "latitude": 37.37,
  "longitude": 128.39,
  "homepage": "https://example.com",
  "operationStatus": null,
  "reviewedAt": "2026-11-13T09:00:00",
  "representativeImage": null,
  "imageAttribution": null,
  "imageSourceUrl": null,
  "imageLicense": null,
  "activities": [
    {
      "placeActivityId": 1,
      "activityTypeId": 1,
      "activityTypeName": "스키",
      "environmentType": "OUTDOOR",
      "seasons": ["WINTER"]
    }
  ],
  "notices": [],
  "sources": []
}
```

`OUTDOOR`·`WINTER` 등 기존 환경/시즌 코드 목록은 아직 최종 확정하지 않았다.

## 조회와 검증

공개 장소 확인, 활동+종목 fetch join, 전체 시즌, 공개 유효 공지 projection, 확인된 출처 projection의 총 5개 쿼리로 조회한다. 여러 컬렉션을 한꺼번에 fetch join해 활동×시즌×공지 행이 늘어나게 하지 않는다. 찾을 수 없는 장소는 첫 조회 후 종료한다.

MySQL Testcontainers의 상세 API 통합 테스트는 공개 제한, 활동별 시즌 묶음, 미확인 정보 NULL, 타 장소 데이터 분리, 내부 필드 제외, 공지 공개/유효기간 경계/출처 확인, 공통 400·404 오류를 검증한다. Hibernate 통계로 활동 3개와 23개 모두 5개 쿼리인지 확인한다. 단위 테스트는 공개 장소가 없을 때 관련 데이터를 읽지 않고 종료하는 것을 검사한다. 기존 데이터가 있는 V1에서 V2/V3로 업그레이드하는 테스트도 수행한다.

```bash
./gradlew test --tests com.outpick.domain.place.PlaceDetailApiTests --tests com.outpick.domain.place.PlaceDetailServiceTests
./gradlew build
```

Swagger에도 `/api/v1/places/{placeId}`를 노출한다. 후속 프론트 연결은 [연결 문서](place-integration.md)를 참고한다. 공공 API 수집은 아직 구현하지 않았다.
