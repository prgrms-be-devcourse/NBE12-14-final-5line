// @vitest-environment jsdom

import '../../../../../../tests/setup-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { usePathname } from 'next/navigation';
import { describe, expect, it, vi } from 'vitest';
import NotificationList from '@/domains/notification/components/NotificationList';
import NotificationMenu from '@/domains/notification/components/NotificationMenu';
import { notificationUnreadStatusQuery } from '@/domains/notification/queries/notificationQueries';
import { createQueryClient } from '@/shared/lib/query/queryClient';
import { server } from '../../../../../../tests/mocks/server';
import AccountSidebar from './AccountSidebar';

vi.mock('next/navigation', () => ({ usePathname: vi.fn(), useRouter: () => ({ push: vi.fn() }) }));

const member = { id: 9, role: 'USER' } as const;
const api = 'https://api.outpick.test/api/v1';
const inboxUrl = `${api}/members/me/notifications`;

describe('공통 사이드바', () => {
  it('일반 회원에게 공통 메뉴만 표시하고 현재 페이지를 강조한다', () => {
    vi.mocked(usePathname).mockReturnValue('/mypage/favorites');

    const client = createQueryClient();
    client.setQueryData(notificationUnreadStatusQuery(member.id).queryKey, false);
    render(<QueryClientProvider client={client}><AccountSidebar member={member} /></QueryClientProvider>);

    const menu = within(screen.getByRole('navigation', { name: '공통 메뉴' }));
    expect(menu.getAllByRole('link').map(link => link.textContent)).toEqual(['마이페이지', '내 제보 상태', '관심 장소', '알림함']);
    expect(menu.getByRole('link', { name: '관심 장소', current: 'page' })).toBeTruthy();
    expect(screen.queryByRole('navigation', { name: '관리자 메뉴' })).toBeNull();
  });

  it('상단과 메뉴의 미읽음 표시가 조회를 공유하고 마지막 알림의 공지를 열면 함께 사라진다', async () => {
    vi.mocked(usePathname).mockReturnValue('/mypage/notifications');
    let readAt: string | null = null;
    const status = vi.fn(() => HttpResponse.json({ hasUnread: !readAt }));
    server.use(
      http.get(`${inboxUrl}/unread-status`, status),
      http.get(`${api}/auth/csrf`, () => HttpResponse.json({ token: 'test-token', headerName: 'X-CSRF-TOKEN' })),
      http.get(inboxUrl, () => HttpResponse.json({
        items: [{ notificationId: 101, placeId: 7, placeNoticeId: 34, title: '휴무 안내',
          createdAt: '2026-11-25T11:03:05', readAt, noticeAvailable: true }],
        page: 0, size: 20, totalElements: 1,
      })),
      http.patch(`${inboxUrl}/101/read`, () => {
        readAt = '2026-11-25T11:08:00';
        return HttpResponse.json({ notificationId: 101, readAt });
      })
    );
    const user = userEvent.setup();
    render(
      <QueryClientProvider client={createQueryClient()}>
        <NotificationMenu memberId={member.id} iconClassName="" />
        <AccountSidebar member={member} />
        <NotificationList memberId={member.id} />
      </QueryClientProvider>
    );

    const unread = { name: '알림함', description: '읽지 않은 알림 있음' };
    expect(await screen.findByRole('button', unread)).toBeTruthy();
    expect(screen.getByRole('link', unread)).toBeTruthy();
    expect(status).toHaveBeenCalledTimes(1);
    await user.click(await screen.findByRole('link', { name: '공지 보기' }));
    await waitFor(() => expect(screen.queryAllByText('읽지 않은 알림 있음')).toHaveLength(0));
    expect(status).toHaveBeenCalledTimes(2);
  });

  it.each([
    ['/admin/', '운영 현황'],
    ['/admin/places', '장소 관리'],
    ['/admin/operating-changes/', '운영 정보 검수'],
    ['/admin/notices/', '공지 관리'],
    ['/admin/place-reports/', '오류 제보'],
    ['/admin/review-reports/', '후기 신고'],
    ['/mypage/', '마이페이지'],
  ])('운영자에게 두 메뉴를 표시하고 %s에서 %s 항목만 강조한다', (pathname, label) => {
    vi.mocked(usePathname).mockReturnValue(pathname);

    render(<AccountSidebar member={{ id: 1, role: 'ADMIN' }} />);

    const menu = within(screen.getByRole('navigation', { name: '관리자 메뉴' }));
    const commonMenu = within(screen.getByRole('navigation', { name: '공통 메뉴' }));
    expect(screen.queryByText('공통 메뉴')).toBeNull();
    expect(screen.queryByText('관리자 메뉴')).toBeNull();
    expect(commonMenu.getAllByRole('link').map(link => link.textContent)).toEqual(['마이페이지']);
    expect(menu.getAllByRole('link').map(link => link.textContent)).toEqual(['운영 현황', '장소 관리', '운영 정보 검수', '공지 관리', '오류 제보', '후기 신고']);
    expect(screen.getAllByRole('link', { current: 'page' })).toEqual([screen.getByRole('link', { name: label })]);
  });
});
