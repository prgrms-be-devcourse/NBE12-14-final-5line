// @vitest-environment jsdom
import '../../../../../../../tests/setup-dom';
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { PlaceOperatingInfoData } from '@/domains/place/types/placeApi.type';
import OperatingInfoPanel from './OperatingInfoPanel';

const info: PlaceOperatingInfoData = {
  operatingHours: '평일 10:30~19:30\n주말 09:30~19:30', restDays: '매주 월요일', feeInfo: '25,000원', contactPhone: '1855-3946',
  sourceUrl: 'https://www.data.go.kr/data/15101578/openapi.do', collectedAt: '2026-11-16T12:00:00',
  verificationStatus: 'NEEDS_REVIEW', verifiedAt: null, reviewNote: '공식 예약 시간과 차이가 있어 확인이 필요합니다.',
  officialSourceUrl: 'https://flystation.kr/usage/usage_020100.html', comparedAt: '2026-11-16T13:00:00',
};

describe('수집된 운영 안내', () => {
  it('수집 값과 확인 필요 상태를 보여주며 수집일을 검수일로 표시하지 않는다', () => {
    render(<OperatingInfoPanel info={info} />);
    expect(screen.getByText('확인 필요')).toBeTruthy();
    expect(screen.getByText('매주 월요일')).toBeTruthy();
    expect(screen.getByText('25,000원')).toBeTruthy();
    expect(screen.getByText('1855-3946')).toBeTruthy();
    expect(screen.getByText('2026-11-16 12:00:00 (한국 시간)')).toBeTruthy();
    expect(screen.getByText('검수 완료 전')).toBeTruthy();
    expect(screen.getByText(info.reviewNote!)).toBeTruthy();
    expect(screen.getByRole('link', { name: '대조한 공식 안내 확인 ↗' }).getAttribute('href')).toBe(info.officialSourceUrl);
  });

  it('수집하지 않은 장소와 제공되지 않은 개별 항목을 구분한다', () => {
    const view = render(<OperatingInfoPanel info={null} />);
    expect(screen.getByText(/아직 수집된 운영 안내가 없습니다/)).toBeTruthy();
    view.rerender(<OperatingInfoPanel info={{ ...info, operatingHours: null, restDays: null }} />);
    expect(screen.getAllByText('제공된 정보 없음')).toHaveLength(2);
  });

  it('실제 검수일이 있을 때에만 검수된 안내로 표시한다', () => {
    const view = render(<OperatingInfoPanel info={{ ...info, verificationStatus: 'VERIFIED', verifiedAt: null }} />);
    expect(screen.queryByText('검수된 안내')).toBeNull();
    view.rerender(<OperatingInfoPanel info={{ ...info, verificationStatus: 'VERIFIED', verifiedAt: '2026-11-17T09:00:00' }} />);
    expect(screen.getByText('검수된 안내')).toBeTruthy();
    expect(screen.getByText('2026-11-17 09:00:00')).toBeTruthy();
  });
});

describe('해수욕장 요금 표시', () => {
  it('요금이 없으면 요금 행을 숨기고 운영시간은 유지한다', () => {
    render(<OperatingInfoPanel info={{ ...info, feeInfo: null }} hideMissingFee />);
    expect(screen.queryByText('이용요금')).toBeNull();
    expect(screen.getByText('매주 월요일')).toBeTruthy();
  });
  it('등록된 주차·편의시설 요금은 해수욕장에서도 표시한다', () => {
    render(<OperatingInfoPanel info={{ ...info, feeInfo: '주차 3,000원' }} hideMissingFee />);
    expect(screen.getByText('주차 3,000원')).toBeTruthy();
  });
});
