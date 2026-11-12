import { setupServer } from 'msw/node';

// 실제 포트를 여는 서버가 아니라 테스트 프로세스의 HTTP 요청을 가로채는 도구입니다.
// 공통 기본 응답은 두지 않고 각 테스트에서 server.use()로 필요한 응답을 등록합니다.
export const server = setupServer();
