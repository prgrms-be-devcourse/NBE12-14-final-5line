import { apiClient } from '@/shared/lib/http/apiClient';
import { csrfHeader } from '@/domains/member/api/memberApi';
import type { AdminPlace, AdminPlaceDetail, AdminPlacePage, PlaceDraftInput } from '../types/adminPlace.type';

export type AdminSummary = { pendingReports: number; draftPlaces: number; publishedPlaces: number };

export async function getAdminSummary(signal?: AbortSignal): Promise<AdminSummary> {
  return (await apiClient.get<AdminSummary>('/api/v1/admin/dashboard', { signal })).data;
}

export async function getDraftPlaces(filters: { page: number; query: string }, signal?: AbortSignal): Promise<AdminPlacePage> {
  const params = { status: 'DRAFT', page: filters.page, query: filters.query || undefined };
  return (await apiClient.get<AdminPlacePage>('/api/v1/admin/places', { params, signal })).data;
}

export async function getAdminPlaceDetail(placeId: number, signal?: AbortSignal): Promise<AdminPlaceDetail> {
  return (await apiClient.get<AdminPlaceDetail>(`/api/v1/admin/places/${placeId}`, { signal })).data;
}

export async function createPlaceDraft(input: PlaceDraftInput): Promise<AdminPlace> {
  return (await apiClient.post<AdminPlace>('/api/v1/admin/places', input,
    { headers: await csrfHeader() })).data;
}
