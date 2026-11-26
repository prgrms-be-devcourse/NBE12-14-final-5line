export type PlaceSummary = {
  id: number;
  name: string | null;
  address: string | null;
  latitude: number | null;
  longitude: number | null;
  representativeImage?: string | null;
  imageAttribution?: string | null;
  imageSourceUrl?: string | null;
  imageLicense?: string | null;
};
export type PlacePage = { items: PlaceSummary[]; page: number; size: number; totalElements: number };
export type PlaceCatalog = {
  regions: { id: number; name: string }[];
  categories: { id: number; name: string; activityTypeIds: number[] }[];
};
export type PlaceSelection = { query: string; category: string; region: string; season: string; environment: string; sort: string };
export type PlaceOperatingInfoData = {
  operatingHours: string | null;
  restDays: string | null;
  contactPhone: string | null;
  sourceUrl: string;
  collectedAt: string;
  verificationStatus: 'UNVERIFIED' | 'NEEDS_REVIEW' | 'VERIFIED';
  verifiedAt: string | null;
  reviewNote: string | null;
  officialSourceUrl: string | null;
  comparedAt: string | null;
};
export type PlaceDetailData = PlaceSummary & {
  operatingInfo?: PlaceOperatingInfoData | null;
  nameAlias: string | null;
  description: string | null;
  homepage: string | null;
  operationStatus: string | null;
  reviewedAt: string | null;
  representativeImage: string | null;
  activities: { placeActivityId: number; activityTypeId: number; activityTypeName: string; environmentType: string | null; seasons: string[] }[];
  notices: { placeNoticeId: number; title: string | null; content: string | null; validFrom: string | null; validUntil: string | null; sourceRecordId: number }[];
  sources: { sourceRecordId: number; type: string | null; url: string | null; verifiedFields: string | null; originUpdatedAt: string | null; verifiedAt: string }[];
};
