// @vitest-environment jsdom

import '../../../../../tests/setup-dom';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { delay, http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '../../../../../tests/mocks/server';
import QueryProvider from '@/app/_providers/QueryProvider';
import MainExampleContent from './MainExampleContent';

const url = 'https://api.outpick.test/api/examples';

/** 실제 Provider·조회 Hook·Axios를 함께 실행해 화면에서 관찰하는 동작을 검증합니다. */
function renderExample() {
  return render(<QueryProvider><MainExampleContent /></QueryProvider>);
}

describe('일반 예시 화면의 서버 상태', () => {
  it('조회 중 안내를 표시하고 성공하면 이름과 참여 인원을 표시한다', async () => {
    server.use(http.get(`${url}/1`, async () => {
      await delay(50);
      return HttpResponse.json({ name: '예시 액티비티', participantCount: 3 });
    }));

    renderExample();

    expect(screen.getByRole('status').textContent).toBe('예시를 불러오는 중입니다.');
    expect(await screen.findByText('예시 액티비티')).toBeTruthy();
    expect(screen.getByText('3명')).toBeTruthy();
    expect(screen.queryByRole('status')).toBeNull();
  });

  it('업무 오류를 자동 재시도하지 않고 표시하며 다시 조회를 누르면 성공 결과로 바뀐다', async () => {
    let requests = 0;
    server.use(http.get(`${url}/1`, () => {
      requests += 1;
      return HttpResponse.json({ detail: '예시를 찾을 수 없습니다.' }, {
        status: 404,
        headers: { 'Content-Type': 'application/problem+json' },
      });
    }));
    const user = userEvent.setup();
    renderExample();

    expect((await screen.findByRole('alert')).textContent).toBe('예시를 찾을 수 없습니다.');
    expect(requests).toBe(1);

    server.use(http.get(`${url}/1`, () => {
      requests += 1;
      return HttpResponse.json({ name: '재조회 결과', participantCount: 4 });
    }));
    await user.click(screen.getByRole('button', { name: '다시 조회' }));

    expect(await screen.findByText('재조회 결과')).toBeTruthy();
    expect(screen.queryByRole('alert')).toBeNull();
    expect(requests).toBe(2);
  });

  it('성공한 조회도 다시 조회 버튼을 누르면 새 응답으로 갱신한다', async () => {
    let requests = 0;
    server.use(http.get(`${url}/1`, () => {
      requests += 1;
      return HttpResponse.json({ name: `결과 ${requests}`, participantCount: 3 });
    }));
    const user = userEvent.setup();
    renderExample();
    await screen.findByText('결과 1');

    await user.click(screen.getByRole('button', { name: '다시 조회' }));

    expect(await screen.findByText('결과 2')).toBeTruthy();
    expect(requests).toBe(2);
  });

  it('ID별 결과를 구분하고 30초 안에 이전 ID로 돌아오면 캐시를 재사용한다', async () => {
    let firstRequests = 0;
    server.use(
      http.get(`${url}/1`, () => {
        firstRequests += 1;
        return HttpResponse.json({ name: '첫 번째 예시', participantCount: 1 });
      }),
      http.get(`${url}/2`, () => HttpResponse.json({ name: '두 번째 예시', participantCount: 2 }))
    );
    const user = userEvent.setup();
    renderExample();
    await screen.findByText('첫 번째 예시');

    await user.selectOptions(screen.getByRole('combobox', { name: '조회할 예시' }), '2');
    expect(await screen.findByText('두 번째 예시')).toBeTruthy();
    expect(screen.queryByText('첫 번째 예시')).toBeNull();

    await user.selectOptions(screen.getByRole('combobox', { name: '조회할 예시' }), '1');
    expect(await screen.findByText('첫 번째 예시')).toBeTruthy();
    await waitFor(() => expect(screen.queryByRole('status')).toBeNull());
    expect(firstRequests).toBe(1);
  });
});
