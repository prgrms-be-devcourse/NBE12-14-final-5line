/** 같은 예시 데이터를 사용하는 모든 화면이 공유하는 Query Key입니다. ID별 상세 캐시를 구분합니다. */
export const exampleQueryKeys = {
  all: ['examples'] as const,
  details: () => [...exampleQueryKeys.all, 'detail'] as const,
  detail: (id: number) => [...exampleQueryKeys.details(), id] as const,
};
