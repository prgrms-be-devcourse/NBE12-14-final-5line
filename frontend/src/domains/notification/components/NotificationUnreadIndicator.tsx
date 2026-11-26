'use client';

import { useQuery } from '@tanstack/react-query';
import { notificationUnreadStatusQuery } from '../queries/notificationQueries';

export default function NotificationUnreadIndicator({ memberId, descriptionId }: {
  memberId: number;
  descriptionId: string;
}) {
  const { data: hasUnread } = useQuery(notificationUnreadStatusQuery(memberId));

  return (
    <>
      {hasUnread && <span className="size-2 shrink-0 rounded-full bg-red-500" aria-hidden="true" />}
      <span id={descriptionId} hidden>{hasUnread ? '읽지 않은 알림 있음' : null}</span>
    </>
  );
}
