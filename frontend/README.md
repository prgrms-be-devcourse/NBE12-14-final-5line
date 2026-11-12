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

```bash
cd frontend
node --version
npm --version
npm ci
npm run dev
```

- `npm ci`: `package-lock.json`에 기록한 의존성을 `node_modules/`에 설치한다. 최초 설치 또는 잠금 파일 변경 후 실행한다.
- `npm run dev`: 개발 서버를 시작한다. 브라우저에서 <http://localhost:3000>에 접속한다. 종료는 `Ctrl+C`를 사용한다.

기본 페이지는 Backend나 MySQL을 실행하지 않아도 확인할 수 있다. `node_modules/`, `.next/`, `out/`은 Git에 포함하지 않는다.

## 코드 검사 및 정적 빌드

`frontend/`에서 실행한다.

```bash
npm run lint
npm run typecheck
npm run build
```

- `lint`: ESLint로 코드를 검사한다.
- `typecheck`: Next.js의 라우트 타입을 생성한 뒤 TypeScript 타입을 검사한다.
- `build`: `output: "export"` 설정으로 배포용 HTML·CSS·JavaScript를 `out/`에 생성한다.

`out/`은 정적 파일 서버나 CDN으로 제공한다. Next.js 실행 서버를 사용하는 `next start`는 이 구성에 사용하지 않는다.

Python 3가 설치되어 있다면 빌드 결과를 다음과 같이 확인할 수 있다. 이 명령은 로컬 확인용이며 실제 배포 설정은 아니다.

```bash
python3 -m http.server 3001 --bind 127.0.0.1 --directory out
```

브라우저에서 <http://localhost:3001>에 접속하고, 확인 후 `Ctrl+C`로 종료한다. 기본 페이지의 정적 제공만 확인하며, 실제 배포 환경의 경로·API 연결 검증은 후속 작업에서 진행한다.

### 초기 구성 검증 결과

- Node.js 26.13.0, npm 11.20.0에서 ESLint·타입 검사·정적 빌드를 통과했다.
- 개발 서버와 정적 파일 서버에서 기본 HTML·CSS·JavaScript의 HTTP 200 응답과 Tailwind 클래스 생성을 확인했다.
- 브라우저 연결 도구를 사용할 수 없어 화면 모양은 직접 확인하지 못했다.
- `npm audit`에서 린트 도구의 간접 의존성인 minimatch 관련 중간 등급 경고 7건이 확인됐다. 호환 범위 내 `npm audit fix`로는 해결되지 않았으며, 강제 버전 변경은 적용하지 않았다.

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
