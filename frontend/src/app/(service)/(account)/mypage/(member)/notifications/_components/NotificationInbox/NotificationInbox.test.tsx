// @vitest-environment jsdom

import '../../../../../../../../../tests/setup-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { AppRouterContext } from 'next/dist/shared/lib/app-router-context.shared-runtime';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { InboxNotification } from '@/domains/notification/api/notificationApi';
import { createQueryClient } from '@/shared/lib/query/queryClient';
import { server } from '../../../../../../../../../tests/mocks/server';
import NotificationInbox from './NotificationInbox';

const api = 'https://api.outpick.test/api/v1';
const inboxUrl = `${api}/members/me/notifications`;
const push = vi.fn();
const router = { push, back: vi.fn(), forward: vi.fn(), refresh: vi.fn(), replace: vi.fn(), prefetch: vi.fn(), bfcacheId: '' };
const notice: InboxNotification = {
  notificationId: 101,
  eventId: 21,
  placeId: 7,
  placeNoticeId: 34,
  title: '휴무 안내',
  createdAt: '2026-11-25T11:03:05',
  readAt: null,
  sourceUrl: 'https://example.com/holiday',
  noticeAvailable: true,
};

function mockMember() {
  server.use(
    http.get(`${api}/members/me`, () => HttpResponse.json({ id: 9, role: 'USER' })),
    http.get(`${inboxUrl}/unread-status`, () => HttpResponse.json({ hasUnread: false })),
    http.get(`${api}/auth/csrf`, () => HttpResponse.json({ token: 'test-token', headerName: 'X-CSRF-TOKEN' }))
  );
}

function renderInbox() {
  return render(
    <AppRouterContext.Provider value={router}>
      <QueryClientProvider client={createQueryClient()}>
        <NotificationInbox />
      </QueryClientProvider>
    </AppRouterContext.Provider>
  );
}

function notificationPage(items: InboxNotification[], page = 0, totalElements = items.length) {
  return HttpResponse.json({ items, page, size: 20, totalElements });
}

