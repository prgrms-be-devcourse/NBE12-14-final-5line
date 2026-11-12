# OUTPICK 로컬 MySQL 개발 환경 구성 결과

- 작업일: 2026-11-09
- 상태: 로컬 실행 및 테스트 검증 완료
- 목적: Docker Compose로 로컬 MySQL을 실행하고 Spring Boot 연결 및 Flyway 초기화를 확인한다.

## 구현 범위

개발용 MySQL은 프로젝트 루트의 `compose.yaml`로 관리한다. 자동 테스트는 Testcontainers가 별도 MySQL을 실행한다. 두 환경 모두 `mysql:8.4.13` 이미지를 사용한다.

JPA Entity, 도메인 테이블, 마이그레이션 SQL은 추가하지 않았다. Frontend와 AWS 인프라는 이번 작업에서 제외했다.

## 구성 파일

| 파일 | 주요 내용 |
|---|---|
| `compose.yaml` | MySQL 컨테이너, named volume, DB 접속 healthcheck, 로컬 포트 바인딩 |
| `Makefile` | 개발 DB 시작·종료·초기화 명령 |
| `.env.example` | DB 이름, 사용자명, 포트 예시와 비밀번호 입력 항목 |
| `backend/src/main/resources/application.yaml` | 기본 `local` profile, Hibernate 자동 DDL 비활성화, Flyway 활성화 |
| `backend/src/main/resources/application-local.yaml` | 환경 변수를 참조하는 DataSource 설정 |
| `backend/build.gradle.kts` | Spring Boot Testcontainers 및 MySQL·JUnit 연동 테스트 의존성 |
| `backend/src/test/java/com/outpick/BackendApplicationTests.java` | 실제 MySQL 접속과 Flyway 초기화 검증 |
| `README.md` | 터미널·IntelliJ 실행, 환경 변수 등록, 테스트 및 DB 초기화 안내 |

실제 로컬 설정은 Git에서 제외되는 루트 `.env`에 저장한다. 기존 `.gitignore`가 `.env`를 제외하고 `.env.example`을 허용하므로 별도 수정하지 않았다. 실제 비밀번호는 이 문서에 기록하지 않는다.

## 주요 결정

### Compose 위치와 데이터 관리

실행 진입점을 쉽게 찾을 수 있도록 루트에 `compose.yaml`을 배치했다. 운영 구조가 확정되지 않아 환경별 Compose 파일이나 공통 override 구조는 추가하지 않았다.

MySQL 포트는 `127.0.0.1`에만 바인딩하고 기본 포트는 `3306`이다. 데이터는 `mysql-data` named volume에 저장한다. 컨테이너를 종료하거나 재생성해도 볼륨을 삭제하지 않으면 데이터가 유지된다.

| 명령 | 동작 |
|---|---|
| `make dev-up` | MySQL을 시작하고 healthcheck 통과까지 대기 |
| `make dev-down` | 컨테이너와 네트워크 제거, 데이터 볼륨 유지 |
| `make dev-reset` | `RESET` 확인 입력 후 데이터 볼륨 삭제 및 MySQL 재시작 |

### 실행 profile과 환경 변수

`spring.profiles.default: local`을 지정했다. profile을 생략하면 `local`이 적용되고, 다른 active profile을 명시하면 기본 `local`은 적용되지 않는다. 운영용 profile은 아직 구성하지 않았다.

Compose가 읽은 `.env`는 IntelliJ나 Spring Boot에 자동 전달되지 않는다. 터미널 실행은 `.env`를 현재 셸에 불러와 환경 변수로 내보낸다. IntelliJ 실행은 `.env`의 `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_PORT` 값을 Run 설정에 등록한다.

`MYSQL_ROOT_PASSWORD`는 MySQL 컨테이너 초기화용이다. `.env`에는 필요하지만 Spring Boot의 IntelliJ 실행 설정에는 필요하지 않다. 애플리케이션은 별도의 `outpick` 계정을 사용한다.

현재 파일의 기본값은 다음과 같다. 대화에서 논의한 모든 DB 기본값 제거 또는 Compose와의 기본값 통일은 구현하지 않았다.

