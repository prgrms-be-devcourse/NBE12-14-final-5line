'use client';

import { hashKey, skipToken, useQuery } from '@tanstack/react-query';
import { getPlaceCatalog, getPlaceDetail, searchPlaces } from '../api/placeApi';
import { placeQueryKeys } from '../queries/placeQueryKeys';
import type { PlaceSelection } from '../types/placeApi.type';
import type { PlaceMapBounds } from '../types/kakaoMap.type';

export function usePlaceCatalog() {
  return useQuery({ queryKey: placeQueryKeys.catalog, queryFn: ({ signal }) => getPlaceCatalog(signal) });
}

export function usePlaces(
  filters: Omit<PlaceSelection, 'sort'>,
  bounds: PlaceMapBounds | null,
  keepPreviousArea: boolean
) {
  const catalog = usePlaceCatalog();
  const { data: placeCatalog } = catalog;
  const places = useQuery({
    queryKey: [...placeQueryKeys.list(filters, bounds), placeCatalog] as const,
    queryFn: placeCatalog
      ? ({ signal }) => searchPlaces(filters, placeCatalog, bounds, signal)
      : skipToken,
    refetchOnMount: false,
    placeholderData: keepPreviousArea ? (previous, query) => {
      if (!query) return undefined;
      const [, , previousFilters, , previousCatalog] = query.queryKey;
      return hashKey([previousFilters, previousCatalog]) === hashKey([filters, placeCatalog]) ? previous : undefined;
    } : undefined,
  });
  return { catalog, places };
}

export function usePlaceDetail(id: number) {
  return useQuery({
    queryKey: placeQueryKeys.detail(id),
    enabled: Number.isSafeInteger(id) && id > 0,
    queryFn: ({ signal }) => getPlaceDetail(id, signal),
  });
}
