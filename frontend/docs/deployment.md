# Frontend 이미지 빌드와 로컬 실행

Frontend 이미지를 빌드하고 로컬에서 화면·API 연결을 확인하는 안내다. EC2의 전체 파일 업로드·최초 배포·기존 서버 업데이트는 [배포 통합 안내](../../infra/deploy.md)를 따른다.

```text
브라우저 → Nginx ┬─ 화면·JS·CSS 요청 → 이미지에 포함된 Frontend 파일
                └─ /api/ 요청 → backend:8080
```

`frontend/Dockerfile`은 Node.js 26에서 `npm ci`와 정적 빌드를 실행한다. 최종 이미지에는 Nginx와 `out/` 전체만 담는다. EC2에는 Node.js를 설치하지 않는다. 로컬 HTTP 실행에서는 `nginx/default.conf`를 읽기 전용으로 연결한다. EC2 HTTPS 실행에서는 `nginx/https.conf`로 바꾸며 서비스 이름 `nginx`는 유지한다.

## Mac에서 이미지 빌드와 실행 확인하기

Docker Desktop을 실행하고 Node.js 26을 준비한다. `frontend/.env`에 `NEXT_PUBLIC_KAKAO_MAP_JS_KEY`를 설정한다. 개발용 `.env.local`과 별개로 Docker 빌드는 `.env`의 지도 키만 읽는다. 파일이 없거나 키가 비어 있으면 안내 후 빌드를 중단한다. 키 값은 명령에 직접 적지 않는다.

프로젝트 루트에서 이미지를 만든다.

```bash
make frontend-docker-build
```

이 명령은 `frontend/scripts/docker-build.mjs`로 환경 파일을 실행하지 않고 읽어 지도 키를 전달한다. AMD64·ARM64를 함께 빌드하고 `--load`로 로컬 Docker에 저장한다. Docker Desktop의 containerd 이미지 저장소가 필요하며, Windows는 Linux 컨테이너 모드를 사용한다. `IMAGE_TAG`를 생략하면 `v0`를 사용한다. `.dockerignore`는 개인 `.env` 파일·키 파일·기존 빌드 결과를 제외한다. 배포용 API 주소는 `/`로 고정한다. 이미지 빌드는 테스트를 실행하지 않으므로 기존 Frontend 검사와 테스트는 별도로 실행한다.

루트 `.env`가 없다면 [MySQL 실행 안내](../../README.md#mysql-실행)에 따라 작성한다. Backend 이미지는 기존 `chocobe/outpick-backend:v0`를 사용한다. 각 명령이 성공한 뒤 다음 명령을 실행한다.

```bash
docker compose -f compose.deploy.yaml config --quiet
docker compose -f compose.deploy.yaml run --rm --no-deps nginx nginx -t
docker compose -f compose.deploy.yaml up -d --wait
docker compose -f compose.deploy.yaml logs --tail 100 backend
```

`config --quiet`는 비밀번호를 포함한 설정을 출력하지 않고 검사한다. `run --rm --no-deps`는 다른 서비스를 시작하지 않고 임시 컨테이너에서 설정을 검사한 뒤 제거한다. `nginx -t`의 `test is successful`을 확인한다. `up -d --wait`는 백그라운드 실행 후 컨테이너 상태를 기다린다. Backend에는 healthcheck가 없으므로 `Started BackendApplication` 로그와 실제 API 응답으로 준비 완료를 확인한다.

브라우저에서 `http://localhost/`를 열어 장소 검색 화면과 검색 결과를 확인한다. `http://localhost/login/`을 직접 열어 로그인 폼을 확인한다. 테스트용 관리자 계정이 있다면 로그인한 뒤 `http://localhost/admin/`을 열고, 새로고침 후에도 운영 현황이 표시되는지 확인한다.

종료할 때는 다음 명령을 사용한다. MySQL 데이터 볼륨은 유지한다.

```bash
docker compose -f compose.deploy.yaml down
```

## Mac에서 Docker Hub에 업로드하기

앞 단계에서 멀티 플랫폼 빌드한 결과를 그대로 업로드하려면 다음 명령을 실행한다. 태그를 바꿨다면 동일한 태그를 지정한다. `--platform`을 지정하지 않으면 로컬에 저장한 두 플랫폼이 함께 업로드된다.

```bash
docker push chocobe/outpick-frontend:v0
```

로컬 검증 후 프로젝트 루트에서 ARM64와 AMD64 이미지를 함께 빌드해 올린다. Docker Hub 로그인이 필요하며 저장소를 Public으로 공개해야 EC2에서 로그인 없이 내려받을 수 있다.

```bash
make frontend-docker-build-push
```

이 명령도 `frontend/.env`의 지도 키를 읽어 `--build-arg NEXT_PUBLIC_KAKAO_MAP_JS_KEY`로 전달한다. Docker 명령 인자에는 이름만 지정하고 값은 자식 프로세스 환경으로 전달한다. `--platform linux/amd64,linux/arm64`는 EC2용 AMD64와 Mac용 ARM64를 지정한다. `--push`는 완성한 이미지를 Docker Hub에 업로드한다. 태그는 `IMAGE_TAG=v1`처럼 변경할 수 있다. Backend도 변경했다면 `make backend-docker-build-push`로 함께 빌드·업로드한다. 별도의 로컬 빌드나 `docker push`는 필요하지 않다.

전달한 지도 키는 빌드된 브라우저 코드에 포함된다. 키를 바꾸면 재빌드·업로드·컨테이너 교체가 필요하며 EC2의 `.env`를 바꾸는 것만으로는 반영되지 않는다. Kakao에 실제 접속 주소도 JavaScript SDK 도메인으로 등록한다. 이 키는 브라우저에 공개되며 REST API·Admin 키는 사용하지 않는다.

## EC2에 적용하기

서버에 올릴 파일 목록과 복사 명령은 [배포 안내 2번](../../infra/deploy.md#2-전체-배포-파일-업로드)에 있다. 기존 HTTPS 서버에서 이미지 교체만 한다면 [기존 EC2의 앱 업데이트](../../infra/deploy.md#기존-ec2의-앱-업데이트)를 따른다. 서버 적용 시 HTTP 전용 로컬 실행 명령을 사용하지 않는다.

참고: [Next.js 정적 배포](https://nextjs.org/docs/app/guides/static-exports), [빌드 시 공개 환경변수](https://nextjs.org/docs/app/guides/environment-variables), [Nginx 파일 탐색](https://nginx.org/en/docs/http/ngx_http_core_module.html#try_files).
