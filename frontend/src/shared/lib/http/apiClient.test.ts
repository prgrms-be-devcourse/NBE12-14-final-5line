import { delay, http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '../../../../tests/mocks/server';
import { apiClient } from './apiClient';

const url = 'https://api.outpick.test/api/check';

describe('공통 HTTP 클라이언트', () => {
  it('쿠키 전송을 허용하고 기본 타임아웃을 10초로 설정한다', () => {
    expect(apiClient.defaults.withCredentials).toBe(true);
    expect(apiClient.defaults.timeout).toBe(10_000);
  });

  it('HTML 오류 응답은 본문을 노출하지 않고 상태와 공통 안내를 반환한다', async () => {
    server.use(http.get(url, () => new HttpResponse('<html>proxy error</html>', {
      status: 502,
      headers: { 'Content-Type': 'text/html' },
    })));

    await expect(apiClient.get('/api/check')).rejects.toMatchObject({
      message: '요청을 처리할 수 없습니다.',
      details: { kind: 'http', status: 502, fieldErrors: [] },
    });
  });

  it('일반 JSON 오류는 ProblemDetail로 해석하지 않고 공통 안내를 반환한다', async () => {
    server.use(http.get(url, () => HttpResponse.json({ detail: '내부 구현 정보' }, { status: 500 })));

    await expect(apiClient.get('/api/check')).rejects.toMatchObject({
      message: '요청을 처리할 수 없습니다.',
      details: { kind: 'http', status: 500, fieldErrors: [] },
    });
  });

  it('ProblemDetail의 잘못된 필드 타입을 무시하고 HTTP 응답 상태를 유지한다', async () => {
    server.use(http.get(url, () => HttpResponse.json({
      status: 200,
      detail: 123,
      code: {},
      requestId: 123,
      fieldErrors: [null, { field: 3, message: false }, { field: 'name', message: '필수입니다.' }],
    }, {
      status: 400,
      headers: { 'Content-Type': 'application/problem+json' },
    })));

    await expect(apiClient.get('/api/check')).rejects.toMatchObject({
      details: {
        kind: 'http',
        status: 400,
        code: undefined,
        requestId: undefined,
        fieldErrors: [{ field: 'name', message: '필수입니다.' }],
      },
    });
  });

  it('응답을 받지 못한 연결 실패를 network 오류로 구분한다', async () => {
    server.use(http.get(url, () => HttpResponse.error()));

    await expect(apiClient.get('/api/check')).rejects.toMatchObject({
      details: { kind: 'network' },
    });
  });

  it('요청별 제한 시간 안에 응답하지 않으면 timeout 오류로 구분한다', async () => {
    server.use(http.get(url, async () => {
      await delay(100);
      return HttpResponse.json({ ok: true });
    }));

    await expect(apiClient.get('/api/check', { timeout: 20 })).rejects.toMatchObject({
      details: { kind: 'timeout' },
    });
  });

  it('AbortSignal로 취소한 요청을 canceled 오류로 구분한다', async () => {
    const controller = new AbortController();
    controller.abort();

    await expect(apiClient.get('/api/check', { signal: controller.signal })).rejects.toMatchObject({
      name: 'ApiError',
      details: { kind: 'canceled' },
    });
  });
});
