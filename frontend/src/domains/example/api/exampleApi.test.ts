import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '../../../../tests/mocks/server';
import { getExample, previewExample } from './exampleApi';

const baseURL = 'https://api.outpick.test';

describe('예시 도메인 API', () => {
  it('조회 요청의 ID를 경로에 전달하고 성공 DTO를 그대로 반환한다', async () => {
    const body = { name: '예시 액티비티', participantCount: 3 };
    server.use(http.get(`${baseURL}/api/examples/1`, () => HttpResponse.json(body)));

    expect(await getExample({ id: 1 })).toEqual(body);
  });

  it('미리보기 입력을 POST 본문으로 보내고 응답 DTO를 반환한다', async () => {
    const input = { name: '농구', participantCount: 5 };
    server.use(http.post(`${baseURL}/api/examples/preview`, async ({ request }) => {
      expect(await request.json()).toEqual(input);
      return HttpResponse.json(input);
    }));

    expect(await previewExample(input)).toEqual(input);
  });

  it('업무 오류의 HTTP 상태·설명·코드·요청 ID를 공통 오류에 유지한다', async () => {
    server.use(http.get(`${baseURL}/api/examples/2`, () => HttpResponse.json({
      type: 'about:blank',
      title: 'Not Found',
      status: 404,
      detail: '예시를 찾을 수 없습니다.',
      code: 'EXAMPLE_NOT_FOUND',
      requestId: 'example-request-id',
    }, {
      status: 404,
      headers: { 'Content-Type': 'application/problem+json' },
    })));

    await expect(getExample({ id: 2 })).rejects.toMatchObject({
      name: 'ApiError',
      message: '예시를 찾을 수 없습니다.',
      details: {
        kind: 'http',
        status: 404,
        code: 'EXAMPLE_NOT_FOUND',
        requestId: 'example-request-id',
        fieldErrors: [],
      },
    });
  });

  it('Validation 오류의 필드 이름과 안내 문구를 공통 오류에 유지한다', async () => {
    const fieldErrors = [
      { field: 'name', message: '이름을 입력해 주세요.' },
      { field: 'participantCount', message: '인원수는 1명 이상이어야 합니다.' },
    ];
    server.use(http.post(`${baseURL}/api/examples/preview`, () => HttpResponse.json({
      status: 400,
      detail: '입력값을 확인해 주세요.',
      code: 'VALIDATION_FAILED',
      requestId: 'validation-request-id',
      fieldErrors,
    }, {
      status: 400,
      headers: { 'Content-Type': 'application/problem+json; charset=utf-8' },
    })));

    await expect(previewExample({ name: '', participantCount: 0 })).rejects.toMatchObject({
      details: { kind: 'http', status: 400, code: 'VALIDATION_FAILED', fieldErrors },
    });
  });
});
