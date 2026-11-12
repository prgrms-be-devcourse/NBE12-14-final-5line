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
  "instance": "/api/places/42",
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
  "instance": "/api/places",
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
