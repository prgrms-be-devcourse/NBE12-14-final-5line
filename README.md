# OUTPICK 로컬 개발 환경

## MySQL 실행

Java 25, Docker Compose, Make가 필요합니다. Docker를 먼저 실행합니다.

1. 프로젝트 루트에서 환경 변수 파일을 복사합니다.

   ```bash
   cp .env.example .env
   ```

2. `.env`의 `MYSQL_PASSWORD`와 `MYSQL_ROOT_PASSWORD`에 서로 다른 로컬 비밀번호를 입력합니다. `.env`는 Git에서 제외됩니다. 셸에서도 읽을 수 있도록 값은 작은따옴표로 감싸고, 값 자체에는 작은따옴표를 넣지 않습니다.
3. MySQL을 시작합니다.

   ```bash
   make compose-up
   ```

MySQL은 `127.0.0.1:3306`에 노출됩니다. 포트가 사용 중이면 `.env`의 `MYSQL_PORT`를 변경합니다. 데이터는 Compose 프로젝트의 `mysql-data` named volume에 저장됩니다.

## Spring Boot 실행

profile을 지정하지 않으면 `local`이 적용됩니다. 기본 Java가 다르면 `JAVA_HOME`을 Java 25 경로로 지정합니다.

### IntelliJ에서 실행

1. 위의 MySQL 실행 절차에 따라 `.env`를 작성하고, 프로젝트 루트에서 `make compose-up`을 실행합니다.
2. `File → Project Structure`에서 Project SDK와 language level을 Java 25로 맞춥니다. Modules의 language level도 25 또는 프로젝트 설정을 상속하도록 지정합니다.
3. `backend/src/main/java/com/outpick/BackendApplication.java`를 열고 `main` 메서드 옆 실행 버튼으로 실행 구성을 만듭니다. 환경 변수 등록 전에는 DB 연결 오류가 발생할 수 있습니다.
4. `Run → Edit Configurations`에서 `BackendApplication`을 선택합니다.
5. `Environment variables` 항목을 엽니다. 항목이 보이지 않으면 `Modify options`에서 추가합니다.
6. 다음 변수에 실제 `.env`의 값을 등록합니다. `.env`에서 값을 감싼 따옴표는 입력하지 않습니다.

   - `MYSQL_DATABASE`
   - `MYSQL_USER`
   - `MYSQL_PASSWORD`
   - `MYSQL_PORT`

7. 실행 JRE가 Java 25인지 확인합니다. Active profiles는 비워 두면 기본 `local`이 적용됩니다.
8. `Apply → OK`로 저장하고 `BackendApplication`을 실행합니다.

`MYSQL_ROOT_PASSWORD`는 MySQL 컨테이너 초기화용이므로 IntelliJ에 등록하지 않습니다. 비밀번호를 입력한 실행 구성은 `Store as project file`로 공유하지 않습니다. `.env`의 연결 정보를 바꾸면 IntelliJ의 값도 함께 변경합니다.

### 터미널에서 실행

Compose는 루트 `.env`를 자동으로 읽지만 Spring Boot는 읽지 않습니다. 프로젝트 루트의 전용 터미널에서 환경 변수를 내보낸 뒤 실행합니다.

```bash
set -a
. ./.env
set +a
cd backend
./gradlew bootRun
```

### 실행 확인

다른 환경에서는 `SPRING_PROFILES_ACTIVE`로 profile을 명시합니다. 예를 들어 `prod`를 지정하면 기본 `local` 설정은 적용되지 않습니다. 운영용 설정은 아직 추가하지 않았습니다.

애플리케이션이 시작되면 Flyway가 `flyway_schema_history`를 생성합니다. 아직 마이그레이션 SQL과 도메인 테이블은 없으므로 적용된 마이그레이션은 0개입니다. Hibernate는 테이블을 자동 생성하지 않습니다. 첫 도메인 변경 시 `backend/src/main/resources/db/migration/V1__설명.sql`을 추가합니다.

```bash
curl --fail http://localhost:8080/actuator/health
```

정상 실행 시 상태는 `UP`입니다.

## 테스트 실행

Docker가 실행된 상태에서 다음 명령을 실행합니다.

```bash
cd backend
./gradlew test
```

테스트는 Testcontainers가 별도 MySQL 컨테이너를 생성하고 접속 정보를 주입합니다. 기본 `local` profile이 적용되어도 컨테이너 접속 정보가 DataSource 설정보다 우선하므로 `.env`와 `make compose-up`은 필요하지 않습니다. 개발 DB와 볼륨을 공유하지 않으며 테스트 종료 후 컨테이너는 제거됩니다. 첫 실행에는 이미지 다운로드가 필요합니다.

## MySQL 종료 및 초기화

```bash
make compose-down
```

종료 후에도 데이터는 유지됩니다. 기존 볼륨이 있으면 `.env`의 DB 이름·사용자·비밀번호를 변경해도 MySQL 초기 설정에 반영되지 않습니다.

초기화 명령은 로컬 DB 데이터를 모두 삭제하며, 확인 문구에 `RESET`을 입력해야 실행됩니다.

```bash
make compose-reset
```

이 Compose 구성은 로컬 개발용입니다. 운영 DB와 비밀 정보 관리는 배포 구조를 정할 때 별도로 구성합니다.

## Frontend 개발 환경

Next.js·TypeScript 기반 Frontend의 Node 버전, 실행·검사·테스트·정적 빌드 방법은 [Frontend README](frontend/README.md)를 참고합니다. 공통 기반과 로컬 브라우저 검증을 마쳤으며, Zustand는 공유할 UI 상태가 확정될 때까지 보류합니다.
