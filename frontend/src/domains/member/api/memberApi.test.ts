import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '../../../../tests/mocks/server';
import { login, requestPasswordReset, validatePasswordReset, withdraw } from './memberApi';

const base = 'https://api.outpick.test/api/v1';

describe('회원 API 연결', () => {
  it('로그인 전에 CSRF 토큰을 받아 요청 헤더에 담는다', async () => {
    server.use(
      http.get(`${base}/auth/csrf`, () => HttpResponse.json({ token: 'csrf-123', headerName: 'X-CSRF-TOKEN' })),
      http.post(`${base}/auth/login`, async ({ request }) => {
        expect(request.headers.get('X-CSRF-TOKEN')).toBe('csrf-123');
        expect(await request.json()).toEqual({ email: 'test@example.com', password: 'secret' });
        return HttpResponse.json({ id: 1, email: 'test@example.com', nickname: '테스터' });
      })
    );
    await expect(login({ email: 'test@example.com', password: 'secret' }))
      .resolves.toMatchObject({ id: 1, nickname: '테스터' });
  });

  it('재설정 요청은 서버의 동일 안내 문구를 전달한다', async () => {
    server.use(
      http.get(`${base}/auth/csrf`, () => HttpResponse.json({ token: 'csrf-123', headerName: 'X-CSRF-TOKEN' })),
      http.post(`${base}/auth/password-reset/request`, () => HttpResponse.json({ message: '안내 메일이 발송됩니다.' }))
    );
    await expect(requestPasswordReset('unknown@example.com')).resolves.toBe('안내 메일이 발송됩니다.');
  });

  it('재설정 토큰 검증은 URL 대신 CSRF를 적용한 요청 본문으로 전송한다', async () => {
    server.use(
      http.get(`${base}/auth/csrf`, () => HttpResponse.json({ token: 'csrf-123', headerName: 'X-CSRF-TOKEN' })),
      http.post(`${base}/auth/password-reset/validate`, async ({ request }) => {
        expect(new URL(request.url).search).toBe('');
        expect(request.headers.get('X-CSRF-TOKEN')).toBe('csrf-123');
        expect(await request.json()).toEqual({ token: 'reset-secret' });
        return new HttpResponse(null, { status: 204 });
      })
    );
    await expect(validatePasswordReset('reset-secret')).resolves.toBeUndefined();
  });

  it('회원 탈퇴는 현재 비밀번호와 최종 확인을 함께 보낸다', async () => {
    server.use(
      http.get(`${base}/auth/csrf`, () => HttpResponse.json({ token: 'csrf-123', headerName: 'X-CSRF-TOKEN' })),
      http.delete(`${base}/members/me`, async ({ request }) => {
        expect(request.headers.get('X-CSRF-TOKEN')).toBe('csrf-123');
        expect(await request.json()).toEqual({ password: 'secret', confirmed: true });
        return HttpResponse.json({ message: '회원 탈퇴가 완료되었습니다.' });
      })
    );
    await expect(withdraw('secret')).resolves.toBe('회원 탈퇴가 완료되었습니다.');
  });
});
