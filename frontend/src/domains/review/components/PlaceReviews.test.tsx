// @vitest-environment jsdom

import '../../../../tests/setup-dom';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { currentMember } from '@/domains/member/api/memberApi';
import { createReview, listReviews, uploadReviewPhoto } from '../api/reviewApi';
import PlaceReviews from './PlaceReviews';

vi.mock('@/domains/member/api/memberApi', () => ({ currentMember: vi.fn() }));
vi.mock('../api/reviewApi', () => ({
  createReview: vi.fn(), listReviews: vi.fn(), uploadReviewPhoto: vi.fn(),
}));
afterEach(() => vi.clearAllMocks());

describe('후기와 사진 함께 등록', () => {
  it('사진 업로드 실패 뒤 재시도해도 같은 후기를 중복 생성하지 않는다', async () => {
    vi.mocked(currentMember).mockResolvedValue({ id: 1, email: 'member@example.com', nickname: '회원' });
    vi.mocked(listReviews).mockResolvedValue([]);
    vi.mocked(createReview).mockResolvedValue({ id: 11, placeId: 3, authorId: 1,
      authorNickname: '회원', body: '방문 후기', createdAt: '', updatedAt: '', photos: [] });
    vi.mocked(uploadReviewPhoto).mockRejectedValueOnce(new Error('upload failed'))
      .mockResolvedValueOnce({ id: 7, url: '/photo/7', width: 10, height: 10 });

    render(<PlaceReviews placeId={3} />);
    const body = await screen.findByRole('textbox', { name: '방문 후기 남기기' });
    fireEvent.change(body, { target: { value: '방문 후기' } });
    const photo = new File(['photo'], 'visit.png', { type: 'image/png' });
    fireEvent.change(screen.getByLabelText(/사진 추가 \(최대 3장/), { target: { files: [photo] } });
    fireEvent.click(screen.getByRole('button', { name: '후기 등록' }));
    expect(await screen.findByRole('button', { name: '사진 업로드 다시 시도' })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: '사진 업로드 다시 시도' }));

    await waitFor(() => expect(screen.getByRole('button', { name: '후기 등록' })).toBeTruthy());
    expect(createReview).toHaveBeenCalledTimes(1);
    expect(uploadReviewPhoto).toHaveBeenCalledTimes(2);
    expect(uploadReviewPhoto).toHaveBeenNthCalledWith(2, 11, photo);
  });
});
