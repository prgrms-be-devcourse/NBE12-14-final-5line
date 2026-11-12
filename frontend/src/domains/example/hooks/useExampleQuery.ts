'use client';

import { useQuery } from '@tanstack/react-query';
import { getExample } from '../api/exampleApi';
import { exampleQueryKeys } from '../queries/exampleQueryKeys';

/** ID별 캐시로 예시를 조회합니다. 사용이 끝나 취소된 요청은 Axios에도 AbortSignal을 전달합니다. */
export function useExampleQuery(id: number) {
  return useQuery({
    queryKey: exampleQueryKeys.detail(id),
    queryFn: ({ signal }) => getExample({ id, signal }),
  });
}
