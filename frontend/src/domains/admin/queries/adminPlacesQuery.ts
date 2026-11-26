import { queryOptions } from '@tanstack/react-query';
import { getAdminPlaceDetail, getDraftPlaces } from '../api/adminApi';

export const adminDraftPlacesKey = ['admin', 'places', 'drafts'];

export function adminDraftPlacesQuery(filters: { page: number; query: string }) {
  return queryOptions({
    queryKey: [...adminDraftPlacesKey, filters],
    queryFn: ({ signal }) => getDraftPlaces(filters, signal),
  });
}

export function adminPlaceDetailQuery(placeId: number) {
  return queryOptions({
    queryKey: ['admin', 'places', 'detail', placeId],
    queryFn: ({ signal }) => getAdminPlaceDetail(placeId, signal),
  });
}
