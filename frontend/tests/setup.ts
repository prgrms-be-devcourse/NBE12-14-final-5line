import { afterAll, afterEach, beforeAll } from 'vitest';
import { server } from './mocks/server';

/** 테스트 파일 실행 전에 요청 가로채기를 시작하고, 등록하지 않은 요청은 실패시킵니다. */
beforeAll(() => {
  server.listen({ onUnhandledFrame: 'error' });
});

/** 테스트마다 추가한 응답을 제거해 다음 테스트에 영향을 주지 않도록 합니다. */
afterEach(() => {
  server.resetHandlers();
});

/** 테스트 파일 실행이 끝나면 HTTP 요청 가로채기를 해제합니다. */
afterAll(() => {
  server.close();
});
