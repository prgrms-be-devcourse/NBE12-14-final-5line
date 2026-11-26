// @vitest-environment jsdom

import '../../../../../../../../tests/setup-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { expect, it } from 'vitest';
import { createQueryClient } from '@/shared/lib/query/queryClient';
import type { AdminNotice, AdminNoticeSource } from '@/domains/admin/types/adminNotice.type';
import { server } from '../../../../../../../../tests/mocks/server';
import AdminNotices from './AdminNotices';

const api = 'https://api.outpick.test/api/v1';
const place = { id: 9, name: '눈마루 리조트', address: '평창군', latitude: 37.37, longitude: 128.39 };
const existingSource: AdminNoticeSource = {
  sourceRecordId: 51,
  url: 'https://example.com/place',
  type: 'OFFICIAL',
  verifiedFields: 'address',
  originUpdatedAt: null,
  verifiedAt: '2026-11-21T00:00:00',
};

it('검색한 장소에 새 출처로 초안을 등록하고 기존 출처로 바꾼 뒤 목록에서 확인한다', async () => {
  const noticeUrl = `${api}/admin/places/9/notices`;
  const noticeFields = {
    title: '휴무 안내',
    content: '정비로 쉬어갑니다.',
    validFrom: '2026-11-23T09:00:00',
    validUntil: null,
  };
  const createPayload = {
    ...noticeFields,
    source: { url: 'https://example.com/holiday', verifiedAt: '2026-11-22T00:00:00' },
  };
  const draftSource: AdminNoticeSource = {
    ...existingSource,
    ...createPayload.source,
    sourceRecordId: 52,
    verifiedFields: 'notice',
  };
  let notice: AdminNotice = { ...noticeFields, placeNoticeId: 7, publishStatus: 'DRAFT', source: draftSource };
  let created = false;
  let posted: unknown;
  let patched: unknown;
  server.use(
    http.get(`${api}/places`, ({ request }) => {
      const matches = new URL(request.url).searchParams.get('query') === '눈마루';
      return HttpResponse.json({ items: matches ? [place] : [], page: 0, size: 8, totalElements: Number(matches) });
    }),
    http.get(`${api}/places/9`, () => HttpResponse.json({ ...place, sources: [existingSource] })),
    http.get(`${api}/auth/csrf`, () => HttpResponse.json({ headerName: 'X-CSRF-TOKEN', token: 'notice-test-token' })),
    http.get(noticeUrl, () => HttpResponse.json({
      items: created ? [notice] : [],
      page: 0,
      size: 20,
      totalElements: Number(created),
    })),
    http.post(noticeUrl, async ({ request }) => {
      expect(request.headers.get('X-CSRF-TOKEN')).toBe('notice-test-token');
      posted = await request.json();
      created = true;
      return HttpResponse.json(notice, { status: 201 });
    }),
    http.patch(`${noticeUrl}/7`, async ({ request }) => {
      expect(request.headers.get('X-CSRF-TOKEN')).toBe('notice-test-token');
      patched = await request.json();
      notice = { ...notice, title: '수정한 휴무 안내', source: existingSource };
      return HttpResponse.json(notice);
    })
  );
  const user = userEvent.setup();
  render(<QueryClientProvider client={createQueryClient()}><AdminNotices /></QueryClientProvider>);

  await user.type(screen.getByRole('searchbox', { name: '장소명' }), '눈마루');
  await user.click(screen.getByRole('button', { name: '검색' }));
  await user.click(await screen.findByRole('button', { name: /눈마루 리조트/ }));
  await screen.findByText('등록된 공지가 없어요.');
  await user.click(screen.getByRole('button', { name: '새 공지' }));

  const createForm = within(screen.getByRole('form', { name: '새 공지 작성' }));
  expect(document.activeElement).toBe(createForm.getByRole('heading', { name: '새 공지 작성' }));
  await user.type(createForm.getByRole('textbox', { name: '제목' }), createPayload.title);
  await user.type(createForm.getByRole('textbox', { name: '본문' }), createPayload.content);
  await user.type(createForm.getByRole('textbox', { name: '공식 출처 URL' }), createPayload.source.url);
  fireEvent.change(createForm.getByLabelText('확인일'), { target: { value: '2026-11-22' } });
  fireEvent.change(createForm.getByLabelText('시작 시각'), { target: { value: '2026-11-23T09:00' } });
  await user.click(createForm.getByRole('button', { name: '초안 저장' }));

  await screen.findByText('공지 초안을 저장했어요.');
  const edit = screen.getByRole('button', { name: '휴무 안내 수정' });
  expect(posted).toEqual(createPayload);
  const list = within(screen.getByRole('region', { name: '공지 목록' }));
  expect(list.getByText(createPayload.source.url)).toBeTruthy();
  expect(list.getByText('2026-11-22')).toBeTruthy();
  await user.click(edit);

  const editForm = within(screen.getByRole('form', { name: '공지 초안 수정' }));
  const title = editForm.getByRole('textbox', { name: '제목' });
  const source = editForm.getByRole('combobox', { name: '연결할 출처' });
  expect(source).toHaveProperty('value', '52');
  await user.clear(title);
  await user.type(title, '수정한 휴무 안내');
  await user.selectOptions(source, '51');
  await user.click(editForm.getByRole('button', { name: '초안 저장' }));

  expect(await list.findByRole('heading', { name: '수정한 휴무 안내' })).toBeTruthy();
  expect(list.getByText(existingSource.url ?? '')).toBeTruthy();
  expect(patched).toEqual({ ...noticeFields, title: '수정한 휴무 안내', sourceRecordId: 51 });
});

