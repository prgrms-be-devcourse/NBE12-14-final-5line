'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useState, type MouseEvent } from 'react';
import {
  markNotificationRead,
  type InboxNotification,
  type NotificationPage,
} from '@/domains/notification/api/notificationApi';
import { notificationInboxKey, notificationInboxQuery, notificationInboxViewKey } from '@/domains/notification/queries/notificationQueries';
import { getPlaceNoticeUrl, scrollToPlaceNotice } from '@/domains/place/lib/placeNavigation';
import { ApiError } from '@/shared/lib/http/apiError';

const pageSize = 20;
const linkClassName = 'font-bold text-[#5147d9] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#5147ff]';
const buttonClassName = `${linkClassName} cursor-pointer rounded-lg border border-[#ddd9ff] px-3 py-2 text-sm disabled:cursor-not-allowed disabled:opacity-60`;

export default function NotificationList({ memberId, unreadOnly = false, onNavigate }: {
  memberId: number;
  unreadOnly?: boolean;
  onNavigate?: () => void;
}) {
  const [page, setPage] = useState(0);
  const inbox = useQuery(notificationInboxQuery(memberId, page, pageSize, unreadOnly));

  if (inbox.isPending) return <p className="text-sm text-slate-600" role="status">알림을 불러오고 있어요.</p>;

  const data = inbox.data;
  const hasNextPage = !!data && (page + 1) * data.size < data.totalElements;

  return (
    <>
      {inbox.isError && (
        <p className="mb-3 text-sm text-red-700" role="alert">
          {data ? '알림을 새로 불러오지 못했어요.' : '알림을 불러오지 못했어요.'}{' '}
          <button className={linkClassName} type="button" onClick={() => void inbox.refetch()}>다시 시도</button>
        </p>
      )}
      {data && (data.items.length ? (
        <ul className="grid gap-4">
          {data.items.map(notification => (
            <NotificationItem key={notification.notificationId} memberId={memberId} notification={notification}
              unreadOnly={unreadOnly} onNavigate={onNavigate} />
          ))}
        </ul>
      ) : <p className="text-sm text-slate-600">
        {page > 0 ? '이 페이지에 표시할 알림이 없어요.' : unreadOnly ? '읽지 않은 알림이 없어요.' : '받은 알림이 없어요.'}
      </p>)}
      {(page > 0 || hasNextPage) && (
        <nav className="mt-6 flex items-center gap-3" aria-label="알림함 페이지">
          <button className={buttonClassName} type="button" disabled={page === 0}
            onClick={() => setPage(value => value - 1)}>이전</button>
          <span className="text-sm text-slate-600">{page + 1}페이지</span>
          <button className={buttonClassName} type="button" disabled={!hasNextPage}
            onClick={() => setPage(value => value + 1)}>다음</button>
        </nav>
      )}
    </>
  );
}

function NotificationItem({ memberId, notification, unreadOnly, onNavigate }: {
  memberId: number;
  notification: InboxNotification;
  unreadOnly: boolean;
  onNavigate?: () => void;
}) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const queryKey = notificationInboxKey(memberId);
  const title = notification.noticeAvailable ? notification.title ?? '장소 공지' : '현재 볼 수 없는 공지';
  const isRead = notification.readAt !== null;
  const noticeUrl = getPlaceNoticeUrl(notification.placeId, notification.placeNoticeId);
  const Heading = unreadOnly ? 'h3' : 'h2';
  const mutation = useMutation({
    mutationFn: markNotificationRead,
    onSuccess: async result => {
      await queryClient.cancelQueries({ queryKey });
      queryClient.setQueriesData<NotificationPage>({ queryKey: notificationInboxViewKey(memberId, false) }, previous => previous && ({
        ...previous,
        items: previous.items.map(item => item.notificationId === result.notificationId
          ? { ...item, readAt: result.readAt } : item),
      }));
      queryClient.setQueriesData<NotificationPage>({ queryKey: notificationInboxViewKey(memberId, true) }, previous => previous && ({
        ...previous,
        items: previous.items.filter(item => item.notificationId !== result.notificationId),
      }));
      void queryClient.invalidateQueries({ queryKey });
    },
  });

  async function openNotice(event: MouseEvent<HTMLAnchorElement>) {
    const sameTab = event.button === 0 && !event.metaKey && !event.ctrlKey && !event.shiftKey && !event.altKey;
    if (sameTab) event.preventDefault();
    if (mutation.isPending) return;

    try {
      if (!isRead) await mutation.mutateAsync(notification.notificationId);
    } catch {
      return; // The card shows the mutation error and keeps the action available.
    }

    if (!sameTab) return;
    onNavigate?.();
    const target = new URL(noticeUrl, window.location.href);
    const current = window.location;
    if (target.pathname === current.pathname && target.search === current.search) {
      if (target.hash === current.hash) scrollToPlaceNotice();
      else current.hash = target.hash;
    } else {
      router.push(noticeUrl, { scroll: false });
    }
  }

  return (
    <li className="rounded-2xl border border-[#e9edf3] bg-white p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <Heading className="text-base font-bold">{title}</Heading>
          <div className="mt-1 flex flex-wrap items-center gap-x-2 text-sm text-slate-600">
            <time dateTime={notification.createdAt}>{notification.createdAt.replace('T', ' ').slice(0, 16)}</time>
            <span className={isRead ? undefined : 'font-bold text-[#5147d9]'}>{isRead ? '읽음' : '읽지 않음'}</span>
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {!isRead && (
            <button className={buttonClassName} type="button" disabled={mutation.isPending}
              onClick={() => mutation.mutate(notification.notificationId)}>
              {mutation.isPending ? '읽음 처리 중…' : '읽음 표시'}
            </button>
          )}
          {notification.noticeAvailable && (
            <Link className="inline-flex items-center gap-1 rounded-lg bg-[#f3f1ff] px-3 py-2 text-sm font-bold text-[#5147d9] hover:bg-[#eae6ff] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#5147ff]"
              href={noticeUrl} scroll={false} onClick={event => void openNotice(event)}
              onAuxClick={event => { if (event.button === 1) void openNotice(event); }}
              aria-disabled={mutation.isPending}>
              공지 보기 <span aria-hidden="true">→</span>
            </Link>
          )}
        </div>
      </div>
      {mutation.isError && <p className="mt-2 text-sm text-red-700" role="alert">
        {mutation.error instanceof ApiError ? mutation.error.message : '알림을 읽음 처리하지 못했어요.'}
      </p>}
    </li>
  );
}
