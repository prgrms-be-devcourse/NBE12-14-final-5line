# OUTPICK 백엔드

백엔드를 로컬에서 실행하면 기본 주소는 `http://localhost:8080`입니다. MySQL과 Spring Boot 실행 절차는 [프로젝트 README](../README.md)를 참고하세요.

## 활성화된 로컬 URL

| 로컬 URL | 용도 |
|---|---|
| [`/actuator`](http://localhost:8080/actuator) | HTTP로 공개된 Actuator 엔드포인트 목록을 확인합니다. |
| [`/actuator/health`](http://localhost:8080/actuator/health) | 애플리케이션 상태를 확인합니다. 정상 기동과 DB 연결이 확인되면 `UP`을 반환합니다. |
| [`/swagger-ui.html`](http://localhost:8080/swagger-ui.html) | Swagger UI 진입점입니다. `/swagger-ui/index.html`로 이동합니다. |
| [`/v3/api-docs`](http://localhost:8080/v3/api-docs) | OpenAPI 명세를 JSON으로 제공합니다. |
| [`/v3/api-docs.yaml`](http://localhost:8080/v3/api-docs.yaml) | 같은 OpenAPI 명세를 YAML로 제공합니다. |

## API 성공 응답

본문이 있는 성공 응답은 각 API의 DTO를 JSON 본문으로 직접 반환합니다. 공통 `data` 래퍼는 사용하지 않습니다.

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

페이지 목록은 항목과 페이지 정보를 하나의 DTO로 반환합니다. 아래는 페이지 번호를 사용하는 목록의 예시입니다.

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
