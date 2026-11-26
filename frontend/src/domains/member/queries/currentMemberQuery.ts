import { queryOptions, type QueryClient } from '@tanstack/react-query';
import { currentMember, type Member } from '../api/memberApi';
import { ApiError } from '@/shared/lib/http/apiError';

export const currentMemberQuery = queryOptions({
  queryKey: ['member', 'me'],
  queryFn: async ({ signal }) => {
    try {
      return await currentMember(signal);
    } catch (cause) {
      if (cause instanceof ApiError && cause.details.kind === 'http' && cause.details.status === 401) {
        return null;
      }
      throw cause;
    }
  },
});

/** 이전 세션의 조회 응답이 로그인이나 로그아웃 결과를 덮어쓰지 않도록 합니다. */
export async function setCurrentMember(queryClient: QueryClient, member: Member | null) {
  await queryClient.cancelQueries({ queryKey: currentMemberQuery.queryKey });
  queryClient.setQueryData(currentMemberQuery.queryKey, member);
}
