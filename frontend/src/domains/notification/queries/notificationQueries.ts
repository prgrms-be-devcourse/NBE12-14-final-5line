import { queryOptions } from '@tanstack/react-query';
import { getNotificationPage, getNotificationUnreadStatus } from '../api/notificationApi';

export function notificationInboxKey(memberId: number) {
  return ['member', memberId, 'notifications'] as const;
}

export function notificationInboxViewKey(memberId: number, unreadOnly: boolean) {
  return [...notificationInboxKey(memberId), unreadOnly ? 'unread' : 'all'] as const;
}

export function notificationInboxQuery(memberId: number, page: number, size: number, unreadOnly: boolean) {
  return queryOptions({
    queryKey: [...notificationInboxViewKey(memberId, unreadOnly), page, size],
    queryFn: ({ signal }) => getNotificationPage(page, size, unreadOnly, signal),
    refetchOnMount: unreadOnly ? 'always' : true,
  });
}

export function notificationUnreadStatusQuery(memberId: number) {
  return queryOptions({
    queryKey: [...notificationInboxKey(memberId), 'unread-status'],
    queryFn: ({ signal }) => getNotificationUnreadStatus(signal),
    refetchOnWindowFocus: true,
  });
}
