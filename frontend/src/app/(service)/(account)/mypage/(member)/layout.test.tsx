// @vitest-environment jsdom

import '../../../../../../tests/setup-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';
import { createQueryClient } from '@/shared/lib/query/queryClient';
import { server } from '../../../../../../tests/mocks/server';
import MypageLayout from '../layout';
import MemberOnlyLayout from './layout';

const { replace } = vi.hoisted(() => ({ replace: vi.fn() }));
vi.mock('next/navigation', () => ({ useRouter: () => ({ replace }) }));

const memberUrl = 'https://api.outpick.test/api/v1/members/me';
const pageHeading = '관심 장소';

function renderMemberPage() {
  replace.mockClear();

  render(
    <QueryClientProvider client={createQueryClient()}>
      <MypageLayout>
        <MemberOnlyLayout><h1>{pageHeading}</h1></MemberOnlyLayout>
      </MypageLayout>
    </QueryClientProvider>
  );
}

describe('일반 회원 전용 페이지 접근', () => {
  it('일반 회원이 전용 페이지를 열면 본문을 표시한다', async () => {
    server.use(http.get(memberUrl, () => HttpResponse.json({ id: 1, role: 'USER' })));
    renderMemberPage();

    expect(await screen.findByRole('heading', { name: pageHeading })).toBeTruthy();
    expect(replace).not.toHaveBeenCalled();
  });

  it('관리자가 전용 페이지를 열면 마이페이지로 보내고 전용 본문을 숨긴다', async () => {
    server.use(http.get(memberUrl, () => HttpResponse.json({ id: 2, role: 'ADMIN' })));
    renderMemberPage();

    await waitFor(() => expect(replace).toHaveBeenCalledExactlyOnceWith('/mypage/'));
    expect(screen.queryByRole('heading')).toBeNull();
  });

  it('비로그인 상태에서 전용 페이지를 열면 복귀 경로 없이 로그인으로 보낸다', async () => {
    server.use(http.get(memberUrl, () => new HttpResponse(null, { status: 401 })));
    renderMemberPage();

    await waitFor(() => expect(replace).toHaveBeenCalledExactlyOnceWith('/login/'));
    expect(screen.queryByRole('heading', { name: pageHeading })).toBeNull();
  });
});
