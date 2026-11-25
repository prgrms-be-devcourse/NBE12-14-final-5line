import { csrfHeader } from '@/domains/member/api/memberApi';
import type { PlaceSummary } from '@/domains/place/types/placeApi.type';
import { apiClient } from '@/shared/lib/http/apiClient';

export type PublishedFavorite = PlaceSummary & { publishStatus: 'PUBLISHED'; isNotified: boolean };
export type FavoritePlace = PublishedFavorite | {
  id: number;
  name: string | null;
  publishStatus: 'HIDDEN';
};

type FavoriteAction = { placeId: number; message: string };
type FavoriteNotification = { placeId: number; isNotified: boolean };

const favoritesPath = '/api/v1/members/me/favorites';

export async function getFavoritePlaces(signal?: AbortSignal): Promise<FavoritePlace[]> {
  const { data } = await apiClient.get<FavoritePlace[]>(favoritesPath, { signal });
  return data;
}

export async function saveFavoritePlace(placeId: number): Promise<FavoriteAction> {
  const { data } = await apiClient.post<FavoriteAction>(favoritesPath, { placeId }, {
    headers: await csrfHeader(),
  });
  return data;
}

export async function removeFavoritePlace(placeId: number): Promise<FavoriteAction> {
  const { data } = await apiClient.delete<FavoriteAction>(`${favoritesPath}/${placeId}`, {
    headers: await csrfHeader(),
  });
  return data;
}

export async function changeFavoriteNotification({ placeId, isNotified }: FavoriteNotification): Promise<FavoriteNotification> {
  const { data } = await apiClient.patch<FavoriteNotification>(`${favoritesPath}/${placeId}/notification-settings`, { isNotified }, {
    headers: await csrfHeader(),
  });
  return data;
}