| 항목 | Compose | Spring `local` |
|---|---|---|
| DB 이름 | 필수 | 필수 |
| 사용자명 | 필수 | `outpick` |
| 앱 계정 비밀번호 | 필수 | 로컬용 `outpick` 기본값 존재 |
| root 비밀번호 | 필수 | 사용하지 않음 |
| 포트 | `3306` | `3306` |
| 접속 호스트 | 로컬 포트 바인딩 | `localhost` |

실행 안내는 양쪽에 동일한 실제 `.env` 값을 전달하는 방식을 기준으로 한다. Spring의 로컬 비밀번호 기본값은 실제 `.env`의 비밀번호를 대체하지 않는다. 운영 자격 증명으로 사용할 수 없다.

### Flyway와 테스트 DB

Flyway는 빈 DB에 `flyway_schema_history`를 생성한다. 마이그레이션 SQL이 없으므로 적용된 마이그레이션은 0개이며, Hibernate도 테이블을 자동 생성하지 않는다. 첫 도메인 스키마 변경 시 `V1` 마이그레이션을 추가할 예정이다.

테스트는 `@ServiceConnection`으로 Testcontainers MySQL의 접속 정보를 주입한다. 개발용 `.env`나 Compose 볼륨을 공유하지 않는다. 기본 `local`이 적용되는 경우에도 테스트 컨테이너의 접속 정보로 연결되는 것을 확인했다.

## 검증 결과

아래 결과는 이번 구현 과정에서 수행한 검증 기록이다. 이 요약 문서 작성 시 빌드와 테스트를 다시 실행하지는 않았다.

| 검증 | 결과 |
|---|---|
| `docker compose config --quiet` | Compose 설정 검증 통과 |
| `make dev-up` | MySQL healthcheck 통과 |
| `./gradlew test` | MySQL 연결·`SELECT 1`·Flyway 이력 테이블·마이그레이션 검증 성공 |
| 기본 `local` 추가 후 테스트 | 테스트 1개 성공, 실패·오류 0개 |
| `./gradlew build` | 실행 JAR 생성 및 빌드 성공 |
| Spring Boot `local` 실행 | Java 25에서 개발 DB 연결 및 앱 기동 확인 |
| `/actuator/health` | `UP` 응답 확인 |
| 개발 DB 테이블 확인 | `flyway_schema_history` 생성, 마이그레이션 0개 확인 |
| 컨테이너 종료·재생성 | 임시 테이블의 데이터 유지 확인 후 해당 테이블 제거 |
| `make dev-reset` | 취소 동작 및 확인 입력 후 볼륨 삭제·재생성·빈 DB 확인 |
| `.env` Git 제외 | `git check-ignore .env`로 확인 |
| IntelliJ 로컬 실행 | 환경 변수 등록 후 사용자가 실행 완료 확인 |

초기 검증에서는 Flyway 실행 전에 임시 테이블을 생성해 앱 기동이 거부되었다. 검증용 테이블을 제거한 뒤 빈 DB에서 Flyway 초기화와 정상 기동을 확인했다. 이를 우회하는 baseline 설정은 추가하지 않았다.

## 실행 안내와 후속 사항

상세 실행 절차는 [루트 README](../../README.md)에 기록했다. IntelliJ의 Java SDK·language level·실행 JRE는 Java 25를 기준으로 한다. 터미널에서 `.env`를 불러온 값은 해당 세션과 자식 프로세스에 전달되며 IntelliJ에는 자동 적용되지 않는다.

기존 볼륨이 있으면 `.env`의 사용자명·비밀번호·DB 이름을 바꿔도 MySQL의 기존 계정과 DB가 자동 변경되지 않는다. `dev-reset`은 데이터를 모두 삭제하므로 초기화가 필요한 경우에만 사용한다.

현재 범위에서 필수 미완료 구현은 없다. 운영 DB·profile·비밀 정보 관리, 도메인 마이그레이션은 이후 작업이다. 일반 설정과 비밀번호 기본값의 정책은 위의 실제 상태를 기준으로 추후 정리할 수 있다.

사용자가 로컬 실행과 커밋 완료를 알렸다. 에이전트는 커밋·Push를 수행하지 않았다. 이 요약 문서는 별도 변경으로 추가한다.
