// @vitest-environment jsdom

import '../../../../../tests/setup-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { expect, it } from 'vitest';
import { getQueryClient } from '@/shared/lib/query/queryClient';
import { server } from '../../../../../tests/mocks/server';
import type { AdminPlace, AdminPlaceDetail } from '../../types/adminPlace.type';
import AdminPlaces from '../AdminPlaces';

const api = 'https://api.outpick.test/api/v1';
const placesUrl = `${api}/admin/places`;
const capturedPlace: AdminPlace = { id: 9, name: '눈마루 리조트', address: '평창군', status: 'DRAFT' };
const manualPlace: AdminPlace = { id: 10, name: '수동 등록 장소', address: '테스트 주소', status: 'DRAFT' };
const sharedDetail = {
  regionId: 3, regionName: '강원특별자치도', nameAlias: null, latitude: 37.5, longitude: 127.1,
  description: '운영자 확인용 소개', homepage: null, activities: [], sources: [],
};
const regions = http.get(`${api}/places/filters`, () => HttpResponse.json({
  regions: [{ id: 3, name: '강원특별자치도' }], categories: [],
}));

function renderPlaces() {
  const user = userEvent.setup();
  render(<QueryClientProvider client={getQueryClient()}><AdminPlaces /></QueryClientProvider>);
  return user;
}

it('검수 전 목록에서 페이지를 이동하고 장소를 열어 등록된 종목과 미확인 출처를 확인한다', async () => {
  const capturedDetail: AdminPlaceDetail = {
    ...capturedPlace,
    ...sharedDetail,
    activities: [{ placeActivityId: 1, activityTypeId: 5, activityTypeName: '스키', environmentType: 'OUTDOOR', seasons: ['WINTER'] }],
    sources: [{ sourceRecordId: 3, type: 'TOUR_API', url: 'https://example.com/imported', verifiedFields: null, originUpdatedAt: null, verifiedAt: null }],
  };
  let detailRequests = 0;
  server.use(
    regions,
    http.get(placesUrl, ({ request }) => {
      const params = new URL(request.url).searchParams;
      expect(params.get('status')).toBe('DRAFT');
      const page = Number(params.get('page'));
      const query = params.get('query');
      const items = query ? (query === '눈마루' ? [capturedPlace] : []) : [page === 0 ? manualPlace : capturedPlace];
      return HttpResponse.json({ items, page, size: 20, totalElements: query ? items.length : 21 });
    }),
    http.get(`${placesUrl}/9`, () => ++detailRequests === 1
      ? new HttpResponse(null, { status: 500 })
      : HttpResponse.json(capturedDetail))
  );
  const user = renderPlaces();
  const review = within(screen.getByRole('region', { name: '검수 전 장소' }));

  await review.findByRole('button', { name: /수동 등록 장소/ });
  await user.click(review.getByRole('button', { name: '다음' }));
  await user.click(await review.findByRole('button', { name: /눈마루 리조트/ }));
  expect(document.activeElement).toBe(review.getByRole('heading', { name: '장소 상세' }));
  await review.findByRole('alert');
  await user.click(review.getByRole('button', { name: '다시 시도' }));
  await review.findByText('눈마루 리조트');
  expect(review.getByText('스키')).toBeTruthy();
  expect(review.getByText('겨울')).toBeTruthy();
  expect(review.getByText('https://example.com/imported')).toBeTruthy();
  expect(review.getByText('확인일 미확인')).toBeTruthy();
  await user.click(review.getByRole('button', { name: '목록으로' }));
  expect(review.getByText('2페이지')).toBeTruthy();

  await user.type(review.getByRole('searchbox'), '눈마루');
  await user.click(review.getByRole('button', { name: '검색' }));
  await review.findByRole('button', { name: /눈마루 리조트/ });
  expect(review.queryByRole('navigation')).toBeNull();
});

it('장소 초안을 저장하면 목록을 갱신하고 종목과 출처가 비어 있는 상세를 열 수 있다', async () => {
  let created = false;
  server.use(
    regions,
    http.get(`${api}/auth/csrf`, () => HttpResponse.json({ headerName: 'X-CSRF-TOKEN', token: 'place-test-token' })),
    http.get(placesUrl, () => HttpResponse.json({ items: created ? [manualPlace] : [], page: 0, size: 20, totalElements: Number(created) })),
    http.post(placesUrl, async ({ request }) => {
      expect(request.headers.get('X-CSRF-TOKEN')).toBe('place-test-token');
      expect(await request.json()).toEqual({
        regionId: 3, name: manualPlace.name, address: manualPlace.address,
        description: sharedDetail.description, latitude: 37.5, longitude: 127.1,
      });
      created = true;
      return HttpResponse.json(manualPlace, { status: 201 });
    }),
    http.get(`${placesUrl}/10`, () => HttpResponse.json({ ...manualPlace, ...sharedDetail }))
  );
  const user = renderPlaces();
  await screen.findByText('검수 전 장소가 없어요.');
  const form = within(screen.getByRole('form', { name: '새 장소' }));
  await user.type(form.getByRole('textbox', { name: '장소명' }), manualPlace.name ?? '');
  await user.type(form.getByRole('textbox', { name: '주소' }), manualPlace.address ?? '');
  await user.type(form.getByRole('textbox', { name: '소개' }), sharedDetail.description);
  await user.type(form.getByRole('spinbutton', { name: '위도' }), '37.5');
  await user.type(form.getByRole('spinbutton', { name: '경도' }), '127.1');
  await user.click(form.getByRole('button', { name: '비공개 초안 저장' }));

  await screen.findByText('장소 #10을 비공개 초안으로 저장했어요.');
  await user.click(await screen.findByRole('button', { name: /수동 등록 장소/ }));
  await screen.findByText(sharedDetail.description);
  expect(screen.getByText('등록된 종목이 없어요.')).toBeTruthy();
  expect(screen.getByText('등록된 출처가 없어요.')).toBeTruthy();
});
