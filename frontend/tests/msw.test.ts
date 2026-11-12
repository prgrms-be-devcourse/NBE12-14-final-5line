import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from './mocks/server';

// 실제 Backend 주소와 분리한 테스트 전용 URL입니다. 네트워크 서버를 실행하지 않습니다.
const exampleUrl = 'https://api.outpick.test/examples/1';

describe('Vitest와 MSW 연결', () => {
  it('등록한 성공 응답의 상태 코드와 JSON 본문을 읽는다', async () => {
    const body = { id: 1, name: '테스트 장소' };
    server.use(http.get(exampleUrl, () => HttpResponse.json(body)));

    const response = await fetch(exampleUrl);

    expect(response.status).toBe(200);
    expect(response.headers.get('content-type')).toContain('application/json');
    expect(await response.json()).toEqual(body);
  });

  it('등록한 실패 응답의 404 상태와 ProblemDetail Content-Type 및 본문을 읽는다', async () => {
    const problem = {
      type: 'about:blank',
      title: 'Not Found',
      status: 404,
      detail: '예시를 찾을 수 없습니다.',
      instance: '/examples/1',
      code: 'EXAMPLE_NOT_FOUND',
      requestId: 'test-request-id',
    };
    server.use(http.get(exampleUrl, () => HttpResponse.json(problem, {
      status: 404,
      headers: { 'Content-Type': 'application/problem+json' },
    })));

    const response = await fetch(exampleUrl);

    expect(response.status).toBe(404);
    expect(response.headers.get('content-type')).toContain('application/problem+json');
    expect(await response.json()).toEqual(problem);
  });
});
