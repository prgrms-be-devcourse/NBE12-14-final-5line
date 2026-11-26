// @vitest-environment jsdom

import '../../../../tests/setup-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { createQueryClient } from '@/shared/lib/query/queryClient';
import { getOperatingChanges, resolveOperatingChange } from '../api/adminOperatingChangeApi';
import AdminOperatingChanges from './AdminOperatingChanges';

vi.mock('../api/adminOperatingChangeApi');

describe('운영 정보 검수 화면', () => {
  it('이전 값이 비었으면 대시를 보이고 사유를 입력한 뒤 반려하면 목록을 갱신한다', async () => {
    vi.mocked(getOperatingChanges).mockResolvedValueOnce({
      items: [{
        candidateId: 1, placeId: 2, placeName: '테스트 시설', field: 'usageFee',
        previousValue: null, newValue: '65,000원', previousSourceUrl: 'https://example.com/previous',
        newSourceUrl: 'https://example.com/next', detectedAt: '2026-11-26T10:00:00',
      }],
      page: 0, size: 20, totalElements: 1,
    }).mockResolvedValue({ items: [], page: 0, size: 20, totalElements: 0 });
    vi.mocked(resolveOperatingChange).mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(<QueryClientProvider client={createQueryClient()}><AdminOperatingChanges /></QueryClientProvider>);

    expect(await screen.findByText('65,000원')).toBeTruthy();
    expect(screen.getByText('—')).toBeTruthy();
    const reject = screen.getByRole<HTMLButtonElement>('button', { name: '반려' });
    expect(reject.disabled).toBe(true);
    await user.type(screen.getByRole('textbox', { name: /검수 의견/ }), '공식 안내와 다름');
    await user.click(reject);

    expect(resolveOperatingChange).toHaveBeenCalledWith(expect.objectContaining({
      candidateId: 1, decision: 'REJECTED', reason: '공식 안내와 다름',
    }), expect.anything());
    expect(await screen.findByText('검수 대기 0건')).toBeTruthy();
  });
});
