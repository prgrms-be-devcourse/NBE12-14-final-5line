import { queryOptions, useQuery } from '@tanstack/react-query';
import { getFavoritePlaces } from '../api/favoriteApi';

export function favoritePlacesQuery(memberId: number) {
  return queryOptions({
    queryKey: ['member', memberId, 'favorite-places'],
    queryFn: ({ signal }) => getFavoritePlaces(signal),
  });
}

export function useFavoritePlaces(memberId: number) {
  return useQuery(favoritePlacesQuery(memberId));
}
