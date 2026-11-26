// @vitest-environment jsdom

import '../../../../../tests/setup-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { createQueryClient } from '@/shared/lib/query/queryClient';
import { render, screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { server } from '../../../../../tests/mocks/server';
import MemberProfileMenu from '@/domains/member/components/MemberProfileMenu/MemberProfileMenu';
import AdminAccess from './AdminAccess';

const { replace } = vi.hoisted(() => ({ replace: vi.fn() }));
vi.mock('next/navigation', () => ({ useRouter: () => ({ replace }) }));

const memberUrl = 'https://api.outpick.test/api/v1/members/me';
const adminHeading = '관리자 페이지 본문';

function renderAdminPage() {
  render(
    <QueryClientProvider client={createQueryClient()}>
      <AdminAccess><h1>{adminHeading}</h1></AdminAccess>
    </QueryClientProvider>
  );
}

describe('관리자 공통 접근 확인', () => {
  beforeEach(() => replace.mockClear());

  it('권한을 빠르게 확인하면 로딩 표시 없이 관리자 본문을 표시한다', async () => {
    server.use(http.get(memberUrl, () => HttpResponse.json({ id: 1, role: 'ADMIN' })));

    renderAdminPage();

    expect(screen.queryByRole('status')).toBeNull();
    expect(screen.queryByRole('heading', { name: adminHeading })).toBeNull();
    expect(await screen.findByRole('heading', { name: adminHeading })).toBeTruthy();
    expect(screen.queryByRole('status')).toBeNull();
    expect(replace).not.toHaveBeenCalled();
  });

  it('회원 조회가 1초 이상 걸리면 로딩을 표시하고 응답이 오면 본문을 표시한다', async () => {
    const responseReady = Promise.withResolvers<void>();
    server.use(http.get(memberUrl, async () => {
      await responseReady.promise;
      return HttpResponse.json({ id: 1, role: 'ADMIN' });
    }));

    renderAdminPage();

    expect(screen.queryByRole('status')).toBeNull();
    expect((await screen.findByRole('status', {}, { timeout: 2000 })).textContent).toBe('운영자 권한을 확인하고 있어요.');
    expect(screen.queryByRole('heading', { name: adminHeading })).toBeNull();

    responseReady.resolve();

    expect(await screen.findByRole('heading', { name: adminHeading })).toBeTruthy();
    expect(screen.queryByRole('status')).toBeNull();
  });

  it('프로필 메뉴와 관리자 접근 확인이 회원 조회 요청을 공유한다', async () => {
    let requestCount = 0;
    server.use(http.get(memberUrl, () => {
      requestCount++;
      return HttpResponse.json({ id: 1, role: 'ADMIN' });
    }));
    const user = userEvent.setup();
    render(
      <QueryClientProvider client={createQueryClient()}>
        <MemberProfileMenu />
        <AdminAccess><h1>{adminHeading}</h1></AdminAccess>
      </QueryClientProvider>
    );

    await user.click(screen.getByRole('button', { name: '프로필 메뉴' }));

    expect(await screen.findByRole('link', { name: '운영 현황' })).toBeTruthy();
    expect(await screen.findByRole('heading', { name: adminHeading })).toBeTruthy();
    expect(requestCount).toBe(1);
  });

  it('일반 회원을 마이페이지로 보내고 관리자 본문을 숨긴다', async () => {
    server.use(http.get(memberUrl, () => HttpResponse.json({ id: 2, role: 'USER' })));

    renderAdminPage();

    await waitFor(() => expect(replace).toHaveBeenCalledExactlyOnceWith('/mypage/'));
    expect(screen.queryByRole('heading')).toBeNull();
    expect(screen.queryByRole('status')).toBeNull();
  });

  it('회원 조회가 401이면 복귀 경로 없이 로그인으로 보내고 관리자 본문을 숨긴다', async () => {
    server.use(http.get(memberUrl, () => new HttpResponse(null, { status: 401 })));

    renderAdminPage();

    await waitFor(() => expect(replace).toHaveBeenCalledExactlyOnceWith('/login/'));
    expect(screen.queryByRole('heading')).toBeNull();
  });

  it('회원 조회에 실패하면 본문 대신 오류 안내를 표시한다', async () => {
    server.use(http.get(memberUrl, () => HttpResponse.error()));

    renderAdminPage();

    expect((await screen.findByRole('alert')).textContent).toContain('계정 정보를 확인하지 못했어요.');
    expect(screen.queryByRole('heading', { name: adminHeading })).toBeNull();
    expect(replace).not.toHaveBeenCalled();
  });
});
