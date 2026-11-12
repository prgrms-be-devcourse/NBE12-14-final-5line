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
