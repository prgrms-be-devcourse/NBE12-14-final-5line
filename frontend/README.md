# OUTPICK Frontend

Next.js + TypeScript + Tailwind CSS 기반으로 정적 파일을 빌드한다. HTTP 통신은 Axios, 서버 상태 관리는 TanStack Query, 공유 클라이언트 상태 관리는 Zustand를 사용할 예정이다.

현재는 개발 환경과 세 가지 레이아웃 예시를 구성한 단계다. Axios·TanStack Query·Zustand 설치와 Backend 연결은 후속 작업에서 진행한다.

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

## 라우팅 및 디렉터리 구조

다음은 구현한 라우팅·레이아웃 구조와 후속 기능의 배치 예시다. `/` 진입 화면, 세 Route Group의 레이아웃·예시 페이지·전용 컴포넌트, `shared/components/layout/`은 구현했다. `_providers/`, `_hooks/`, `domains/`, 공통 통신·상태 관리 파일은 향후 배치 예시이며 아직 만들지 않았다. 실제 코드가 필요할 때 파일을 추가하고 빈 디렉터리는 미리 만들지 않는다.

```text
frontend/src/
├── app/
│   ├── layout.tsx                     # html·body·공통 Provider 연결
│   ├── globals.css
│   ├── page.tsx                       # 예시 페이지 진입 화면
│   ├── _providers/
│   │   └── QueryProvider.tsx           # 후속 TanStack Query 작업
│   ├── (minimal)/
│   │   ├── layout.tsx                 # 본문만 표시
│   │   └── minimal-example/
│   │       ├── page.tsx
│   │       └── _components/
│   │           └── MinimalExampleContent.tsx
│   ├── (main)/
│   │   ├── layout.tsx                 # MainLayout 적용
│   │   └── main-example/
│   │       ├── page.tsx
│   │       ├── _components/
│   │       │   └── MainExampleContent.tsx
│   │       └── _hooks/                # 페이지 전용 Hook이 필요할 때
│   └── (admin)/
│       ├── layout.tsx                 # AdminLayout 적용
│       └── admin-example/
│           ├── page.tsx
│           └── _components/
│               └── AdminExampleContent.tsx
├── domains/
│   └── place/                         # 실제 장소 기능 구현 시 배치 예시
│       ├── components/
│       ├── hooks/
│       │   ├── usePlacesQuery.ts
│       │   └── usePlaceQuery.ts
│       ├── api/
│       │   └── placeApi.ts
│       ├── queries/
│       │   └── placeQueryKeys.ts
│       ├── stores/
│       └── types/
└── shared/
    ├── components/
    │   └── layout/
    │       ├── MainLayout.tsx
    │       ├── AdminLayout.tsx
    │       ├── Header.tsx
    │       ├── Sidebar.tsx
    │       └── AdminSidebar.tsx
    ├── hooks/
    ├── lib/
    │   ├── http/
    │   │   └── apiClient.ts            # 후속 Axios 작업
    │   └── query/
    │       └── queryClient.ts          # 후속 TanStack Query 작업
    ├── stores/
    │   └── useSidebarStore.ts          # 후속 Zustand 작업의 배치 예시
    └── types/
```

### 코드 배치와 참조 방향

| 위치 | 담당 |
|---|---|
| `app/` | URL, 레이아웃, 페이지 구성과 앱 초기 연결 |
| 페이지의 `_components/`, `_hooks/` | 해당 페이지에서만 사용하는 컴포넌트와 Hook |
| `domains/도메인명/components/` | 여러 화면에서 사용하는 도메인 전용 UI |
| `domains/도메인명/api/` | 도메인 API 요청 함수 |
| `domains/도메인명/queries/` | 도메인의 Query Key 등 TanStack Query 관련 정의 |
| `domains/도메인명/hooks/` | 요청 함수와 Query Key를 연결하는 조회 Hook 등 |
| `domains/도메인명/stores/` | 해당 도메인에서 공유하는 클라이언트 상태 |
| `domains/도메인명/types/` | 도메인 요청·응답 등 타입 |
| `shared/` | 도메인과 무관한 공통 UI·Hook·통신 설정·Store·타입 |

