import { csrfHeader } from '@/domains/member/api/memberApi';
import { apiClient } from '@/shared/lib/http/apiClient';

export type OperatingChange = {
  candidateId: number;
  placeId: number;
  placeName: string | null;
  field: string;
  previousValue: string | null;
  newValue: string;
  previousSourceUrl: string;
  newSourceUrl: string;
  detectedAt: string;
};

type OperatingChangePage = { items: OperatingChange[]; page: number; size: number; totalElements: number };

export type OperatingChangeResolution = {
  candidateId: number;
  placeId: number;
  decision: 'APPROVED' | 'REJECTED';
  reason: string;
};

const path = '/api/v1/admin/place-operating-changes';

export async function getOperatingChanges(page: number, signal: AbortSignal) {
  const { data } = await apiClient.get<OperatingChangePage>(path, { params: { page, size: 20 }, signal });
  return data;
}

export async function resolveOperatingChange({ candidateId, decision, reason }: OperatingChangeResolution) {
  await apiClient.patch(`${path}/${candidateId}/resolution`, { decision, reason }, { headers: await csrfHeader() });
}
