import { queryOptions } from '@tanstack/react-query';
import type { QueryClient } from '@tanstack/react-query';
import { getPlacePage } from '@/domains/place/api/placeApi';
import { placeQueryKeys } from '@/domains/place/queries/placeQueryKeys';
import { getAdminNotices } from '../api/adminNoticeApi';

export function noticePlaceSearchQuery(query: string, page: number) {
  return queryOptions({
    queryKey: ['admin', 'notice-place-search', query, page],
    queryFn: ({ signal }) => {
      const params = new URLSearchParams({ query, page: String(page), size: '8' });
      return getPlacePage(params, signal);
    },
  });
}

export function adminNoticeListKey(placeId: number) {
  return ['admin', 'places', placeId, 'notices'] as const;
}

export function adminNoticeListQuery(placeId: number, page: number) {
  return queryOptions({
    queryKey: [...adminNoticeListKey(placeId), page],
    queryFn: ({ signal }) => getAdminNotices({ placeId, page, signal }),
  });
}

export async function refreshNoticeQueries(queryClient: QueryClient, placeId: number, refreshPublicDetail: boolean) {
  const updates = [queryClient.invalidateQueries({ queryKey: adminNoticeListKey(placeId) })];
  if (refreshPublicDetail) {
    updates.push(queryClient.invalidateQueries({ queryKey: placeQueryKeys.detail(placeId), exact: true }));
  }
  await Promise.all(updates);
}