참조 방향은 `app → domains → shared`와 `app → shared`로 유지한다. `domains`는 `app`을 참조하지 않고, `shared`는 `app`이나 `domains`를 참조하지 않는다.

페이지 전용 컴포넌트는 여러 도메인 기능을 조합할 수 있다. 일반 화면과 관리자 화면이 같은 장소 API를 사용해도 장소 코드는 `domains/place/`에서 관리한다. 페이지나 레이아웃 종류마다 도메인을 만들지 않는다.

Query Key는 각 Hook에 문자열로 흩어 놓지 않고 도메인의 `queries/`에서 함께 정의한다. 서버 응답은 TanStack Query가 관리하고 Zustand에 복사하지 않는다. 도메인 전용 공유 UI 상태는 해당 도메인의 `stores/`에, 여러 도메인에서 사용하는 공통 UI 상태는 `shared/stores/`에 둔다. 한 컴포넌트의 상태와 URL 검색 조건은 [상태 관리 규칙](#상태-관리-규칙)을 따른다.

### 레이아웃과 예시 URL

최상위 `app/layout.tsx`에는 모든 화면에 필요한 `html`, `body`, 공통 Provider를 둔다. 각 Route Group의 `layout.tsx`는 레이아웃 컴포넌트에 `children`을 전달하고, 실제 헤더·사이드바 배치는 `shared/components/layout/`의 컴포넌트에서 구성한다. 최소 레이아웃은 `children`만 반환한다.

`(minimal)` 같은 Route Group은 URL에 포함되지 않는다. `_components`, `_hooks`, `_providers`는 라우팅 대상에서 제외하는 내부 디렉터리다.

| 예시 URL | 레이아웃 |
|---|---|
| `/` | 예시 페이지로 이동하는 진입 화면 |
| `/minimal-example` | 본문만 표시 |
| `/main-example` | 일반 헤더 + 사이드바 |
| `/admin-example` | 관리자 헤더 + 관리자 사이드바 |

위 세 가지 레이아웃과 페이지 전용 컴포넌트를 최소한의 Tailwind CSS 스타일로 구현했다. `domains/example/`은 만들지 않는다. 관리자 예시는 화면 구성을 보여 주며 권한 검사는 포함하지 않는다.

Axios 인스턴스, Query Provider·Query Client·Query Key, Zustand Store는 각 후속 작업에서 추가한다. `domains/place/`는 향후 실제 장소 기능의 배치 예시이며 이번 예시 구현 범위에 포함하지 않는다.

### 예시 확인과 새 페이지 추가

`make frontend-dev`로 실행한 뒤 `/`에서 각 예시 링크로 이동한다. 각 주소로 직접 접속하거나 새로고침하고, 예시 목록으로 돌아오는 링크도 확인한다. 일반·관리자 사이드바는 넓은 화면에서는 왼쪽에, 좁은 화면에서는 본문 위에 배치한다.

새 페이지는 원하는 레이아웃의 Route Group 아래에 `page.tsx`를 추가한다. 페이지 전용 컴포넌트는 해당 경로의 `_components/`에 두고, 상위 그룹의 `layout.tsx`가 적용하는 헤더·사이드바를 페이지에서 다시 작성하지 않는다.

이번 구현은 ESLint·타입 검사를 통과했다. 개발 서버의 네 경로에서 HTTP 200, 레이아웃별 헤더·사이드바 포함 여부, 진입·복귀 링크를 HTML로 확인했다. 브라우저에서 링크 클릭·뒤로 가기·반응형 화면의 시각적 확인은 수행하지 않았다. 이번 변경의 정적 빌드 검증은 사용자 요청에 따라 생략했다.

### 장소 상세 URL과 메타정보

장소 상세 URL은 `/places/detail?id=123`으로 정한다. 정적 상세 페이지를 제공하고 브라우저에서 `id`를 읽어 API로 해당 장소를 조회하는 방향이다. 실제 장소 페이지와 API 연결은 담당 기능 개발에서 구현한다.

OUTPICK 공통 OG 태그를 제공하는 방향으로 두고, 장소별 OG 태그·검색엔진 노출 요구사항은 주요 기능 개발 후 팀과 논의한다. 장소별 정적 HTML 생성이나 Backend 공유 URL은 그때 검토한다.

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
