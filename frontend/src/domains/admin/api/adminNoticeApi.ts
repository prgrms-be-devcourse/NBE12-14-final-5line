import { csrfHeader } from '@/domains/member/api/memberApi';
import { apiClient } from '@/shared/lib/http/apiClient';
import type { AdminNotice, AdminNoticeDraftInput, AdminNoticePage } from '../types/adminNotice.type';

function noticesPath(placeId: number) {
  return `/api/v1/admin/places/${placeId}/notices`;
}

export async function getAdminNotices({ placeId, page, signal }: {
  placeId: number;
  page: number;
  signal?: AbortSignal;
}): Promise<AdminNoticePage> {
  const { data } = await apiClient.get<AdminNoticePage>(noticesPath(placeId), { params: { page, size: 20 }, signal });
  return data;
}

export async function createNotice({ placeId, input, publicationRequestId }: {
  placeId: number;
  input: AdminNoticeDraftInput;
  publicationRequestId?: string;
}): Promise<AdminNotice> {
  const { data } = await apiClient.post<AdminNotice>(noticesPath(placeId), input, {
    params: publicationRequestId ? { publish: true, publicationRequestId } : undefined,
    headers: await csrfHeader(),
  });
  return data;
}

export async function updateNotice({ placeId, noticeId, input, publish }: {
  placeId: number;
  noticeId: number;
  input: AdminNoticeDraftInput;
  publish: boolean;
}): Promise<AdminNotice> {
  const { data } = await apiClient.patch<AdminNotice>(`${noticesPath(placeId)}/${noticeId}`, input, {
    params: publish ? { publish } : undefined,
    headers: await csrfHeader(),
  });
  return data;
}

export async function changeNoticeVisibility({ placeId, noticeId, visible }: {
  placeId: number;
  noticeId: number;
  visible: boolean;
}): Promise<AdminNotice> {
  const { data } = await apiClient.patch<AdminNotice>(`${noticesPath(placeId)}/${noticeId}/visibility`, { visible }, {
    headers: await csrfHeader(),
  });
  return data;
}
