// @vitest-environment jsdom
import { afterEach, describe, expect, it } from 'vitest';
import { consumeResetToken } from './consumeResetToken';

describe('비밀번호 재설정 링크', () => {
  afterEach(() => window.history.replaceState(null, '', '/'));

  it('메일 링크의 fragment에서 토큰을 읽고 주소창에서는 제거한다', () => {
    window.history.replaceState(null, '', '/reset-password/#token=abc123');

    expect(consumeResetToken(window.location, window.history)).toBe('abc123');
    expect(window.location.href).not.toContain('abc123');
    expect(window.location.pathname).toBe('/reset-password/');
  });

  it('이전에 발급된 query 링크도 읽은 뒤 URL에서 토큰을 지운다', () => {
    window.history.replaceState(null, '', '/reset-password/?token=legacy&source=mail');

    expect(consumeResetToken(window.location, window.history)).toBe('legacy');
    expect(window.location.search).toBe('?source=mail');
  });
});
