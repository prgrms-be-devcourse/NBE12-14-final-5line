import { apiClient } from '@/shared/lib/http/apiClient';
import { csrfHeader } from '@/domains/member/api/memberApi';
import type { AdminPlace, AdminPlaceDetail, AdminPlacePage, PlaceDraftInput } from '../types/adminPlace.type';

export type AdminSummary = { pendingReports: number; draftPlaces: number; publishedPlaces: number };

export async function getAdminSummary(signal?: AbortSignal): Promise<AdminSummary> {
  return (await apiClient.get<AdminSummary>('/api/v1/admin/dashboard', { signal })).data;
}

export async function getDraftPlaces(filters: { page: number; query: string; status?: 'DRAFT' | 'PUBLISHED' | 'HIDDEN' }, signal?: AbortSignal): Promise<AdminPlacePage> {
  const params = { status: filters.status ?? 'DRAFT', page: filters.page, query: filters.query || undefined };
  return (await apiClient.get<AdminPlacePage>('/api/v1/admin/places', { params, signal })).data;
}

export async function getAdminPlaceDetail(placeId: number, signal?: AbortSignal): Promise<AdminPlaceDetail> {
  return (await apiClient.get<AdminPlaceDetail>(`/api/v1/admin/places/${placeId}`, { signal })).data;
}

export async function createPlaceDraft(input: PlaceDraftInput): Promise<AdminPlace> {
  return (await apiClient.post<AdminPlace>('/api/v1/admin/places', input,
    { headers: await csrfHeader() })).data;
}

export type SnorkelingCandidatePage = {
  items: { candidateId: string; facilityName: string; reviewStatus: string; recommendable: boolean; existingPlaceId: number | null }[];
  page: number; size: number; totalElements: number;
};
export async function getSnorkelingCandidates(page: number, signal?: AbortSignal): Promise<SnorkelingCandidatePage> {
  return (await apiClient.get<SnorkelingCandidatePage>('/api/v1/admin/place-candidates/snorkeling',
    { params: { page, size: 20 }, signal })).data;
}

export type AdminEvidence = {
  prices: { id: number; ticketName: string | null; priceKind: string; priceBasis: string;
    amountKrw: number | null; freeOfCharge: boolean; sourceKind: string; sourceUrl: string | null;
    checkedAt: string | null; verifiedAt: string | null; validFrom: string | null;
    validUntil: string | null; status: 'VERIFIED' | 'NEEDS_REVIEW' | 'REJECTED' }[];
  facts: { id: number; fieldKey: string; valueJson: string; sourceKind: string;
    sourceUrl: string | null; checkedAt: string | null; verifiedAt: string | null;
    validFrom: string | null; validUntil: string | null;
    status: 'VERIFIED' | 'NEEDS_REVIEW' | 'REJECTED' }[];
};
export async function getAdminEvidence(placeId: number, signal?: AbortSignal): Promise<AdminEvidence> {
  return (await apiClient.get<AdminEvidence>(`/api/v1/admin/places/${placeId}/evidence`, { signal })).data;
}
export async function reviewAdminEvidence(placeId: number, kind: 'prices' | 'facts', id: number,
  status: 'VERIFIED' | 'NEEDS_REVIEW' | 'REJECTED'): Promise<void> {
  await apiClient.patch(`/api/v1/admin/places/${placeId}/${kind}/${id}/review`, { status },
    { headers: await csrfHeader() });
}
export async function updateAdminPlace(placeId: number, input: { name: string; address: string; description: string }): Promise<AdminPlace> {
  return (await apiClient.patch<AdminPlace>(`/api/v1/admin/places/${placeId}`, input,
    { headers: await csrfHeader() })).data;
}
export async function changeAdminPlaceStatus(placeId: number, status: AdminPlace['status']): Promise<AdminPlace> {
  return (await apiClient.patch<AdminPlace>(`/api/v1/admin/places/${placeId}/visibility`, { status },
    { headers: await csrfHeader() })).data;
}

export async function createAdminPrice(placeId: number, input: Record<string, unknown>): Promise<void> {
  await apiClient.post(`/api/v1/admin/places/${placeId}/prices`, input, { headers: await csrfHeader() });
}
export async function createAdminFact(placeId: number, input: Record<string, unknown>): Promise<void> {
  await apiClient.post(`/api/v1/admin/places/${placeId}/facts`, input, { headers: await csrfHeader() });
}
