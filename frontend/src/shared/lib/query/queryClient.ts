import { environmentManager, QueryClient } from '@tanstack/react-query';

/** 앱의 공통 조회 정책을 적용한 클라이언트를 만듭니다. 개별 조회에서 정책을 재정의할 수 있습니다. */
export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        gcTime: 5 * 60_000,
        retry: false,
        refetchOnWindowFocus: false,
        refetchOnReconnect: true,
      },
    },
  });
}

let browserQueryClient: QueryClient | undefined;

/** 서버 렌더링에서는 클라이언트를 분리하고 브라우저에서는 재렌더링·페이지 이동에도 캐시를 유지합니다. */
export function getQueryClient() {
  if (environmentManager.isServer()) {
    return createQueryClient();
  }

  browserQueryClient ??= createQueryClient();
  return browserQueryClient;
}
