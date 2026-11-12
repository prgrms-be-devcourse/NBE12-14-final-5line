# OUTPICK 백엔드

백엔드를 로컬에서 실행하면 기본 주소는 `http://localhost:8080`입니다. 

MySQL과 Spring Boot 실행 절차는 [프로젝트 README](../README.md)를 참고하세요.

## 활성화된 로컬 URL

| 로컬 URL | 용도 |
|---|---|
| [`/actuator`](http://localhost:8080/actuator) | HTTP로 공개된 Actuator 엔드포인트 목록을 확인합니다. |
| [`/actuator/health`](http://localhost:8080/actuator/health) | 애플리케이션 상태를 확인합니다. 정상 기동과 DB 연결이 확인되면 `UP`을 반환합니다. |
| [`/swagger-ui.html`](http://localhost:8080/swagger-ui.html) | Swagger UI 진입점입니다. `/swagger-ui/index.html`로 이동합니다. |
| [`/v3/api-docs`](http://localhost:8080/v3/api-docs) | OpenAPI 명세를 JSON으로 제공합니다. |
| [`/v3/api-docs.yaml`](http://localhost:8080/v3/api-docs.yaml) | 같은 OpenAPI 명세를 YAML로 제공합니다. |

## 도메인 API 공통 경로

`com.outpick.domain` 아래의 `@RestController`에는 Spring MVC가 `/api/v1`을 공통으로 붙입니다. 컨트롤러에는 `@RequestMapping("/examples")`처럼 리소스 경로만 작성합니다. 실제 요청 주소는 `/api/v1/examples/1`입니다. 접두사를 컨트롤러마다 중복 작성하지 않습니다.

공통 접두사는 [`WebConfig.API_PREFIX`](src/main/java/com/outpick/global/config/WebConfig.java)에서 관리합니다. 실행 환경별 설정이 아니라 클라이언트와 공유하는 API 계약입니다. 이 값을 바꾸면 전체 도메인 API의 주소가 바뀌므로, 향후 v1·v2를 동시에 제공하려면 별도의 버전 구성이 필요합니다.

Swagger UI가 안내하는 도메인 API도 같은 접두사를 사용합니다. Swagger 문서(`/v3/api-docs`), Swagger UI, Actuator는 도메인 컨트롤러가 아니므로 기존 경로를 유지합니다. Prometheus도 활성화할 경우 `/actuator/prometheus`를 사용합니다. 이번 작업에서 지표 공개 설정을 추가한 것은 아닙니다.

## 환경별 CORS 정책

[`CorsProperties`](src/main/java/com/outpick/global/config/CorsProperties.java)가 `app.cors.allowed-origins`를 읽고 `WebConfig`가 `/api/v1/**`에 적용합니다. 허용 출처는 현재 `application-local.yaml`에만 등록했습니다.

```yaml
app:
  cors:
    allowed-origins:
      - http://localhost:3000
```

- 정확히 등록한 출처에 쿠키 등 자격 증명 전송을 허용합니다. 허용 출처에 `*`는 사용하지 않습니다.
- 메서드는 `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`, 요청 헤더는 `Content-Type`, `Authorization`을 허용합니다.
- 포트가 다르거나 `127.0.0.1`을 사용하면 다른 출처입니다. 필요한 주소를 해당 환경의 목록에 명시적으로 추가합니다.
- 다른 프로필에서는 허용 출처가 기본적으로 비어 있습니다. 필요한 출처를 해당 환경에서 별도로 설정해야 합니다.
- 허용 출처의 정상 응답과 컨트롤러의 ProblemDetail·Validation 오류 응답에 CORS 헤더를 제공합니다. CORS 정책 자체가 거부한 요청은 Spring의 403 응답으로 처리됩니다.

CORS는 브라우저의 교차 출처 접근 정책이며 인증·권한 검사나 CSRF 방어를 대신하지 않습니다. 서버 간 호출과 Prometheus 수집은 브라우저의 CORS 제한을 받지 않습니다. Swagger UI가 Backend와 같은 출처에서 실행되면 별도 Frontend 출처 허용 없이 호출할 수 있습니다.

Spring Security 도입 시 Security 필터에서도 이 MVC CORS 정책을 사용하도록 연결해야 합니다. 허용 메서드·헤더 추가는 인증·CSRF 정책을 정할 때 함께 검토합니다. 이번에는 인증·CSRF 설정을 변경하지 않았습니다.

로컬 Backend 실행 후 다음 요청으로 응답 헤더를 확인할 수 있습니다.

```bash
# 성공 응답의 출처·자격 증명 허용 헤더 확인
curl -i http://localhost:8080/api/v1/examples/1 \
  -H 'Origin: http://localhost:3000'

# JSON POST 전 사전 요청 확인
curl -i -X OPTIONS http://localhost:8080/api/v1/examples/preview \
  -H 'Origin: http://localhost:3000' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: content-type,authorization'
```

curl은 브라우저처럼 CORS를 강제하지 않습니다. 이 요청은 서버가 반환하는 헤더 확인용입니다. 사용자가 Frontend에서 ID 1 성공·ID 2 업무 오류 응답을 확인했습니다. 인증 쿠키 전송 검증은 별도입니다.

[`WebConfigTests`](src/test/java/com/outpick/global/config/WebConfigTests.java)와 [`WebConfigWithoutOriginsTests`](src/test/java/com/outpick/global/config/WebConfigWithoutOriginsTests.java)는 실제 MVC에서 허용·거부 출처, 오류 응답, 사전 요청과 v1 경로를 검증합니다. [`BackendApplicationTests`](src/test/java/com/outpick/BackendApplicationTests.java)는 Testcontainers MySQL과 전체 애플리케이션에서 OpenAPI·Swagger·Actuator 경로도 확인합니다.

참고: [Spring MVC CORS 공식 문서](https://docs.spring.io/spring-framework/reference/web/webmvc-cors.html).

## API 성공 응답

본문이 있는 성공 응답은 각 API의 DTO를 JSON 본문으로 직접 반환합니다. 

공통 `data` 래퍼는 사용하지 않습니다.

- 조회는 `200 OK`와 단건 또는 목록 DTO를 반환합니다.
- 생성은 `201 Created`를 반환합니다. 생성한 리소스를 조회할 URL이 있으면 `Location` 헤더에 담습니다.
- 본문이 필요 없는 성공 응답은 `204 No Content`를 반환합니다.

단건 조회 응답 예시입니다.

```json
{
  "id": 42,
  "name": "한강 카약 체험장"
}
```

페이지 목록은 항목과 페이지 정보를 하나의 DTO로 반환합니다. 

아래는 페이지 번호를 사용하는 목록의 예시입니다.

```json
{
  "items": [
    { "id": 42, "name": "한강 카약 체험장" }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

## 공통 오류 응답

Spring MVC에서 발생한 오류는 RFC 9457의 `ProblemDetail` 형식으로 반환합니다. 

응답의 Content-Type은 `application/problem+json`입니다. 

HTTP 상태 코드와 본문의 `status`는 같은 값을 사용합니다.

다음은 장소를 찾지 못한 경우의 `404 Not Found` 응답 예시입니다.

```json
{
  "title": "Not Found",
  "status": 404,
  "detail": "장소를 찾을 수 없습니다.",
  "instance": "/api/v1/places/42",
  "code": "PLACE_NOT_FOUND",
  "requestId": "c187928f-5800-4d36-9f62-1ccf86445e17"
}
```

| 필드 | 용도 |
|---|---|
| `title` | HTTP 상태의 기본 제목 |
| `status` | HTTP 상태 코드 |
| `detail` | 사용자에게 공개할 오류 안내 |
| `instance` | 오류가 발생한 요청 경로 |
| `code` | 프론트가 오류를 구분할 때 사용하는 코드 |
| `requestId` | 같은 요청의 서버 로그를 찾는 식별자 |

`type`의 기본값은 `about:blank`이며 현재 JSON 직렬화에서는 생략됩니다. 

`code`와 `requestId`는 OUTPICK이 추가한 확장 필드입니다.

### 도메인 오류 사용법

각 도메인은 enum으로 오류 목록을 정의합니다.

각 요소는 HTTP 상태, 오류 코드, 공개할 안내 문구를 담은 `global.error.ErrorCode` record를 보관합니다.

아래 코드는 장소 도메인에 적용할 예시입니다.

```java
@Getter
public enum PlaceErrorCode {
    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "장소를 찾을 수 없습니다."),
    PLACE_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 등록된 장소입니다.");

    private final ErrorCode errorCode;

    PlaceErrorCode(HttpStatus status, String message) {
        this.errorCode = new ErrorCode(status, name(), message);
    }
}
```

Lombok의 `@Getter`가 `getErrorCode()`를 생성합니다.

새 오류는 enum에 상태와 문구를 추가하고, 요소 이름을 오류 코드로 사용합니다.

요소 이름은 프론트의 오류 분기에 사용되므로 변경 시 연동을 함께 확인합니다.

기능 코드에서는 `throw new BusinessException(PlaceErrorCode.PLACE_NOT_FOUND.getErrorCode());`처럼 record를 전달합니다.

`GlobalExceptionHandler`가 해당 오류를 공통 응답으로 변환합니다.

안내 문구에는 클라이언트에 공개할 내용을 사용합니다.

<br />

Spring MVC 오류는 상태 이름을 코드로 사용합니다. 

잘못된 JSON은 `400`·`BAD_REQUEST`, 없는 경로는 `404`·`NOT_FOUND`, 지원하지 않는 HTTP 메서드는 `405`·`METHOD_NOT_ALLOWED`를 반환합니다.

필요한 `Allow` 등의 HTTP 헤더도 유지합니다.

예상하지 못한 예외는 `500`·`INTERNAL_SERVER_ERROR`와 공통 안내 문구로 반환하고, 원인과 스택 트레이스는 서버 로그에 기록합니다.

### 요청 ID와 로그 확인

`RequestIdFilter`가 요청마다 UUID를 생성하고 `X-Request-Id` 응답 헤더에 담습니다. 

오류 본문의 `requestId`도 같은 값을 사용합니다. 

요청 처리 중 MDC에 저장한 ID는 로그의 `[requestId=...]`에 표시됩니다. 

오류를 조사할 때 응답에서 받은 ID를 서버 로그에서 검색합니다.

<br />

각 요청의 ID는 서버에서 생성합니다. 
비동기 재디스패치에는 같은 ID를 사용하며, 별도 작업 스레드의 로그에 MDC를 전파하는 기능은 추후 비동기 실행 구성 시 검토합니다.

### 테스트로 사용 예시 확인

[`GlobalExceptionHandlerTests`](src/test/java/com/outpick/global/error/GlobalExceptionHandlerTests.java)의 테스트 전용 컨트롤러와 오류 코드를 참고하세요. 

도메인 오류, 잘못된 JSON, 없는 경로, HTTP 메서드 오류, 예상치 못한 예외, 성공 응답의 요청 ID를 검증합니다.

```bash
# backend 디렉터리에서 실행
./gradlew test --tests com.outpick.global.error.GlobalExceptionHandlerTests
```

이 MVC 테스트는 DB 없이 실행됩니다. 

## 요청 값 검증

요청 DTO에는 Jakarta Validation 제약을 선언하고, 컨트롤러의 요청 본문에는 `@Valid`를 붙입니다.

`GlobalExceptionHandler`가 검증 실패를 `400`·`VALIDATION_FAILED` 응답으로 변환합니다.

```java
public record CreatePlaceRequest(
    @NotBlank(message = "장소 이름을 입력해 주세요.") String name
) {}
```

컨트롤러에서는 `@Valid @RequestBody CreatePlaceRequest request`로 받습니다. 

쿼리·경로 파라미터에는 `@Min`, `@Max` 등의 제약을 직접 선언합니다. 

예를 들어 `@RequestParam("size") @Max(value = 50, message = "페이지 크기는 50 이하여야 합니다.") int pageSize`처럼 작성합니다. 

오류에는 Java 변수명인 `pageSize` 대신 공개 파라미터명인 `size`를 담습니다.

<br />

컨트롤러 클래스에 `@Validated`를 추가하면 AOP 방식의 메서드 검증으로 처리 경로가 달라집니다. 

현재 공통 처리기는 Spring MVC 내장 메서드 검증을 사용하므로 컨트롤러 클래스의 `@Validated` 없이 사용합니다.

<br />

검증 실패 응답은 기존 ProblemDetail 필드에 `fieldErrors`를 추가합니다.

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "요청 값을 확인해 주세요.",
  "instance": "/api/v1/places",
  "code": "VALIDATION_FAILED",
  "requestId": "c187928f-5800-4d36-9f62-1ccf86445e17",
  "fieldErrors": [
    {
      "field": "name",
      "message": "장소 이름을 입력해 주세요."
    }
  ]
}
```

- `field`는 요청 필드 경로나 쿼리·경로 파라미터 이름입니다.
- 중첩 DTO와 배열은 `@Valid`로 함께 검증하면 `address.city`, `photos[0].url`처럼 위치를 반환합니다.
- 같은 필드의 여러 제약이 실패하면 오류를 각각 담습니다. 배열 순서는 보장하지 않으며 프론트는 `field`로 입력란과 연결합니다.
- 오류 객체에는 `field`와 `message`만 담습니다. 제약의 안내 문구는 클라이언트에 공개할 내용으로 작성하고 비밀번호 등 실제 입력값을 포함하지 않습니다.
- JSON 문법 오류, 필수 파라미터 누락, 숫자 변환 실패는 기존 `BAD_REQUEST` 응답으로 처리합니다. 필드별 제약 검증은 `VALIDATION_FAILED`로 구분합니다.

[`ValidationExceptionHandlerTests`](src/test/java/com/outpick/global/error/ValidationExceptionHandlerTests.java)에 DTO·파라미터 제약 선언과 정상·오류 요청 예시가 있습니다. 중첩 경로, 한 필드의 여러 오류, 비밀번호 입력값 제외, 반환값 검증 실패의 `500` 응답도 확인합니다.

```bash
# backend 디렉터리에서 실행
./gradlew test --tests com.outpick.global.error.ValidationExceptionHandlerTests
```

[Spring MVC 공식 검증 문서](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html)

[Spring MVC 공식 오류 응답 문서](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html)

## 예시 도메인과 패키지 구조

`com.outpick.domain.example`은 도메인 안에 계층별 패키지를 두는 구조와 공통 기능 사용법을 보여 줍니다.

```text
domain/example/
├── controller/ExampleController.java
├── service/ExampleService.java
├── dto/
│   ├── ExampleCreateRequest.java
│   └── ExampleResponse.java
└── exception/ExampleErrorCode.java
```

| 패키지 | 예시에서의 역할 |
|---|---|
| `controller` | HTTP 요청을 받고 입력 검증 후 서비스를 호출해 응답 DTO 반환 |
| `service` | 고정 예시 조회와 미리보기 처리, 상황에 맞는 도메인 예외 발생 |
| `dto` | 입력 필드 제약과 응답 데이터 정의 |
| `exception` | 도메인별 오류 목록과 공통 `ErrorCode` record 보관 |

`global`에는 여러 도메인이 사용하는 예외 처리·검증 오류 응답·요청 ID 필터가 있습니다. 예시 도메인은 이 공통 코드를 사용합니다.

### 예시 API 호출

앱을 실행한 뒤 다음 API를 호출하거나 Swagger UI에서 실행할 수 있습니다. 예시 서비스는 DB 접근 없이 고정 데이터와 입력값으로 응답을 만듭니다. 앱 기동에는 기존 MySQL 실행 설정이 필요합니다.

| API | 동작 |
|---|---|
| `GET /api/v1/examples/1` | 고정 예시를 `200`과 DTO 본문으로 반환 |
| `GET /api/v1/examples/2` | 서비스에서 `BusinessException` 발생, 공통 처리기가 `404`·`EXAMPLE_NOT_FOUND` 반환 |
| `GET /api/v1/examples/0` | 양수 ID 제약에 실패해 `400`·`VALIDATION_FAILED`와 `id` 오류 반환 |
| `POST /api/v1/examples/preview` | 입력 필드를 검증하고 미리보기 DTO를 `200`으로 반환 |

미리보기는 데이터를 저장하지 않으므로 이후 조회 결과는 고정 예시를 유지합니다. 생성 요청 DTO의 필드 제약을 확인할 수 있는 API입니다.

```bash
# 정상 조회
curl -i http://localhost:8080/api/v1/examples/1

# 도메인 오류
curl -i http://localhost:8080/api/v1/examples/2

# 정상 미리보기
curl -i http://localhost:8080/api/v1/examples/preview \
  -H 'Content-Type: application/json' \
  -d '{"name":"예시 액티비티","participantCount":3}'

# 필수 필드 오류: 이름 공백, 인원수 누락
curl -i http://localhost:8080/api/v1/examples/preview \
  -H 'Content-Type: application/json' \
  -d '{"name":" "}'
```

정상 조회와 미리보기의 응답 본문은 다음 형식입니다.

```json
{
  "name": "예시 액티비티",
  "participantCount": 3
}
```

`ExampleCreateRequest`는 이름의 `@NotBlank`·`@Size(max = 30)`과 인원수의 `@NotNull`·`@Min(1)`·`@Max(10)`을 보여 줍니다. 인원수는 `Integer`여서 누락을 `null`로 받아 필수값 제약으로 처리할 수 있습니다. 필드 오류는 기존 `fieldErrors` 응답으로 반환합니다.

[`ExampleControllerTests`](src/test/java/com/outpick/domain/example/ExampleControllerTests.java)는 실제 컨트롤러와 서비스를 연결하고 공통 예외 처리기·필터를 사용합니다. DB 없이 예시 API의 정상 응답, 범위의 경계, 필수값·길이·숫자 범위 검증, 도메인 오류를 확인합니다.

```bash
# backend 디렉터리에서 실행
./gradlew test --tests com.outpick.domain.example.ExampleControllerTests
```
