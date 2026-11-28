const listPositionKey = 'outpick:place-list-position';
type PlaceListPosition = { placeId: number; selectedPlaceId: number | null; visibleCount: number; y: number };

/** 복귀 주소는 사이트의 장소 목록(/)만 허용합니다. */
export function getListReturnUrl(value: string | null) {
  if (!value || !value.startsWith('/') || value.startsWith('//')) return '/';
  try {
    const url = new URL(value, 'https://outpick.invalid');
    const query = url.searchParams.toString();
    return url.origin === 'https://outpick.invalid' && ['/', '/explore', '/explore/'].includes(url.pathname)
      ? `${url.pathname}${query ? `?${query}` : ''}${url.hash}`
      : '/';
  } catch {
    return '/';
  }
}

export function getPlaceDetailUrl(placeId: number, returnTo: string) {
  const params = new URLSearchParams({ id: String(placeId), returnTo: getListReturnUrl(returnTo) });
  return `/places/detail/?${params}`;
}

export function getPlaceNoticeUrl(placeId: number, placeNoticeId: number) {
  return `/places/detail/?id=${placeId}#place-notice-${placeNoticeId}`;
}

export function scrollToPlaceNotice() {
  const match = /^#place-notice-([1-9]\d*)$/.exec(window.location.hash);
  if (!match) return;

  const heading = document.getElementById('place-notices');
  // Keep the URL fragment separate from DOM IDs so native anchor scrolling cannot override this position.
  const card = heading?.parentElement?.querySelector(`[data-place-notice-id="${match[1]}"]`);
  const target = card && card !== heading?.nextElementSibling ? card.querySelector('h3') : heading;
  target?.scrollIntoView({ block: 'center' });
}

/** 일반적인 같은 탭 이동 직전에만 호출하며, 브라우저 저장소가 막혀도 탐색은 유지합니다. */
export function rememberPlaceListPosition(placeId: number, visibleCount: number, selectedPlaceId: number | null = null, y = window.scrollY) {
  try {
    sessionStorage.setItem(listPositionKey, JSON.stringify({
      url: getListReturnUrl(`${window.location.pathname}${window.location.search}`),
      placeId,
      selectedPlaceId,
      visibleCount,
      y,
    }));
  } catch {
    // 스크롤 복원 실패가 상세 이동을 막지 않도록 합니다.
  }
}

/** 일치하는 검색 목록으로 돌아올 때 한 번만 사용합니다. */
export function takePlaceListPosition(url: string): PlaceListPosition | null {
  try {
    const raw = sessionStorage.getItem(listPositionKey);
    if (!raw) return null;
    const saved: unknown = JSON.parse(raw);
    if (typeof saved !== 'object' || saved === null
      || !('url' in saved) || saved.url !== getListReturnUrl(url)) return null;
    sessionStorage.removeItem(listPositionKey);
    if (!('y' in saved) || typeof saved.y !== 'number' || !Number.isFinite(saved.y) || saved.y < 0
      || !('placeId' in saved) || typeof saved.placeId !== 'number' || !Number.isSafeInteger(saved.placeId) || saved.placeId < 1
      || !('visibleCount' in saved) || typeof saved.visibleCount !== 'number'
      || !Number.isSafeInteger(saved.visibleCount) || saved.visibleCount < 1) return null;
    const selectedId = 'selectedPlaceId' in saved ? saved.selectedPlaceId : null;
    const selectedPlaceId = typeof selectedId === 'number' && Number.isSafeInteger(selectedId) && selectedId > 0
      ? selectedId : null;
    return { placeId: saved.placeId, selectedPlaceId, visibleCount: saved.visibleCount, y: saved.y };
  } catch {
    return null;
  }
}
