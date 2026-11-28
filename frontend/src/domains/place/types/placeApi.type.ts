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
  pricePreview?: string | null;
};
export type PlacePage = { items: PlaceSummary[]; page: number; size: number; totalElements: number };
export type PlaceCatalog = {
  regions: { id: number; name: string }[];
  categories: { id: number; name: string; activityTypeIds: number[]; publishedPlaceCount?: number }[];
};
export type PlaceSelection = { query: string; category: string; region: string; season: string; environment: string; sort: string };
export type PlaceOperatingInfoData = {
  operatingHours: string | null;
  restDays: string | null;
  feeInfo: string | null;
  contactPhone: string | null;
  sourceUrl: string | null;
  collectedAt: string;
  verificationStatus: 'UNVERIFIED' | 'NEEDS_REVIEW' | 'VERIFIED';
  verifiedAt: string | null;
  reviewNote: string | null;
  officialSourceUrl: string | null;
  comparedAt: string | null;
};
export type PlaceDetailData = PlaceSummary & {
  operatingInfo?: PlaceOperatingInfoData | null;
  verifiedDetails?: { prices: VerifiedPlacePrice[]; activityDetails: Record<string, unknown>; operating: Record<string, unknown> } | null;
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

export type SnorkelingSafetyData = {
  placeId: number;
  siteType: string;
  entryStatus: 'ALLOWED' | 'RESTRICTED' | 'PROHIBITED' | 'UNKNOWN';
  recommendable: boolean;
  verifiedSafetyDetails: Record<string, unknown> | null;
  commonPrecautions: string[];
  confirmedWarnings: string[];
  sourceUrl: string | null;
  checkedAt: string | null;
  verifiedAt: string | null;
};
export type VerifiedPlacePrice = {
  priceKind: string; ticketName: string | null; priceType: string | null;
  ageBand: string | null; dayType: string | null; seasonLabel: string | null;
  amountKrw: number | null; freeOfCharge: boolean; priceUnit: string | null;
  durationMinutes: number | null; useCount: number | null; passengerCount: number | null;
  equipmentType: string | null; equipmentIncluded: boolean | null; mandatoryExtraCost: boolean | null;
  residencyCondition: string | null; rawPriceText: string | null; additionalConditions: unknown;
  sourceUrl: string | null; verifiedAt: string | null;
  validFrom: string | null; validUntil: string | null;
};

export type FisheryRule = {
  ruleId: number; resourceName: string; category: string; ruleKind: string;
  period: string | null; threshold: string | null; region: string | null; dateMatches: boolean;
  sourceExcerpt: string; legalBasis: string; noticeNumber: string | null;
  sourceDocument: string; officialUrl: string | null; verificationStatus: string;
  lastVerifiedAt: string | null;
};
export type FisheryProtectionData = {
  placeId: number; visitDate: string; harvestPermission: 'UNKNOWN'; advisory: string;
  confirmedClosed: FisheryRule[]; confirmedLimits: FisheryRule[]; reviewCandidates: FisheryRule[];
};
