import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '../../../../tests/mocks/server';
import { searchPlaces } from './placeApi';
import type { PlaceSelection } from '../types/placeApi.type';

const selection: PlaceSelection = { query: '', category: '전체', region: '', season: '', environment: '', sort: 'name' };
const catalog = { regions: [{ id: 400, name: '강원' }], categories: [{ id: 90, name: '스키·보드', activityTypeIds: [800, 900] }] };

describe('장소 API 연결', () => {
  it('서버 ID·코드·범위를 직렬화하고 페이지 응답을 그대로 유지한다', async () => {
    server.use(http.get('https://api.outpick.test/api/v1/places', ({ request }) => {
      const params = new URL(request.url).searchParams;
      expect(params.get('regionId')).toBe('400');
      expect(params.getAll('activityTypeIds')).toEqual(['800', '900']);
      expect(params.get('season')).toBe('WINTER');
      expect(params.get('environmentType')).toBe('OUTDOOR');
      expect(params.get('page')).toBe('0');
      expect(params.get('size')).toBe('50');
      expect(params.get('sort')).toBe('name');
      expect(params.get('south')).toBe('37');
      return HttpResponse.json({ items: [], page: 0, size: 50, totalElements: 21 });
    }));
    expect(await searchPlaces({ ...selection, region: '강원', category: '스키·보드', season: '겨울', environment: '실외' },
      catalog, { south: 37, west: 127, north: 38, east: 129 })).toEqual({ items: [], page: 0, size: 50, totalElements: 21 });
  });
  it('현재 제공하지 않는 분류를 전체 검색으로 바꾸지 않는다', async () => {
    expect(await searchPlaces({ ...selection, category: '항공' }, catalog, null)).toEqual({ items: [], page: 0, size: 50, totalElements: 0 });
  });
  it('취소 신호를 Axios에 전달한다', async () => {
    const controller = new AbortController(); controller.abort();
    await expect(searchPlaces(selection, catalog, null, controller.signal)).rejects.toMatchObject({ details: { kind: 'canceled' } });
  });
});
