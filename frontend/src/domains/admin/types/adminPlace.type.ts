import type { PlaceDetailData, PlaceSummary } from '@/domains/place/types/placeApi.type';

export type AdminPlace = Pick<PlaceSummary, 'id' | 'name' | 'address'> & {
  status: 'DRAFT' | 'PUBLISHED' | 'HIDDEN';
};

export type AdminPlacePage = {
  items: AdminPlace[];
  page: number;
  size: number;
  totalElements: number;
};

type AdminPlaceSource = Omit<PlaceDetailData['sources'][number], 'verifiedAt'> & {
  verifiedAt: string | null;
};

export type AdminPlaceDetail = AdminPlace &
  Pick<PlaceDetailData, 'nameAlias' | 'latitude' | 'longitude' | 'description' | 'homepage' | 'activities'> & {
    regionId: number;
    regionName: string | null;
    sources: AdminPlaceSource[];
  };

export type PlaceDraftInput = {
  regionId: number;
  name: string;
  address: string;
  description: string;
  latitude: number;
  longitude: number;
};
