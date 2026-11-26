// @vitest-environment jsdom

import '../../../../../tests/setup-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { createQueryClient } from '@/shared/lib/query/queryClient';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';
import { server } from '../../../../../tests/mocks/server';
import MemberProfileMenu from './MemberProfileMenu';

const memberUrl = 'https://api.outpick.test/api/v1/members/me';
vi.mock('next/navigation', () => ({ useRouter: () => ({ push: vi.fn() }) }));

function renderMenu(children?: React.ReactNode) {
  render(
    <QueryClientProvider client={createQueryClient()}>
      <MemberProfileMenu />
      {children}
    </QueryClientProvider>
  );
}

describe('탐색 화면의 프로필 메뉴', () => {
  it('운영자에게 마이페이지와 관리자 기능별 링크를 표시한다', async () => {
    server.use(http.get(memberUrl, () => HttpResponse.json({ id: 1, role: 'ADMIN' })));
    const user = userEvent.setup();
    renderMenu();

    await user.click(screen.getByRole('button', { name: '프로필 메뉴' }));

    await screen.findByRole('link', { name: '마이페이지' });
    expect(screen.getAllByRole('link').map(link => [
      link.textContent, link.getAttribute('href')?.replace(/\/$/, ''),
    ])).toEqual([
      ['마이페이지', '/mypage'],
      ['운영 현황', '/admin'],
      ['장소 관리', '/admin/places'],
      ['운영 정보 검수', '/admin/operating-changes'],
      ['공지 관리', '/admin/notices'],
      ['오류 제보', '/admin/place-reports'],
      ['후기 신고', '/admin/review-reports'],
    ]);
  });

  it('일반 회원에게 관심 장소와 알림 바로가기 및 마이페이지와 내 제보 상태를 표시한다', async () => {
    server.use(
      http.get(memberUrl, () => HttpResponse.json({ id: 2, role: 'USER' })),
      http.get(`${memberUrl}/notifications/unread-status`, () => HttpResponse.json({ hasUnread: false }))
    );
    const user = userEvent.setup();
    renderMenu();

    const favorite = await screen.findByRole('link', { name: '관심 장소' });
    expect(favorite.getAttribute('href')).toMatch(/^\/mypage\/favorites\/?$/);
    expect(screen.getByRole('button', { name: '알림함' }).getAttribute('aria-haspopup')).toBe('dialog');

    const button = screen.getByRole('button', { name: '프로필 메뉴' });
    await user.click(button);

    const menu = document.getElementById(button.getAttribute('aria-controls') ?? '');
    expect([...menu?.querySelectorAll('a') ?? []].map(link => [
      link.textContent, link.getAttribute('href')?.replace(/\/$/, ''),
    ])).toEqual([['마이페이지', '/mypage'], ['내 제보 상태', '/mypage/place-reports']]);
  });

  it('미읽음 모달의 모든 알림을 읽으면 첫 페이지로 돌아가고 빨간 점이 사라진다', async () => {
    const modal = HTMLDialogElement.prototype;
    Object.defineProperties(modal, {
      showModal: { configurable: true, value(this: HTMLDialogElement) { this.open = true; } },
      close: { configurable: true, value(this: HTMLDialogElement) {
        this.open = false;
        this.dispatchEvent(new Event('close'));
      } },
    });
    try {
      const inboxUrl = `${memberUrl}/notifications`;
      const notice = {
        notificationId: 101, eventId: 21, placeId: 7, placeNoticeId: 34,
        title: '휴무 안내', createdAt: '2026-11-25T11:03:05', readAt: null,
        sourceUrl: null, noticeAvailable: true,
      };
      let read = false;
      const list = vi.fn(({ request }: { request: Request }) => {
        const params = new URL(request.url).searchParams;
        expect(params.get('unreadOnly')).toBe('true');
        return HttpResponse.json({ items: read ? [] : [notice], page: Number(params.get('page')), size: 20,
          totalElements: read ? 0 : 21 });
      });
      server.use(
        http.get(memberUrl, () => HttpResponse.json({ id: 2, role: 'USER' })),
        http.get(`${memberUrl.replace('/members/me', '')}/auth/csrf`, () =>
          HttpResponse.json({ token: 'test-token', headerName: 'X-CSRF-TOKEN' })),
        http.get(inboxUrl, list),
        http.get(`${inboxUrl}/unread-status`, () => HttpResponse.json({ hasUnread: !read })),
        http.patch(`${inboxUrl}/read-all`, () => {
          read = true;
          return HttpResponse.json({ updatedCount: 21 });
        })
      );
      const user = userEvent.setup();
      renderMenu();

      const bell = await screen.findByRole('button', { name: '알림함', description: '읽지 않은 알림 있음' });
      expect(list).not.toHaveBeenCalled();
      await user.click(bell);
      const dialog = within(screen.getByRole('dialog', { name: '읽지 않은 알림' }));
      expect(dialog.getByRole('link', { name: '전체 알림함 보기' }).getAttribute('href')).toMatch(/^\/mypage\/notifications\/?$/);
      await user.click(await dialog.findByRole('button', { name: '다음' }));
      expect(await dialog.findByText('2페이지')).toBeTruthy();
      await user.click(dialog.getByRole('button', { name: '전체 읽음' }));
      expect(await dialog.findByText('읽지 않은 알림이 없어요.')).toBeTruthy();
      expect(dialog.queryByRole('heading', { name: '휴무 안내' })).toBeNull();
      expect(dialog.queryByText('2페이지')).toBeNull();
      await user.click(dialog.getByRole('button', { name: '알림함 닫기' }));
      expect(screen.queryByRole('dialog')).toBeNull();
      expect(await screen.findByRole('button', { name: '알림함' })).toBeTruthy();
    } finally {
      Reflect.deleteProperty(modal, 'showModal');
      Reflect.deleteProperty(modal, 'close');
    }
  });

  it('비로그인 상태에서는 로그인과 회원가입 링크만 표시한다', async () => {
    server.use(http.get(memberUrl, () => new HttpResponse(null, { status: 401 })));
    const user = userEvent.setup();
    renderMenu();

    await user.click(screen.getByRole('button', { name: '프로필 메뉴' }));

    expect((await screen.findByRole('link', { name: '로그인' })).getAttribute('href')).toMatch(/^\/login\/?$/);
    expect(screen.getByRole('link', { name: '회원가입' }).getAttribute('href')).toMatch(/^\/signup\/?$/);
    expect(screen.getAllByRole('link')).toHaveLength(2);
  });

  it('회원 조회가 실패하면 오류를 안내한다', async () => {
    server.use(http.get(memberUrl, () => HttpResponse.error()));
    const user = userEvent.setup();
    renderMenu();

    await user.click(screen.getByRole('button', { name: '프로필 메뉴' }));

    expect((await screen.findByRole('alert')).textContent).toBe('계정 정보를 확인하지 못했어요.');
    expect(screen.queryByRole('link')).toBeNull();
  });

  it('키보드로 링크에 이동하고 Escape로 닫으면 프로필 버튼으로 돌아온다', async () => {
    server.use(http.get(memberUrl, () => HttpResponse.json({ id: 1, role: 'ADMIN' })));
    const user = userEvent.setup();
    renderMenu();
    const button = screen.getByRole('button', { name: '프로필 메뉴' });

    await user.tab();
    expect(document.activeElement).toBe(button);
    await user.keyboard('{Enter}');
    const link = await screen.findByRole('link', { name: '마이페이지' });
    await user.tab();
    expect(document.activeElement).toBe(link);

    await user.keyboard('{Escape}');

    expect(button.getAttribute('aria-expanded')).toBe('false');
    expect(screen.queryByRole('link', { name: '마이페이지' })).toBeNull();
    expect(document.activeElement).toBe(button);
  });

  it('메뉴 바깥을 클릭하면 메뉴를 닫는다', async () => {
    server.use(http.get(memberUrl, () => new HttpResponse(null, { status: 401 })));
    const user = userEvent.setup();
    renderMenu(<button type="button">메뉴 바깥</button>);
    const button = screen.getByRole('button', { name: '프로필 메뉴' });

    await user.click(button);
    expect(button.getAttribute('aria-expanded')).toBe('true');
    await user.click(screen.getByRole('button', { name: '메뉴 바깥' }));

    expect(button.getAttribute('aria-expanded')).toBe('false');
  });
});
