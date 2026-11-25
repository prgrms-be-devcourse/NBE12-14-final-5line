// @vitest-environment jsdom

import '../../../../../../../../../tests/setup-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';
import FavoritePlaceButton from '@/app/(service)/places/detail/_components/FavoritePlaceButton';
import type { FavoritePlace } from '@/domains/favorite/api/favoriteApi';
import { createQueryClient } from '@/shared/lib/query/queryClient';
import { server } from '../../../../../../../../../tests/mocks/server';
import FavoritePlaces from './FavoritePlaces';

const api = 'https://api.outpick.test/api/v1';
const favoritesUrl = `${api}/members/me/favorites`;
const published = {
  id: 1,
  name: '등산 공원',
  address: '서울',
  latitude: null,
  longitude: null,
  publishStatus: 'PUBLISHED',
  isNotified: false,
} as const;
const hidden = { id: 2, name: '비공개 공원', publishStatus: 'HIDDEN' } as const;

function renderFavorites() {
  return render(
    <QueryClientProvider client={createQueryClient()}>
      <FavoritePlaceButton placeId={published.id} />
      <FavoritePlaces />
    </QueryClientProvider>
  );
}

function mockMember() {
  server.use(
    http.get(`${api}/members/me`, () => HttpResponse.json({ id: 9, role: 'USER' })),
    http.get(`${api}/auth/csrf`, () => HttpResponse.json({ token: 'test-token', headerName: 'X-CSRF-TOKEN' }))
  );
}

describe('관심 장소 관리', () => {
  it('비로그인 회원은 로그인으로 안내하고 관심 목록 요청을 보내지 않는다', async () => {
    const list = vi.fn(() => HttpResponse.json([]));
    server.use(
      http.get(`${api}/members/me`, () => new HttpResponse(null, { status: 401 })),
      http.get(favoritesUrl, list)
    );
    renderFavorites();

    const login = await screen.findByRole('link', { name: '로그인하고 관심 장소 저장' });
    expect(login.getAttribute('href')).toMatch(/^\/login\/?$/);
    expect(list).not.toHaveBeenCalled();
  });

  it('상세에서 저장하고 목록에서 알림을 켜고 끈 뒤 해제하면 두 화면에 반영한다', async () => {
    mockMember();
    const user = userEvent.setup();
    let favorites: FavoritePlace[] = [];
    const settings: boolean[] = [];
    server.use(
      http.get(favoritesUrl, () => HttpResponse.json(favorites)),
      http.post(favoritesUrl, async ({ request }) => {
        expect(request.headers.get('X-CSRF-TOKEN')).toBe('test-token');
        expect(await request.json()).toEqual({ placeId: 1 });
        favorites = [published];
        return HttpResponse.json({ placeId: 1, message: '관심 장소에 저장했습니다.' }, { status: 201 });
      }),
      http.patch(`${favoritesUrl}/1/notification-settings`, async ({ request }) => {
        expect(request.headers.get('X-CSRF-TOKEN')).toBe('test-token');
        const { isNotified } = await request.json() as { isNotified: boolean };
        settings.push(isNotified);
        favorites = [{ ...published, isNotified }];
        return HttpResponse.json({ placeId: 1, isNotified });
      }),
      http.delete(`${favoritesUrl}/1`, ({ request }) => {
        expect(request.headers.get('X-CSRF-TOKEN')).toBe('test-token');
        favorites = [];
        return HttpResponse.json({ placeId: 1, message: '관심 장소에서 해제했습니다.' });
      })
    );
    const view = renderFavorites();

    await user.click(await screen.findByRole('button', { name: '관심 장소 저장' }));
    expect(await screen.findByRole('button', { name: '관심 장소 해제' })).toBeTruthy();
    expect(screen.getByRole('link', { name: published.name }).getAttribute('href')).toMatch(/^\/places\/detail\/?\?id=1$/);
    const toggle = screen.getByRole<HTMLInputElement>('switch', { name: '등산 공원 변경 알림 수신' });
    expect(toggle.checked).toBe(false);

    await user.click(toggle);
    await waitFor(() => {
      expect(toggle.checked).toBe(true);
      expect(toggle.disabled).toBe(false);
    });
    view.unmount();
    renderFavorites();
    const reloadedToggle = await screen.findByRole<HTMLInputElement>('switch');
    expect(reloadedToggle.checked).toBe(true);

    await user.click(reloadedToggle);
    await waitFor(() => {
      expect(reloadedToggle.checked).toBe(false);
      expect(reloadedToggle.disabled).toBe(false);
    });
    expect(settings).toEqual([true, false]);

    await user.click(screen.getByRole('button', { name: '등산 공원 관심 해제' }));
    expect(await screen.findByRole('button', { name: '관심 장소 저장' })).toBeTruthy();
    expect(screen.getByText(/저장한 관심 장소가 없어요/)).toBeTruthy();
  });

  it('비공개 장소는 이름과 해제만 표시하고, 알림 변경 실패 시 이전 수신 상태를 유지한다', async () => {
    mockMember();
    const user = userEvent.setup();
    server.use(
      http.get(favoritesUrl, () => HttpResponse.json([hidden, published])),
      http.patch(`${favoritesUrl}/1/notification-settings`, () =>
        HttpResponse.json({ detail: '알림을 변경하지 못했어요.' }, {
          status: 500,
          headers: { 'Content-Type': 'application/problem+json' },
        })
      )
    );
    renderFavorites();

    expect(await screen.findByRole('heading', { name: hidden.name })).toBeTruthy();
    expect(screen.getByText('현재 볼 수 없는 장소')).toBeTruthy();
    expect(screen.queryByRole('link', { name: hidden.name })).toBeNull();
    expect(screen.queryByRole('switch', { name: /비공개 공원/ })).toBeNull();
    expect(screen.getByRole('button', { name: '비공개 공원 관심 해제' })).toBeTruthy();

    const toggle = screen.getByRole<HTMLInputElement>('switch');
    await user.click(toggle);
    expect(await screen.findByRole('alert')).toHaveProperty('textContent', '알림을 변경하지 못했어요.');
    expect(toggle.checked).toBe(false);
  });

  it('다른 화면에서 이미 저장한 장소는 409 응답 이후 저장 상태를 다시 조회한다', async () => {
    mockMember();
    const user = userEvent.setup();
    let favorites: FavoritePlace[] = [];
    server.use(
      http.get(favoritesUrl, () => HttpResponse.json(favorites)),
      http.post(favoritesUrl, () => {
        favorites = [published];
        return HttpResponse.json({ code: 'FAVORITE_ALREADY_EXISTS', detail: '이미 관심 장소에 저장된 장소입니다.' }, {
          status: 409,
          headers: { 'Content-Type': 'application/problem+json' },
        });
      })
    );
    renderFavorites();

    await user.click(await screen.findByRole('button', { name: '관심 장소 저장' }));
    expect(await screen.findByRole('button', { name: '관심 장소 해제' })).toBeTruthy();
    expect(await screen.findByText('이미 관심 장소에 저장된 장소입니다.')).toBeTruthy();
  });
});
