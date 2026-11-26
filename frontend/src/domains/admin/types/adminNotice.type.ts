export type AdminNoticeSource = {
  sourceRecordId: number;
  type: string | null;
  url: string | null;
  verifiedFields: string | null;
  originUpdatedAt: string | null;
  verifiedAt: string | null;
};

export type AdminNotice = {
  placeNoticeId: number;
  title: string | null;
  content: string | null;
  validFrom: string | null;
  validUntil: string | null;
  publishStatus: 'DRAFT' | 'PUBLISHED' | 'HIDDEN';
  source: AdminNoticeSource;
};

export type AdminNoticePage = {
  items: AdminNotice[];
  page: number;
  size: number;
  totalElements: number;
};

export type AdminNoticeDraftInput = {
  title: string;
  content: string;
  validFrom: string | null;
  validUntil: string | null;
} & (
  | { sourceRecordId: number; source?: never }
  | { source: { url: string; verifiedAt: string }; sourceRecordId?: never }
);
