import { describe, expect, it } from 'vitest';
import { readPlaceMapBounds, readPlaceMapParams, sameMapView, writePlaceMapParams } from './placeMapParams';

describe('지도 URL 상태', () => {
  it('중심과 확대 수준을 복원하고 좌표 정밀도를 정규화한다', () => {
    expect(readPlaceMapParams(new URLSearchParams('lat=37.12345678&lng=128.62&level=8')))
      .toEqual({ lat: 37.123457, lng: 128.62, level: 8 });
    expect(sameMapView({ lat: 37.12345678, lng: 128.62, level: 8 }, { lat: 37.123457, lng: 128.62, level: 8 })).toBe(true);
  });

  it.each([
    '', 'lat=37&lng=128', 'lat=&lng=128&level=8', 'lat=NaN&lng=128&level=8',
    'lat=91&lng=128&level=8', 'lat=37&lng=181&level=8',
    'lat=37&lng=128&level=0', 'lat=37&lng=128&level=15', 'lat=37&lng=128&level=2.5',
  ])('누락되거나 잘못된 값은 무시한다: %s', query => {
    expect(readPlaceMapParams(new URLSearchParams(query))).toBeNull();
  });

  it('필터와 다른 쿼리를 보존하며 지도 값만 갱신한다', () => {
    const params = writePlaceMapParams(new URLSearchParams('region=강원&from=share&lat=0'), { lat: 37.37, lng: 128.39, level: 7 });
    expect(params.get('region')).toBe('강원');
    expect(params.get('from')).toBe('share');
    expect(readPlaceMapParams(params)).toEqual({ lat: 37.37, lng: 128.39, level: 7 });
  });

  it('지도 표시 전에도 마지막 검색 영역을 복원하며 잘못된 경계는 무시한다', () => {
    const bounds = { south: 37.1234567, west: 128.1234567, north: 38.1234567, east: 129.1234567 };
    const params = writePlaceMapParams(new URLSearchParams(), { lat: 37.5, lng: 128.5, level: 8 }, bounds);
    expect(readPlaceMapBounds(params)).toEqual({ south: 37.123456, west: 128.123456, north: 38.123457, east: 129.123457 });
    params.set('south', '40');
    expect(readPlaceMapBounds(params)).toBeNull();
  });

  it('저장 정밀도에 맞는 지도 경계는 곱셈 오차가 있어도 그대로 저장한다', () => {
    const bounds = { south: 37.37, west: 128.39, north: 37.38, east: 128.66 };
    const params = writePlaceMapParams(new URLSearchParams(), { lat: 37.375, lng: 128.525, level: 8 }, bounds);

    expect(readPlaceMapBounds(params)).toEqual(bounds);
  });
});
