# 장소 저장 모델 — ERD v3 구현 범위

## 기준과 범위

사용자가 제공한 `Copy of Copy of OUTPICK - v3.sql`을 직접 확인했다. 상세 기획·설계안은 참고 자료로 읽었으며, SQL에 없는 제안 구조를 팀의 확정 사항으로 간주하지 않았다.

이 문서는 장소 검색·상세 API의 저장 모델을 다룬다. 후속 구현한 HTTP 검색 계약은 [장소 검색 API 문서](place-search-api.md)를 참고한다. 관리자 등록 API, 공공 API 수집, 실제 장소 데이터 적재는 포함하지 않는다.

- `Region`: 상위 지역을 선택적으로 참조한다.
- `ActivityCategory` → `ActivityType`: 활동 분류와 종목.
- `Place`: 지역·이름·별칭·소개·주소·좌표·홈페이지·게시/운영 상태·버전·검토일.
- `PlaceActivity`: 장소별 제공 종목과 실내외 구분.
- `PlaceActivitySeason`: 장소별 종목에 연결된 시즌과 생성 시각.
- `SourceRecord`: 장소별 출처와 확인 필드·확인일·이용 조건 메모.
- `PlaceNotice`: 장소·출처에 연결된 공지와 유효기간.

엔티티는 `com.outpick.domain.place.entity`, 장소 저장/단건 조회용 Repository는 `com.outpick.domain.place.repository`에 있다. 모든 다대일 연관은 LAZY이며 자동 연쇄 저장/삭제를 추가하지 않았다. 등록 서비스에서는 참조 데이터부터 순서대로 저장하고 서비스 트랜잭션으로 묶어야 한다.

## 이번 구현에서 선택한 사항

다음은 ERD 원문을 그대로 실행한 결과가 아니라 구현을 위해 추가한 결정이다. PR에서 팀과 공유한다.

- PK는 MySQL `AUTO_INCREMENT`와 JPA `IDENTITY`로 생성한다. ERD 원문에는 생성 전략이 없었다.
- ERD의 필수/선택 여부와 길이·타입을 유지했다. 좌표는 `BigDecimal` ↔ `DECIMAL(10,7)`로 매핑한다.
- ERD에서 ID 컬럼으로만 표현된 관계에 실제 외래 키를 추가했다. 삭제 시 관련 정보를 자동 삭제하지 않는다.
- 장소·종목 조합, 장소별 종목·시즌 조합에 UNIQUE 제약을 추가했다.
- 공지는 같은 장소의 출처만 참조할 수 있도록 `(source_record_id, place_id)` 복합 외래 키를 사용한다.
- 위도 -90~90, 경도 -180~180을 CHECK 제약으로 검증한다. 좌표 미확인 상태의 NULL은 허용한다.
- 게시 상태·지역·이름 정렬, 종목·실내외 역방향 조회, 장소별 공지 조회용 인덱스를 추가했다. 실제 검색 쿼리가 구현되면 실행 계획을 확인해 조정한다. 좌표 공간 인덱스는 아직 추가하지 않았다.
- 시즌 생성 시각은 ERD처럼 DB의 `CURRENT_TIMESTAMP`가 기록한다. 저장 직후 엔티티 값은 refresh/재조회 전까지 NULL일 수 있다.
- 의미가 정의되지 않은 `activity_type.Field`는 생성하지 않았다.
- `BaseTimeEntity`는 테이블로 생성하지 않았다. ERD에 실제 대상 테이블과의 상속/공통 시간 정책이 정의되지 않아 각 엔티티에 생성·수정 시각을 임의로 추가하지 않았다.
- 아직 회원 엔티티가 없어 `SourceRecord.verifierId`는 nullable 숫자로 보존했다. 회원 모델 작업에서 FK와 연관관계를 결정한다.

## 확정한 장소 게시 상태

`Place.publishStatus`와 `PlaceNotice.publishStatus`는 `PlacePublishStatus`와 명시적인 JPA Converter로 저장한다. enum 선언 순서에 의존하는 ORDINAL 매핑은 사용하지 않는다.

- `0 = DRAFT`: 검수 전. 새 장소의 Java/DB 기본값이다.
- `1 = PUBLISHED`: 공개. 후속 비회원 검색·상세 API는 이 값만 조회해야 한다.
- `2 = HIDDEN`: 비공개. 공개 검색·상세에서 제외한다.

V2(장소)와 V3(공지)는 기존 NULL을 DRAFT로 전환하고 `NOT NULL DEFAULT 0` 및 허용값 CHECK를 적용한다. 기존 1·2 값은 유지하며 그 외의 값이 있으면 마이그레이션을 실패시켜 잘못된 데이터를 임의로 공개하지 않는다. 이전 파일은 변경하지 않았다. 공개 적합성 검수와 상태 변경 권한은 후속 서비스/API 작업이다. 공지의 공개·유효기간 조회 정책은 [상세 API 문서](place-detail-api.md)를 참고한다.

## 아직 결정하지 않은 사항

- **버전**: `Place.version`은 SQL의 `VARCHAR(20)`을 그대로 보존한 일반 문자열이다. JPA `@Version`이 아니며 동시 수정 충돌을 방지하지 않는다. 낙관적 잠금이 필요하면 의미·타입을 합의하고 후속 마이그레이션으로 적용한다.
- **상태/시즌 코드**: `environment_type`, `season`, `operation_status`, 출처 `type`의 코드 목록은 강제하지 않았다. 테스트의 `WINTER`, `OUTDOOR`, `OFFICIAL`은 테스트 입력이며 최종 계약이 아니다.
- **대표 사진**: 초기 모델 범위에서는 제외했으며, 후속 V9에서 별도 `place_representative_image`를 추가했다. `place_media`·`media_asset`나 후기 모델은 변경하지 않았다. [대표 사진 모델과 계약](place-images.md)을 참고한다.
- **운영시간/이용 조건**: 별도 저장 구조를 추가하지 않았다. 기존 `operation_status`와 공지만 보존한다.
- 실제 장소 공개에 필요한 이름·주소·좌표·출처 검수 규칙은 등록/공개 서비스 계층에서 후속 구현해야 한다. 현재 저장 모델만으로 공개 적합성을 보장하지 않는다.

## 마이그레이션과 검증

`src/main/resources/db/migration/V1__create_place_tables.sql`이 8개 테이블을 생성한다. 운영/개발 데이터나 샘플 장소는 삽입하지 않는다. 이미 적용·공유한 마이그레이션은 수정하지 않고 `V2__...sql` 등 후속 파일을 추가한다. 다른 팀원의 미공유 V1 작업이 있다면 머지 전에 번호와 내용을 조율한다.

```bash
./gradlew test --tests com.outpick.domain.place.PlacePersistenceTests
./gradlew build
```

Docker가 실행돼 있어야 한다. Testcontainers의 MySQL 8.4.13에서 빈 DB의 Flyway 실행, Hibernate `ddl-auto=validate`, 재실행 시 무변경, 연관 데이터 저장·재조회, 수정 반영, 중복·외래 키·좌표 제약, 실패 시 전체 롤백을 검사한다. 테스트 컨테이너는 개발 MySQL 볼륨을 공유하지 않는다.
