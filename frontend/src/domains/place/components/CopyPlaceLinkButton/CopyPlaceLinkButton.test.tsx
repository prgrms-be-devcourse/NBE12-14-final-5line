// @vitest-environment jsdom

import '../../../../../tests/setup-dom';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import CopyPlaceLinkButton from './CopyPlaceLinkButton';

afterEach(() => vi.restoreAllMocks());

describe('장소 링크 복사', () => {
  it('검색 주소는 버튼 클릭 직전의 필터와 지도 위치를 복사한다', async () => {
    const user = userEvent.setup();
    const write = vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue();
    render(<CopyPlaceLinkButton label="검색 링크 복사" />);
    window.history.replaceState(null, '', '/?region=강원&lat=37.37&lng=128.39&level=8');
    await user.click(screen.getByRole('button', { name: '검색 링크 복사' }));
    expect(write).toHaveBeenCalledExactlyOnceWith(window.location.href);
    expect(screen.getByRole('status').textContent).toBe('링크를 복사했어요.');
  });

  it('장소 링크는 목록 복귀 주소 없이 지정한 상세 주소만 복사한다', async () => {
    const user = userEvent.setup();
    const write = vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue();
    window.history.replaceState(null, '', '/places/detail/?id=1&returnTo=private-search');
    render(<CopyPlaceLinkButton label="장소 링크 복사" path="/places/detail/?id=1" />);
    await user.click(screen.getByRole('button', { name: '장소 링크 복사' }));
    expect(write).toHaveBeenCalledExactlyOnceWith(`${window.location.origin}/places/detail/?id=1`);
  });

  it('권한 거절 시 수동 복사할 주소를 제공하고 재시도할 수 있다', async () => {
    const user = userEvent.setup();
    const write = vi.spyOn(navigator.clipboard, 'writeText').mockRejectedValueOnce(new Error('denied')).mockResolvedValueOnce();
    render(<CopyPlaceLinkButton label="검색 링크 복사" />);
    await user.click(screen.getByRole('button', { name: '검색 링크 복사' }));
    expect((screen.getByRole('textbox', { name: '공유할 링크' }) as HTMLInputElement).value).toBe(window.location.href);
    expect(screen.getByRole('status').textContent).toContain('직접 복사');
    await user.click(screen.getByRole('button', { name: '검색 링크 복사' }));
    expect(write).toHaveBeenCalledTimes(2);
    expect(screen.queryByRole('textbox', { name: '공유할 링크' })).toBeNull();
    expect(screen.getByRole('status').textContent).toBe('링크를 복사했어요.');
  });
});
