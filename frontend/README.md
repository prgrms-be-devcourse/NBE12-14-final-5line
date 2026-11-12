# OUTPICK Frontend

Next.js + TypeScript + Tailwind CSS 기반으로 정적 파일을 빌드한다. HTTP 통신은 Axios, 서버 상태 관리는 TanStack Query, 공유 클라이언트 상태 관리는 Zustand를 사용할 예정이다.

현재는 기본 페이지와 정적 빌드 환경을 구성한 단계다. Axios·TanStack Query·Zustand 설치와 Backend 연결은 후속 작업에서 진행한다.

## 개발 환경

- Node.js: 26.13.0 기준, 26.x 사용
- npm: 11.20.0 기준
- Next.js App Router, TypeScript strict 모드
- Tailwind CSS, ESLint
- 소스 디렉터리: `src/`, import 별칭: `@/*`

Node.js 설치 방식은 자유롭게 선택한다. [공식 설치 프로그램](https://nodejs.org/en/download)으로 설치한 경우 `node --version`으로 버전을 확인한다. 기존 설치를 반드시 삭제할 필요는 없다.

nvm 사용자는 `frontend/`에서 다음 명령을 실행한다. `.nvmrc`에 기록한 버전을 설치하고 현재 터미널에서 선택한다.

```bash
nvm install
nvm use
```

`.nvmrc`와 `package.json`의 `engines`는 프로젝트 기준을 기록한다. 파일만으로 Node.js가 설치되거나 전환되지는 않는다. 기본 npm 설정에서 `engines` 불일치는 경고이므로 실행 전 버전을 확인한다.

## 설치 및 로컬 실행

저장소 루트에서 다음 명령을 실행한다.

Node.js와 npm은 먼저 설치하고 프로젝트 기준 버전을 선택한다.

```bash
cp frontend/.env.example frontend/.env.local
make frontend-dev
```

- `make frontend-dev`: 필요한 경우 `npm ci`로 패키지를 설치한 뒤 개발 서버를 시작한다. 브라우저에서 <http://localhost:3000>에 접속한다. 로그는 현재 터미널에 표시되고 `Ctrl+C`로 종료한다.
- `node_modules/`에 성공한 설치 기록이 없거나 `package.json`·`package-lock.json`·Node.js/npm 버전이 바뀌면 다시 설치한다. 기존에 직접 설치했더라도 첫 실행에는 한 번 설치한다.
- 설치에 실패하면 개발 서버를 실행하지 않는다. 설치 기록을 삭제하면 다음 실행에서 다시 설치한다.
- Node.js 버전을 자동으로 설치하거나 변경하지 않는다.

기본 페이지는 Backend나 MySQL을 실행하지 않아도 확인할 수 있다. `node_modules/`, `.next/`, `out/`은 Git에 포함하지 않는다.

### 환경변수

`frontend/.env.local`은 Next.js가 읽는 로컬 설정이며 Git에서 제외한다. 루트의 `.env`는 Docker Compose·Backend용이므로 Frontend 설정과 분리한다. 예제 복사는 최초 한 번만 하고, 기존 `.env.local`이 있다면 필요한 항목만 직접 추가한다.

| 변수 | 로컬 예제 값 | 용도 |
|---|---|---|
| `NEXT_PUBLIC_API_BASE_URL` | `http://localhost:8080` | Backend API 서버의 기본 주소. `/api` 등의 요청 경로는 후속 Axios 구성에서 연결 |

현재는 환경변수 예제만 준비했으며, 실제 API 요청에서 사용하는 코드는 Axios 작업에서 추가한다.

`NEXT_PUBLIC_` 변수는 브라우저에 공개되므로 비밀번호·토큰을 넣지 않는다. 정적 배포에서는 빌드 시 값이 고정되므로 배포 환경의 API 주소를 설정한 뒤 빌드한다. 값 변경 후 개발 서버는 다시 시작하고, 배포 파일은 다시 빌드한다. 터미널에서 이미 설정한 동일한 이름의 환경변수는 `.env.local`보다 우선한다.

## 코드 검사 및 정적 빌드

저장소 루트에서 실행한다. 이 명령들은 자동 설치를 하지 않으므로 최초에는 `make frontend-dev`로 설치를 마친 뒤 서버를 종료한다.

```bash
make frontend-lint
make frontend-typecheck
make frontend-build
```

- `frontend-lint`: ESLint로 코드를 검사한다.
- `frontend-typecheck`: Next.js의 라우트 타입을 생성한 뒤 TypeScript 타입을 검사한다.
- `frontend-build`: `output: "export"` 설정으로 배포용 HTML·CSS·JavaScript를 `frontend/out/`에 생성한다.

직접 npm을 사용하려면 `frontend/`에서 `npm ci`, `npm run dev`, `npm run lint`, `npm run typecheck`, `npm run build`를 실행할 수 있다. 새 라이브러리를 추가하는 담당자는 `npm install 패키지명`을 실행하고 `package.json`과 `package-lock.json`을 함께 공유한다.

`out/`은 정적 파일 서버나 CDN으로 제공한다. Next.js 실행 서버를 사용하는 `next start`는 이 구성에 사용하지 않는다.

Python 3가 설치되어 있다면 빌드 결과를 다음과 같이 확인할 수 있다. 이 명령은 로컬 확인용이며 실제 배포 설정은 아니다.

```bash
python3 -m http.server 3001 --bind 127.0.0.1 --directory frontend/out
```

브라우저에서 <http://localhost:3001>에 접속하고, 확인 후 `Ctrl+C`로 종료한다. 기본 페이지의 정적 제공만 확인하며, 실제 배포 환경의 경로·API 연결 검증은 후속 작업에서 진행한다.

### 초기 구성 검증 결과

- Node.js 26.13.0, npm 11.20.0에서 ESLint·타입 검사·정적 빌드를 통과했다.
- 개발 서버와 정적 파일 서버에서 기본 HTML·CSS·JavaScript의 HTTP 200 응답과 Tailwind 클래스 생성을 확인했다.
- 브라우저 연결 도구를 사용할 수 없어 화면 모양은 직접 확인하지 못했다.
- `npm audit`에서 린트 도구의 간접 의존성인 minimatch 관련 중간 등급 경고 7건이 확인됐다. 호환 범위 내 `npm audit fix`로는 해결되지 않았으며, 강제 버전 변경은 적용하지 않았다.

## 코드 스타일 규칙

Next.js 권장 ESLint 설정에 `@stylistic/eslint-plugin` 5.10.0의 스타일 규칙을 추가한다.

| 항목 | 규칙 |
|---|---|
| 일반 문자열 | 홑따옴표 사용. 문자열 안의 홑따옴표를 이스케이프해야 하는 경우 쌍따옴표 허용 |
| JSX 태그 속성 | 쌍따옴표 사용 |
| 들여쓰기 | 공백 2칸 |
| 여러 줄 객체·배열·import/export | 마지막 항목에 쉼표 사용 |
| 여러 줄 enum·튜플 | 마지막 항목에 쉼표 사용 |
| 함수 선언·호출 | 마지막 인자에 쉼표 사용하지 않음 |

JSON은 문법에 따라 쌍따옴표를 사용한다. 구조분해의 마지막 rest 요소처럼 문법상 쉼표를 허용하지 않는 경우는 제외한다.

`frontend/`에서 검사하거나 자동 수정한다.

```bash
npm run lint
npm run lint:fix
```

함수 인자·객체·배열·구조분해는 항목이 3개 이상이면 항목별 줄바꿈을 작성 규칙으로 권장한다.

1~2개는 한 줄과 여러 줄 모두 허용한다.

설치된 안정 패키지에서도 `list-style` 규칙 자체는 실험 기능으로 표시되어 있어 자동 강제는 보류한다. 

향후 CI에 적용할 검사의 안정성을 고려한 결정이며, 규칙이 안정화되면 다시 검토한다.

## 상태 관리 규칙

상태의 성격과 사용 범위에 따라 관리 위치를 정한다.

| 상태 | 관리 위치 | OUTPICK 예시 |
|---|---|---|
| 서버에서 조회하고 갱신하는 데이터 | TanStack Query | 장소 목록·상세, 관심 장소 목록, 로그인한 사용자 정보 |
| 링크 공유·새로고침·뒤로 가기로 복원할 검색 조건 | URL 쿼리 | 검색어, 지역·종목 필터, 페이지 번호 |
| 여러 컴포넌트나 페이지가 공유하는 클라이언트 상태 | Zustand | 공통 패널의 열림 상태, 여러 페이지에 걸친 임시 입력값 |
| 한 컴포넌트 안에서 사용하는 상태 | React의 useState 또는 useReducer | 입력값, 해당 컴포넌트의 펼침 여부 |

표의 예시는 관리 위치를 설명하기 위한 것으로, 해당 기능의 구현을 확정한 것은 아니다.

### 서버 데이터는 TanStack Query에서 공유한다

- 서버 응답을 Zustand에 복사해서 별도로 관리하지 않는다. 같은 데이터의 갱신 위치가 둘로 나뉘는 것을 피한다.
- 헤더와 마이페이지에서 로그인한 사용자 정보를 함께 사용한다면 동일한 사용자 조회 쿼리 키를 사용한다.
- 데이터 변경 후에는 관련 쿼리의 캐시 갱신 또는 무효화 정책을 적용한다. 구체적인 정책은 TanStack Query 구성 단계에서 정한다.
- Axios는 HTTP 요청과 공통 오류 처리를 담당한다. 서버 데이터 캐시와 조회 상태는 TanStack Query에서 관리한다.

### 검색 조건은 URL을 기준으로 관리한다

- 공유하거나 복원해야 하는 검색어·필터·페이지 번호는 URL 쿼리에서 읽는다.
- 동일한 검색 조건을 Zustand에도 저장해 서로 동기화하는 구조는 피한다.
- 검색 실행 전의 임시 입력값은 컴포넌트 상태로 관리할 수 있다. 검색을 적용하면 URL을 갱신한다.

### 공유 클라이언트 상태는 Zustand에서 관리한다

- 서버 데이터와 URL로 관리할 상태를 제외하고, 여러 컴포넌트나 페이지가 함께 읽거나 변경하는 상태를 저장한다.
- 한 컴포넌트에서만 사용하는 상태는 해당 컴포넌트에 둔다.
- Store는 역할별로 구분하고, 기능 개발에 필요한 상태와 변경 함수를 추가한다.
- 로그인 여부를 별도 boolean 값으로 저장해 사용자 조회 결과와 중복 관리하지 않는다.

### 새로고침 후 유지 여부는 별도로 정한다

- Zustand를 사용한다는 이유만으로 새로고침 후 상태가 유지되지는 않는다.
- 브라우저 저장소에 저장할지는 상태별로 논의한다. 저장 기간과 초기화 시점도 함께 정한다.
- HttpOnly 인증 쿠키의 JWT를 Zustand나 브라우저 저장소에 복사하지 않는다. 쿠키 저장과 전송은 브라우저가 담당한다.

## 작업 계획

작업 순서와 진행 상태는 [사전 준비 계획서의 3.7 Frontend](../agents/00-4차%20팀프로젝트%20사전%20준비%20계획.md#37-frontend)에서 관리한다.
