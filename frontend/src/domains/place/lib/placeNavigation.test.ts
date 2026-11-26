// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { getListReturnUrl, getPlaceDetailUrl, rememberPlaceListPosition, takePlaceListPosition } from './placeNavigation';

beforeEach(() => {
  sessionStorage.clear();
  window.history.replaceState(null, '', '/?region=강원&season=겨울');
});

afterEach(() => {
  sessionStorage.clear();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('장소 목록과 상세 이동', () => {
  it('상세 URL에 장소 ID와 원래 목록의 검색 조건을 함께 담는다', () => {
    const url = new URL(getPlaceDetailUrl(1, '/?region=강원&season=겨울'), window.location.origin);
    expect(url.pathname).toBe('/places/detail/');
    expect(url.searchParams.get('id')).toBe('1');
    const returnUrl = new URL(url.searchParams.get('returnTo')!, window.location.origin);
    expect(returnUrl.searchParams.get('region')).toBe('강원');
    expect(returnUrl.searchParams.get('season')).toBe('겨울');
  });

  it.each(['https://evil.example/', '//evil.example/', '/admin/', '/\\evil.example/', 'javascript:alert(1)', null])(
    '목록 이외의 복귀 주소 %s는 루트로 제한한다', value => {
      expect(getListReturnUrl(value)).toBe('/');
    }
  );

  it('같은 목록 주소에서 상세를 연 장소, 선택한 장소, 스크롤 위치를 한 번 복원한다', () => {
    vi.stubGlobal('scrollY', 480);
    rememberPlaceListPosition(35, 40, 1);
    expect(takePlaceListPosition('/?region=경기')).toBeNull();
    const url = `${window.location.pathname}${window.location.search}`;
    expect(takePlaceListPosition(url)).toEqual({ placeId: 35, selectedPlaceId: 1, visibleCount: 40, y: 480 });
    expect(takePlaceListPosition(url)).toBeNull();
  });

  it('선택 ID가 없는 기존 기록에서도 카드 수와 스크롤 위치를 복원한다', () => {
    const url = '/?region=%EA%B0%95%EC%9B%90&season=%EA%B2%A8%EC%9A%B8';
    sessionStorage.setItem('outpick:place-list-position', JSON.stringify({ url, placeId: 35, visibleCount: 40, y: 480 }));
    expect(takePlaceListPosition(url)).toEqual({ placeId: 35, selectedPlaceId: null, visibleCount: 40, y: 480 });
  });

  it('브라우저 저장소 사용이 거부돼도 탐색을 막지 않는다', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('blocked'); });
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('blocked'); });
    expect(() => rememberPlaceListPosition(35, 40)).not.toThrow();
    expect(takePlaceListPosition('/')).toBeNull();
  });

  it('손상된 스크롤 정보는 복원하지 않는다', () => {
    sessionStorage.setItem('outpick:place-list-position', 'broken-json');
    expect(takePlaceListPosition('/')).toBeNull();
    sessionStorage.setItem('outpick:place-list-position', JSON.stringify({ url: '/', y: -10 }));
    expect(takePlaceListPosition('/')).toBeNull();
  });
});
