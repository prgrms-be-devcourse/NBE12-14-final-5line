import type { PlaceSelection } from '../types/placeApi.type';
import type { PlaceMapBounds } from '../types/kakaoMap.type';

export const placeQueryKeys = {
  catalog: ['places', 'filters'] as const,
  list: (filters: Omit<PlaceSelection, 'sort'>, bounds: PlaceMapBounds | null) => ['places', 'list', filters, bounds] as const,
  detail: (id: number) => ['places', 'detail', id] as const,
};