describe('개인 알림함', () => {
  beforeEach(() => push.mockClear());

  it('공지 보기 동작을 명시하고 다음 페이지에 요청한 알림을 표시한다', async () => {
    mockMember();
    const user = userEvent.setup();
    const pages: string[] = [];
    const unavailable = { ...notice, notificationId: 102, noticeAvailable: false, readAt: notice.createdAt };
    server.use(http.get(inboxUrl, ({ request }) => {
      const params = new URL(request.url).searchParams;
      const page = params.get('page')!;
      pages.push(page);
      expect(params.get('size')).toBe('20');
      expect(params.has('unreadOnly')).toBe(false);
      return page === '0' ? notificationPage([notice], 0, 21) : notificationPage([unavailable], 1, 21);
    }));
    renderInbox();

    expect((await screen.findByRole('link', { name: '공지 보기' })).getAttribute('href'))
      .toMatch(/^\/places\/detail\/?\?id=7#place-notice-34$/);
    expect(screen.getByRole('heading', { name: '휴무 안내' })).toBeTruthy();
    expect(screen.queryByRole('link', { name: /공식 출처 보기/ })).toBeNull();
    expect(screen.getByText('2026-11-25 11:03').getAttribute('datetime')).toBe(notice.createdAt);
    expect(screen.getByText('읽지 않음')).toBeTruthy();
    expect(screen.queryByRole('button', { name: '읽음 표시' })).toBeNull();
    await user.click(screen.getByRole('button', { name: '다음' }));
    expect(await screen.findByRole('heading', { name: '현재 볼 수 없는 공지' })).toBeTruthy();
    expect(screen.getByText('2페이지')).toBeTruthy();
    expect(screen.queryByRole('link')).toBeNull();
    expect(screen.getByText('읽음')).toBeTruthy();
    expect(pages).toEqual(['0', '1']);
    await user.click(screen.getByRole('button', { name: '이전' }));
    expect(await screen.findByRole('link', { name: '공지 보기' })).toBeTruthy();
  });

  it('미읽음 알림의 공지 보기로 이동하기 전에 읽음 시각을 저장한다', async () => {
    mockMember();
    const user = userEvent.setup();
    const response = Promise.withResolvers<void>();
    const readAt = '2026-11-25T11:08:00';
    let current = notice;
    const mark = vi.fn(async () => {
      await response.promise;
      current = { ...notice, readAt };
      return HttpResponse.json({ notificationId: 101, readAt });
    });
    server.use(http.get(inboxUrl, () => notificationPage([current])), http.patch(`${inboxUrl}/101/read`, mark));
    renderInbox();

    await user.click(await screen.findByRole('link', { name: '공지 보기' }));
    await waitFor(() => expect(mark).toHaveBeenCalledTimes(1));
    expect(push).not.toHaveBeenCalled();
    response.resolve();
    await waitFor(() => expect(push).toHaveBeenCalledWith('/places/detail/?id=7#place-notice-34', { scroll: false }));
    expect(await screen.findByText('읽음')).toBeTruthy();
  });

  it('전체 읽음은 CSRF 헤더로 처리하며 실패 후 재시도하고 만료된 알림도 갱신한다', async () => {
    mockMember();
    const user = userEvent.setup();
    const readAt = '2026-11-25T11:08:00';
    let current = { ...notice, noticeAvailable: false };
    let attempts = 0;
    server.use(
      http.get(inboxUrl, () => notificationPage([current])),
      http.get(`${inboxUrl}/unread-status`, () => HttpResponse.json({ hasUnread: current.readAt === null })),
      http.patch(`${inboxUrl}/read-all`, async ({ request }) => {
        expect(request.headers.get('X-CSRF-TOKEN')).toBe('test-token');
        expect(await request.text()).toBe('');
        if (++attempts === 1) return HttpResponse.json({ detail: '전체 알림을 읽음 처리하지 못했어요.' }, {
          status: 500,
          headers: { 'Content-Type': 'application/problem+json' },
        });
        current = { ...current, readAt };
        return HttpResponse.json({ updatedCount: 1 });
      })
    );
    renderInbox();

    const button = await screen.findByRole('button', { name: '전체 읽음' }) as HTMLButtonElement;
    expect(screen.queryByRole('link', { name: '공지 보기' })).toBeNull();
    await user.click(button);
    expect(await screen.findByRole('alert')).toHaveProperty('textContent', '전체 알림을 읽음 처리하지 못했어요.');
    expect(screen.getByText('읽지 않음')).toBeTruthy();
    await user.click(button);
    expect(await screen.findByText('읽음')).toBeTruthy();
    await waitFor(() => expect(button.disabled).toBe(true));
  });

  it('조회 오류 뒤 재시도할 수 있고 빈 목록을 안내한다', async () => {
    mockMember();
    const user = userEvent.setup();
    let failed = false;
    server.use(http.get(inboxUrl, () => {
      if (failed) return notificationPage([]);
      failed = true;
      return new HttpResponse(null, { status: 500 });
    }));
    renderInbox();

    expect(await screen.findByRole('alert')).toHaveProperty('textContent', '알림을 불러오지 못했어요. 다시 시도');
    await user.click(screen.getByRole('button', { name: '다시 시도' }));
    expect(await screen.findByText('받은 알림이 없어요.')).toBeTruthy();
  });

  it('목록 응답이 도착하기 전에는 로딩을 표시한다', async () => {
    mockMember();
    const response = Promise.withResolvers<void>();
    server.use(http.get(inboxUrl, async () => {
      await response.promise;
      return notificationPage([notice]);
    }));
    renderInbox();

    expect(await screen.findByRole('status')).toHaveProperty('textContent', '알림을 불러오고 있어요.');
    response.resolve();
    expect(await screen.findByRole('link', { name: '공지 보기' })).toBeTruthy();
  });

  it('읽음 처리에 실패하면 미읽음 상태를 유지하고 오류를 표시한다', async () => {
    mockMember();
    const user = userEvent.setup();
    server.use(
      http.get(inboxUrl, () => notificationPage([{ ...notice, sourceUrl: 'ftp://example.com/holiday' }])),
      http.patch(`${inboxUrl}/101/read`, () => HttpResponse.json({ detail: '알림을 읽음 처리하지 못했어요.' }, {
        status: 500,
        headers: { 'Content-Type': 'application/problem+json' },
      }))
    );
    renderInbox();

    await user.click(await screen.findByRole('link', { name: '공지 보기' }));
    expect(await screen.findByRole('alert')).toHaveProperty('textContent', '알림을 읽음 처리하지 못했어요.');
    expect(screen.getByText('읽지 않음')).toBeTruthy();
    expect(push).not.toHaveBeenCalled();
  });
});
