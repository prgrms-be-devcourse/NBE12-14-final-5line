// @vitest-environment jsdom
import '../../../../../tests/setup-dom';
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import PlacePhoto, { PlacePhotoCredit, getPlacePhoto } from './PlacePhoto';

const place = { id: 1, name: '시설', address: null, latitude: null, longitude: null,
  representativeImage: 'https://tong.visitkorea.or.kr/cms/resource/1/123_image2_1.JPG',
  imageAttribution: '한국관광공사 TourAPI 제공 · 시설 사진', imageSourceUrl: 'https://www.data.go.kr/data/15101578/openapi.do', imageLicense: 'Type3' };

describe('장소 사진과 출처', () => {
  it('원본 URL과 변경금지 이용조건·출처를 함께 표시한다', () => {
    render(<><PlacePhoto place={place} /><PlacePhotoCredit place={place} /></>);
    const image = screen.getByRole('img', { name: '시설 시설 사진' });
    expect(image.getAttribute('src')).toBe(place.representativeImage);
    expect(image.getAttribute('loading')).toBe('lazy');
    expect(screen.getByRole('link', { name: '사진 출처' }).getAttribute('href')).toBe(place.imageSourceUrl);
    expect(screen.getByRole('link', { name: '공공누리 제3유형' }).getAttribute('href')).toContain('licenseType3');
  });

  it('사진이 없거나 전송이 실패하면 대체 일러스트를 표시한다', () => {
    const view = render(<PlacePhoto place={place} />);
    fireEvent.error(screen.getByRole('img'));
    expect(screen.queryByRole('img')).toBeNull();
    expect(screen.getByText('OUTPICK · SAMPLE')).toBeTruthy();
    view.rerender(<PlacePhoto place={{ ...place, representativeImage: null }} />);
    expect(screen.queryByRole('img')).toBeNull();
  });

  it.each([1, 3])('간략 표기에서 제공기관명으로 출처를 연결하고 제%s유형을 표시한다', type => {
    render(<PlacePhotoCredit place={{ ...place, imageLicense: `Type${type}` }} compact />);
    const source = screen.getByRole('link', { name: '© 한국관광공사' });
    expect(source.getAttribute('href')).toBe(place.imageSourceUrl);
    expect(source.getAttribute('title')).toBe(place.imageAttribution);
    expect(screen.queryByRole('link', { name: '사진 출처' })).toBeNull();
    expect(screen.getByRole('link', { name: `공공누리 제${type}유형` }).getAttribute('href')).toContain(`licenseType${type}`);
  });

  it('권리정보가 없거나 허용하지 않은 이미지 URL이면 요청하지 않는다', () => {
    for (const patch of [{ imageLicense: null }, { imageAttribution: null }, { representativeImage: 'https://evil.example/image.jpg' },
      { representativeImage: place.representativeImage + '?key=secret' }, { imageSourceUrl: 'javascript:alert(1)' }]) {
      expect(getPlacePhoto({ ...place, ...patch })).toBeNull();
    }
  });

  it('장소 사진 URL이 바뀌면 이전 이미지의 실패 상태를 이어받지 않는다', () => {
    const view = render(<PlacePhoto place={place} />);
    fireEvent.error(screen.getByRole('img'));
    view.rerender(<PlacePhoto place={{ ...place, representativeImage: 'https://tong.visitkorea.or.kr/cms/resource/1/other.jpg' }} />);
    expect(screen.getByRole('img').getAttribute('src')).toContain('other.jpg');
  });
});
