import { csrfHeader } from '@/domains/member/api/memberApi';
import { apiClient } from '@/shared/lib/http/apiClient';

export type InboxNotification = {
  notificationId: number;
  eventId: number;
  placeId: number;
  placeNoticeId: number;
  title: string | null;
  createdAt: string;
  readAt: string | null;
  sourceUrl: string | null;
  noticeAvailable: boolean;
};

export type NotificationPage = {
  items: InboxNotification[];
  page: number;
  size: number;
  totalElements: number;
};

type NotificationRead = { notificationId: number; readAt: string };

const notificationsPath = '/api/v1/members/me/notifications';

export async function getNotificationPage(page: number, size: number, unreadOnly: boolean, signal?: AbortSignal): Promise<NotificationPage> {
  const { data } = await apiClient.get<NotificationPage>(notificationsPath, {
    params: { page, size, unreadOnly: unreadOnly || undefined },
    signal,
  });
  return data;
}

export async function markNotificationRead(notificationId: number): Promise<NotificationRead> {
  const { data } = await apiClient.patch<NotificationRead>(`${notificationsPath}/${notificationId}/read`, undefined, {
    headers: await csrfHeader(),
  });
  return data;
}