it('즉시 공개 재시도에 요청 ID를 재사용하고 같은 공지를 숨기고 다시 공개한다', async () => {
  const noticeUrl = `${api}/admin/places/9/notices`;
  let notice: AdminNotice | null = null;
  let originalRequestId: string | null = null;
  server.use(
    http.get(`${api}/places`, () => HttpResponse.json({ items: [place], page: 0, size: 8, totalElements: 1 })),
    http.get(`${api}/places/9`, () => HttpResponse.json({ ...place, sources: [existingSource] })),
    http.get(`${api}/auth/csrf`, () => HttpResponse.json({ headerName: 'X-CSRF-TOKEN', token: 'notice-test-token' })),
    http.get(noticeUrl, () => HttpResponse.json({
      items: notice ? [notice] : [],
      page: 0,
      size: 20,
      totalElements: Number(notice !== null),
    })),
    http.post(noticeUrl, async ({ request }) => {
      const params = new URL(request.url).searchParams;
      expect(params.get('publish')).toBe('true');
      const requestId = params.get('publicationRequestId');
      expect(requestId).toMatch(/^[0-9a-f-]{36}$/i);
      expect(await request.json()).toMatchObject({ title: '운영시간 안내', sourceRecordId: 51 });
      if (!originalRequestId) {
        originalRequestId = requestId;
        return HttpResponse.error();
      }
      expect(requestId).toBe(originalRequestId);
      notice = {
        placeNoticeId: 7,
        title: '운영시간 안내',
        content: '오늘은 9시에 엽니다.',
        validFrom: null,
        validUntil: null,
        publishStatus: 'PUBLISHED',
        source: existingSource,
      };
      return HttpResponse.json(notice, { status: 201 });
    }),
    http.patch(`${noticeUrl}/7/visibility`, async ({ request }) => {
      const body: unknown = await request.json();
      if (!notice || typeof body !== 'object' || body === null || !('visible' in body) || typeof body.visible !== 'boolean') {
        return new HttpResponse(null, { status: 400 });
      }
      notice = { ...notice, publishStatus: body.visible ? 'PUBLISHED' : 'HIDDEN' };
      return HttpResponse.json(notice);
    })
  );
  const user = userEvent.setup();
  render(<QueryClientProvider client={createQueryClient()}><AdminNotices /></QueryClientProvider>);
  await user.click(await screen.findByRole('button', { name: /눈마루 리조트/ }));
  await user.click(screen.getByRole('button', { name: '새 공지' }));

  const form = within(screen.getByRole('form', { name: '새 공지 작성' }));
  await user.type(form.getByRole('textbox', { name: '제목' }), '운영시간 안내');
  await user.type(form.getByRole('textbox', { name: '본문' }), '오늘은 9시에 엽니다.');
  await user.click(form.getByRole('radio', { name: '기존 출처' }));
  await user.selectOptions(await form.findByRole('combobox', { name: '연결할 출처' }), '51');
  await user.click(form.getByRole('button', { name: '저장 후 공개' }));
  await form.findByRole('alert');
  await user.click(form.getByRole('button', { name: '저장 후 공개' }));

  await screen.findByText('공지를 공개했어요.');
  await user.click(screen.getByRole('button', { name: '운영시간 안내 숨기기' }));
  await screen.findByText('공지를 숨겼어요.');
  await user.click(screen.getByRole('button', { name: '운영시간 안내 재공개' }));
  await screen.findByText('공지를 공개했어요.');
  expect(screen.getByRole('button', { name: '운영시간 안내 숨기기' })).toBeTruthy();
});
