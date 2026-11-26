import { apiClient } from '@/shared/lib/http/apiClient';
import type { PlaceCatalog, PlaceDetailData, PlacePage, PlaceSelection } from '../types/placeApi.type';
import type { PlaceMapBounds } from '../types/kakaoMap.type';

export const seasonCodes: Record<string, string> = { 봄: 'SPRING', 여름: 'SUMMER', 가을: 'AUTUMN', 겨울: 'WINTER' };
export const environmentCodes: Record<string, string> = { 실내: 'INDOOR', 실외: 'OUTDOOR' };
export const seasonLabel = (value: string) => Object.entries(seasonCodes).find(([, code]) => code === value)?.[0] ?? value;
export const environmentLabel = (value: string | null) => Object.entries(environmentCodes).find(([, code]) => code === value)?.[0] ?? value ?? '실내외 미확인';
const placeSearchLimit = 50;
const allMapBounds = { south: -90, west: -180, north: 90, east: 180 };

export async function getPlaceCatalog(signal?: AbortSignal): Promise<PlaceCatalog> {
  return (await apiClient.get<PlaceCatalog>('/api/v1/places/filters', { signal })).data;
}

export async function getPlacePage(params: URLSearchParams, signal?: AbortSignal): Promise<PlacePage> {
  return (await apiClient.get<PlacePage>('/api/v1/places', { params, signal })).data;
}

export async function searchPlaces(
  selection: Omit<PlaceSelection, 'sort'>,
  catalog: PlaceCatalog,
  bounds: PlaceMapBounds | null,
  signal?: AbortSignal
): Promise<PlacePage> {
  const region = catalog.regions.find(item => item.name === selection.region);
  const category = catalog.categories.find(item => item.name === selection.category);
  if ((selection.region && !region) || (selection.category !== '전체' && !category?.activityTypeIds.length)) {
    return { items: [], page: 0, size: placeSearchLimit, totalElements: 0 };
  }
  const params = new URLSearchParams({ page: '0', size: String(placeSearchLimit), sort: 'name' });
  if (selection.query) params.set('query', selection.query);
  if (region) params.set('regionId', String(region.id));
  if (selection.category !== '전체') category?.activityTypeIds.forEach(id => params.append('activityTypeIds', String(id)));
  if (selection.season) params.set('season', seasonCodes[selection.season] ?? selection.season);
  if (selection.environment) params.set('environmentType', environmentCodes[selection.environment] ?? selection.environment);
  // 실제 지도 범위가 설정되기 전에도 좌표가 있는 장소만 조회합니다.
  Object.entries(bounds ?? allMapBounds).forEach(([key, value]) => params.set(key, String(value)));
  return getPlacePage(params, signal);
}

export async function getPlaceDetail(id: number, signal?: AbortSignal): Promise<PlaceDetailData> {
  return (await apiClient.get<PlaceDetailData>(`/api/v1/places/${id}`, { signal })).data;
}
