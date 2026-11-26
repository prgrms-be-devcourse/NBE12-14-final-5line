# OUTPICK 프런트엔드 개발 가이드

OUTPICK은 스포츠·액티비티 장소를 지도와 목록에서 탐색하는 서비스다. 프런트엔드는 Next.js App Router, TypeScript, Tailwind CSS를 사용한다. 빌드한 정적 페이지를 제공하고, 브라우저에서 백엔드 API를 호출한다.

[로컬 실행](#로컬에서-실행하기) · [페이지와 레이아웃](#레이아웃과-페이지-추가) · [컴포넌트와 Tailwind CSS](#컴포넌트와-tailwind-css) · [API와 상태](#서버-상태-관리와-조회-화면) · [테스트](#테스트) · [빌드와 배포](#정적-빌드와-배포)

## 로컬에서 실행하기

Node.js 26.x를 사용한다. 프로젝트 기준은 Node.js 26.13.0과 npm 11.20.0이다. nvm을 쓰는 경우 `frontend/`에서 `nvm install`과 `nvm use`를 실행하면 `.nvmrc`에 지정된 버전을 사용할 수 있다. [Node 버전](.nvmrc) · [패키지 설정](package.json)

1. `frontend/.env.local`이 없으면 저장소 루트에서 `cp frontend/.env.example frontend/.env.local`을 실행한다. 파일이 이미 있으면 필요한 항목만 추가한다.
2. 실제 장소 조회나 로그인이 필요하면 백엔드와 MySQL을 실행한다. 로컬 데이터 준비 방법은 장소 API 연동 문서를 따른다. [장소 API 연동](../backend/docs/place-integration.md)
3. 저장소 루트에서 `make frontend-dev`를 실행하고 브라우저에서 <http://localhost:3000>을 연다. 명령은 필요할 때 `npm ci`를 실행한 뒤 개발 서버를 띄운다. `Ctrl+C`로 서버를 종료한다.

| 변수 | 설정 |
| --- | --- |
| `NEXT_PUBLIC_API_BASE_URL` | 로컬 기본값은 `http://localhost:8080`이다. API 함수가 `/api/v1/...` 경로를 붙인다. 배포용 Docker 이미지는 같은 서버로 요청하도록 `/`를 사용한다. |
| `NEXT_PUBLIC_KAKAO_MAP_JS_KEY` | 카카오맵 Web SDK용 JavaScript 키를 넣는다. Kakao Developers에서 지도 사용을 켜고 `http://localhost:3000`, `http://127.0.0.1:3000`과 실제 배포 출처를 필요에 따라 등록한다. |

`NEXT_PUBLIC_` 값은 브라우저에 전달된다. 비밀번호나 토큰은 여기에 저장하지 않는다. 값을 변경한 뒤 개발 서버를 다시 시작한다. 배포용 정적 파일은 빌드 시점의 설정을 사용한다. [환경변수 예제](.env.example) · [카카오 지도 Web 가이드](https://apis.map.kakao.com/web/guide/)

## 레이아웃과 페이지 추가

`src/app/layout.tsx`는 전역 글꼴과 Query Provider를 연결한다. 그 아래에서 필요한 헤더와 사이드바에 따라 페이지의 위치를 정한다. 괄호로 감싼 Route Group 이름은 URL에서 생략된다.

| 페이지 | `src/app/` 아래 위치 | 사용하는 레이아웃 |
| --- | --- | --- |
| 장소 탐색 `/` | `(service)/page.tsx` | 공통 헤더와 검색창 |
| 장소 상세 `/places/detail/` | `(service)/places/detail/page.tsx` | 공통 헤더와 상세 본문 |
| 마이페이지 `/mypage/` | `(service)/(account)/mypage/` | 공통 헤더, 사이드바, 로그인 확인 |
| 일반 회원 전용 페이지 | `(service)/(account)/mypage/(member)/` | 마이페이지 레이아웃과 일반 회원 권한 확인 |
| 관리자 페이지 `/admin/` 및 하위 | `(service)/(account)/admin/` | 공통 헤더, 사이드바, 관리자 권한 확인 |
| 로그인·회원가입·비밀번호 재설정 | `(minimal)/` | 인증 화면 |

최상위 레이아웃에서 서비스 화면과 인증 화면을 최대 1440px로 가운데 정렬한다. 상세 본문은 최대 1160px, 계정 사이드바와 본문 영역은 최대 1280px를 사용한다. [최상위 레이아웃](src/app/layout.tsx)

`(service)`의 공통 헤더는 스크롤할 때 화면 위에 남는다. 장소 탐색의 넓은 화면은 헤더와 검색 필터 아래의 공간을 목록과 지도로 나누고 왼쪽 목록만 스크롤한다. 좁은 화면에서는 하단 버튼으로 목록과 지도를 전환한다. 상세·마이페이지·관리자 페이지는 본문을 페이지 단위로 스크롤한다. [서비스 레이아웃](<src/app/(service)/layout.tsx>) · [헤더](<src/app/(service)/_components/ServiceHeader.tsx>) · [계정 레이아웃](<src/app/(service)/(account)/layout.tsx>)

### 관리자 페이지 추가하기

1. `(service)/(account)/admin/` 아래에 폴더를 만들고 `page.tsx`에 본문을 연결한다. 공통 헤더, 사이드바, 관리자 접근 확인은 상위 레이아웃에서 적용한다. [후기 신고 페이지](<src/app/(service)/(account)/admin/review-reports/page.tsx>) · [관리자 레이아웃](<src/app/(service)/(account)/admin/layout.tsx>)
2. 메뉴에서 열 수 있어야 하면 `adminNavigationItems`에 `label`, `href`, Lucide 아이콘을 등록한다. 관리자 계정 메뉴와 사이드바가 이 목록을 공유한다. 일반 회원 전용 메뉴는 `memberNavigationItems`에서 관리한다. [역할별 메뉴 목록](src/domains/member/navigation/accountNavigationItems.ts)
3. 운영자로 로그인해 메뉴 링크, 선택된 메뉴 표시, 직접 접속, 새로고침을 확인한다. 로그아웃한 상태에서는 로그인으로, 권한이 다른 계정은 마이페이지로 이동하는지 확인한다. API의 관리자 권한 검사는 백엔드에서 적용한다. [보호 페이지](src/domains/member/components/ProtectedPage.tsx)

### 정적 내보내기를 고려한 페이지 작성

`useSearchParams()`를 쓰는 클라이언트 컴포넌트는 해당 페이지에서 `Suspense`로 감싼다. 장소 상세는 정적 경로 `/places/detail/`를 제공하고 브라우저에서 `id`를 읽어 장소를 조회한다. [장소 상세의 Suspense 경계](<src/app/(service)/places/detail/page.tsx>)

## 컴포넌트와 Tailwind CSS

### 코드를 둘 위치 정하기

| 코드 | 위치 | 예시 |
| --- | --- | --- |
| 라우트와 공통 레이아웃 | `src/app/` | [서비스 레이아웃](<src/app/(service)/layout.tsx>) |
| 한 페이지에서만 쓰는 컴포넌트 | 해당 라우트의 `_components/` | [PlaceDetail](<src/app/(service)/places/detail/_components/PlaceDetail/>) |
| 여러 화면에서 쓰는 도메인 UI | `src/domains/<도메인>/components/` | [MemberProfileMenu](src/domains/member/components/MemberProfileMenu/) |
| 도메인에 의존하지 않는 공통 코드 | `src/shared/` | [공통 HTTP 클라이언트](src/shared/lib/http/) |

참조 방향은 `app → domains → shared`와 `app → shared`를 따른다. 별칭 `@/`는 `src/`를 가리킨다. `_components/`, `_hooks/`, `_providers/`는 URL을 만들지 않는 내부 폴더다.

컴포넌트에 테스트, 메뉴 데이터 또는 전용 스타일이 있으면 컴포넌트와 이름이 같은 폴더에 함께 둔다. 구현 파일 하나면 `components/` 또는 `_components/` 바로 아래에 둘 수 있다. 메뉴 데이터는 `profileMenuItems.ts`처럼 렌더링 코드와 같은 폴더의 별도 파일에 둔다. [프로필 메뉴 구성](src/domains/member/components/MemberProfileMenu/)

### 스타일 작성하기

새 UI의 배치, 간격, 글자, 색상, 반응형 동작은 TSX의 `className`에서 **Tailwind 유틸리티를 우선 사용한다**. `md:`는 넓은 화면에 적용할 스타일, `focus-visible:`은 키보드 등으로 이동했을 때 보일 포커스 스타일에 사용한다. 조건별 스타일이 필요하면 완성된 클래스 문자열을 선택한다. 예를 들어 `active ? 'bg-indigo-600 text-white' : 'bg-white text-slate-900'`처럼 작성하면 Tailwind가 두 클래스 목록을 모두 찾을 수 있다. [헤더 검색 폼](<src/app/(service)/_components/PlaceSearchForm.tsx>) · [계정 사이드바](<src/app/(service)/(account)/_components/AccountSidebar/AccountSidebar.tsx>)

```tsx
<button
  type="submit"
  disabled={isPending}
  className="w-full rounded-lg bg-[#5147ff] px-4 py-2 font-bold text-white
    focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#5147ff]
    disabled:cursor-wait disabled:opacity-60 md:w-auto"
>
  저장
</button>
```

일반 CSS는 전역 글꼴, 대체 그림, 인증 화면 애니메이션처럼 여러 규칙을 함께 읽는 편이 명확할 때 사용한다. 카카오 SDK가 삽입하는 마커나 카드의 선택·호버 조합은 컴포넌트 가까이의 CSS에서 관리한다. 스타일을 옮길 때는 좁은 화면, 키보드 포커스, 선택과 비활성 상태를 확인한다. [전역 스타일](src/app/globals.css) · [대체 그림](src/domains/place/components/PlaceArtwork/PlaceArtwork.css) · [인증 화면 애니메이션](src/domains/member/components/account.css) · [카카오 마커](src/domains/place/components/KakaoPlaceMap/KakaoPlaceMap.css) · [카드 선택 스타일](<src/app/(service)/_components/PlaceExplorer/PlaceExplorer.css>)

### 이름과 코드 스타일

- 작은 Props 타입은 컴포넌트 파일에 둔다. 도메인에서 공유하는 요청·응답 타입은 `types/*.type.ts`에 둔다. 타입 전용 import는 `import type`을 사용한다. [장소 API 타입](src/domains/place/types/placeApi.type.ts)
- 일반 문자열에는 홑따옴표, JSX 속성에는 쌍따옴표를 사용하고 공백 2칸으로 들여쓴다.
- 여러 줄 객체·배열·import/export·enum·튜플에는 마지막 쉼표를 붙인다. 함수 선언·호출의 마지막 인자에는 쉼표를 붙이지 않는다. 항목이 3개 이상이면 항목별 줄바꿈을 권장한다.
- 소스에서 사용할 아이콘은 설치된 `lucide-react`에서 가져온다. 아이콘만 표시하는 버튼이나 링크에는 목적을 설명하는 접근 가능한 이름을 붙인다. [프로필 메뉴](src/domains/member/components/MemberProfileMenu/MemberProfileMenu.tsx)

## 서버 상태 관리와 조회 화면

| 상태 | 관리 위치 | 예시 |
| --- | --- | --- |
| 서버에서 받은 데이터 | TanStack Query | 현재 회원, 장소 목록·상세, 운영 현황 |
| 공유하거나 뒤로 가기로 복원할 조건 | URL | 장소명, 지역·종목 필터, 지도 검색 영역 |
| 컴포넌트에서만 쓰는 값 | React 상태 | 제출 전 검색어, 프로필 메뉴 열림 여부 |
| 같은 탭의 상세 복귀 정보 | `sessionStorage` | 선택한 장소, 표시한 카드 수, 스크롤 위치 |

여러 화면이 UI 상태를 공유해야 할 때 Zustand 도입을 검토한다. 현재 회원과 로그인 상태는 동일한 회원 조회 캐시에서 읽는다. 서버 데이터와 URL 조건은 소유한 위치에서 읽고 다른 저장소에 중복해서 넣지 않는다.

### 조회와 저장 연결하기

1. 도메인의 `api/`에 요청 함수를 작성하고 공통 Axios 인스턴스를 사용한다. 취소할 수 있는 조회는 TanStack Query의 `signal`을 Axios까지 전달한다.
2. 도메인의 `queries/`에 Query Key 또는 `queryOptions`를 둔다. 서버 응답을 바꾸는 요청 조건은 Query Key에 포함한다. 여러 화면에서 같은 데이터를 읽을 때 정의된 키를 재사용한다. [장소 쿼리 키](src/domains/place/queries/placeQueryKeys.ts) · [관리자 장소 조회 옵션](src/domains/admin/queries/adminPlacesQuery.ts)
3. 화면에서 `useQuery` 또는 도메인 Hook으로 데이터를 읽고 로딩과 오류를 처리한다. 저장에는 `useMutation`을 사용한다. 장소 초안 등록처럼 저장 결과가 여러 화면에 반영돼야 하면 기존 키를 사용해 관련 캐시를 무효화한다. [장소 조회 Hook](src/domains/place/hooks/usePlaces.ts) · [장소 초안 저장과 캐시 갱신](src/domains/admin/components/AdminPlaces.tsx)

공통 조회는 `staleTime` 30초, `gcTime` 5분을 사용한다. 데이터는 30초 동안 최신으로 취급되고, 화면에서 사용하지 않는 캐시는 5분 뒤 제거된다. 조회 실패 자동 재시도와 창 포커스 복귀 시 재조회는 끄고, 네트워크 재연결 시 오래된 활성 조회를 다시 요청한다. 화면 동작에 필요한 경우 개별 조회에서 옵션을 정한다. 장소 목록은 상세 복귀 때 캐시가 남아 있으면 자동으로 재조회하지 않는다. [Query Client 설정](src/shared/lib/query/queryClient.ts)

서비스 헤더와 보호 페이지는 `useCurrentMember()`에서 `['member', 'me']` 캐시를 공유한다. `/api/v1/members/me`의 401은 비로그인 상태로 처리한다. 로그인, 로그아웃, 탈퇴는 이 캐시를 갱신한다. 개발 서버에서는 네트워크 탭과 TanStack Query Devtools에서 요청과 캐시를 확인할 수 있다. [회원 조회와 캐시 갱신](src/domains/member/queries/currentMemberQuery.ts) · [Query Provider](src/app/_providers/QueryProvider.tsx)

### HTTP 통신과 example API

`apiClient`는 `NEXT_PUBLIC_API_BASE_URL`, `withCredentials: true`, 10초 타임아웃을 적용한다. 도메인 요청 함수는 보통 `response.data`를 반환하고, 실패는 공통 `ApiError`로 전달한다. 화면에서는 `error.details.kind`와 HTTP 상태 같은 필요한 정보를 사용해 안내한다. [Axios 설정](src/shared/lib/http/apiClient.ts) · [오류 유형](src/shared/lib/http/apiError.type.ts)

간단한 조회는 `getExample({ id, signal })`의 `GET /api/v1/examples/{id}`와 `useExampleQuery`를 참고한다. 입력값 검증 응답은 `previewExample`의 `POST /api/v1/examples/preview`에서 확인할 수 있다. [예제 API](src/domains/example/api/exampleApi.ts) · [예제 조회 Hook](src/domains/example/hooks/useExampleQuery.ts)

### 장소 탐색과 상세 복귀

- 목록과 지도 마커는 실제 장소 검색 API의 같은 응답을 사용한다. 지도 이동·확대가 끝난 뒤 300ms에 검색하고, 동일 필터로 새 영역을 조회하는 동안 이전 카드와 마커를 표시한다.
- 검색 결과는 최대 50곳을 한 번에 받는다. 카드가 10개씩 추가로 표시될 때 API를 다시 호출하지 않는다. 결과가 50곳을 넘으면 지도 확대를 안내한다.
- 적용된 검색 조건과 지도 범위는 URL에 저장한다. 상세 링크의 `returnTo`는 탐색 경로와 조건을 보존한다. 상세에서 돌아오면 남아 있는 캐시와 `sessionStorage`를 사용해 목록 위치와 선택을 복원한다. [장소 탐색](<src/app/(service)/_components/PlaceExplorer/PlaceExplorer.tsx>) · [목록 복귀](src/domains/place/lib/placeNavigation.ts)

## 테스트

의존성을 설치한 뒤 저장소 루트에서 실행한다.

| 명령 | 확인 범위 |
| --- | --- |
| `make frontend-test` | Vitest 테스트 전체 실행 |
| `make frontend-lint` | ESLint 검사 |
| `make frontend-typecheck` | Next.js 라우트 타입 생성 및 TypeScript 검사 |
| `make frontend-build` | 정적 내보내기와 빌드 검사 |

`frontend/`에서 테스트 하나만 실행하려면 `npm run test -- src/domains/example/api/exampleApi.test.ts`를 사용한다. 반복 작업 중에는 `npm run test:watch`로 변경 시 다시 실행할 수 있다.

- API와 로직은 기본 Node 환경에서 검증한다. MSW의 `server.use()`로 응답을 등록한다. 테스트 사이에 응답이 초기화되고 등록하지 않은 요청은 실패한다. `msw.test.ts`는 `fetch`로 도구 연결을 확인하는 예제다. [테스트 설정](tests/setup.ts) · [API 테스트 예제](src/domains/example/api/exampleApi.test.ts) · [MSW 연결 테스트](tests/msw.test.ts)
- 화면 테스트는 첫 줄에 `// @vitest-environment jsdom`을 선언하고 `tests/setup-dom.ts`를 import한다. React Testing Library로 사용자에게 보이는 텍스트와 동작을 검증한다. 테스트는 대상 코드 가까이에 두고 검증 상황을 한국어로 작성한다. [화면 테스트 설정](tests/setup-dom.ts) · [관리자 접근 테스트](src/domains/admin/components/AdminAccess/AdminAccess.test.tsx)
- 브라우저에서 확인해야 할 항목은 실제 백엔드·쿠키·카카오 SDK 연동과 반응형 배치다. UI를 변경했다면 넓은 화면과 좁은 화면에서 해당 흐름을 재현한다. 권한이 관련되면 비로그인, 일반 회원, 관리자 화면에서 메뉴와 접근 동작을 각각 확인한다.

## 정적 빌드와 배포

`make frontend-build`는 `frontend/out/`에 정적 파일을 생성한다. `output: 'export'`와 `trailingSlash: true`를 사용하므로 `/places/detail/`은 `out/places/detail/index.html`로 제공된다. 호스트는 각 디렉터리의 `index.html`을 반환해야 하며, Next.js 이동에 필요한 `.txt`와 `_next/`를 포함해 `out/` 전체를 배포한다. [Next.js 설정](next.config.ts)

로컬에서 빌드 결과를 확인하려면 개발 서버를 종료하고 Python 3가 설치된 환경에서 `make frontend-preview`를 실행한다. <http://localhost:3000>에서 장소 상세와 보호 페이지를 직접 열고 새로고침한다. 이 명령은 이미 만든 파일을 제공하므로 소스나 환경 설정을 바꾸면 먼저 다시 빌드한다.

Docker 배포는 정적 파일을 Nginx 이미지에 담는다. Docker 빌드용 `frontend/.env`에 카카오맵 JavaScript 키를 설정한다. 빌드·Docker Hub 업로드·EC2 적용은 배포 문서를 따른다. [프런트엔드 배포 안내](docs/deployment.md)
