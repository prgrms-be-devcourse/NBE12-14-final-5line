// @vitest-environment jsdom

import '../../../../tests/setup-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { createQueryClient } from '@/shared/lib/query/queryClient';
import { getOperatingChanges, resolveOperatingChange } from '../api/adminOperatingChangeApi';
import AdminOperatingChanges from './AdminOperatingChanges';

vi.mock('../api/adminOperatingChangeApi');

describe('운영 정보 검수 화면', () => {
  it('같은 장소의 항목을 한 카드에 표시하고 반려한 항목만 목록에서 제거한다', async () => {
    const sources = {
      previousSourceUrl: 'https://example.com/previous', newSourceUrl: 'https://example.com/next',
      detectedAt: '2026-11-26T10:00:00',
    };
    const hours = { candidateId: 2, field: 'operatingHours', previousValue: '09:00', newValue: '10:00', ...sources };
    const fee = { candidateId: 1, field: 'usageFee', previousValue: null, newValue: '65,000원', ...sources };
    const place = { placeId: 2, placeName: '테스트 시설' };
    vi.mocked(getOperatingChanges).mockResolvedValueOnce({
      items: [{ ...place, candidates: [hours, fee] }],
      page: 0, size: 20, totalElements: 1,
    }).mockResolvedValue({ items: [{ ...place, candidates: [hours] }], page: 0, size: 20, totalElements: 1 });
    vi.mocked(resolveOperatingChange).mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(<QueryClientProvider client={createQueryClient()}><AdminOperatingChanges /></QueryClientProvider>);

    const card = within(await screen.findByRole('article', { name: '테스트 시설' }));
    expect(card.getByText('변경 2건')).toBeTruthy();
    expect(card.getByRole('region', { name: '이용시간' })).toBeTruthy();
    const feeReview = within(card.getByRole('region', { name: '이용요금' }));
    expect(feeReview.getByText('65,000원')).toBeTruthy();
    expect(feeReview.getByText('—')).toBeTruthy();
    const reject = feeReview.getByRole<HTMLButtonElement>('button', { name: '반려' });
    expect(reject.disabled).toBe(true);
    await user.type(feeReview.getByRole('textbox', { name: /검수 의견/ }), '공식 안내와 다름');
    await user.click(reject);

    expect(resolveOperatingChange).toHaveBeenCalledWith(expect.objectContaining({
      candidateId: 1, decision: 'REJECTED', reason: '공식 안내와 다름',
    }), expect.anything());
    expect(await card.findByText('변경 1건')).toBeTruthy();
    expect(card.queryByRole('region', { name: '이용요금' })).toBeNull();
    expect(card.getByRole('region', { name: '이용시간' })).toBeTruthy();
  });
});
