import type { PlaceMapBounds, PlaceMapView } from '../types/kakaoMap.type';

/** 좌표는 소수점 여섯 자리까지 저장해 SDK의 미세한 좌표 오차로 URL이 반복 변경되지 않게 합니다. */
export function normalizeMapView(view: PlaceMapView): PlaceMapView {
  return { lat: Number(view.lat.toFixed(6)), lng: Number(view.lng.toFixed(6)), level: view.level };
}

export function readPlaceMapParams(params: Pick<URLSearchParams, 'get'>): PlaceMapView | null {
  const values = ['lat', 'lng', 'level'].map(key => params.get(key));
  if (values.some(value => value === null || value.trim() === '')) return null;
  const [lat, lng, level] = values.map(Number);
  if (!Number.isFinite(lat) || lat < -90 || lat > 90
    || !Number.isFinite(lng) || lng < -180 || lng > 180
    || !Number.isInteger(level) || level < 1 || level > 14) return null;
  return normalizeMapView({ lat, lng, level });
}

/** 모바일 목록에서는 지도가 숨겨져 있으므로 마지막 검색 영역도 함께 복원합니다. */
export function readPlaceMapBounds(params: Pick<URLSearchParams, 'get'>): PlaceMapBounds | null {
  if (!readPlaceMapParams(params)) return null;
  const values = ['south', 'west', 'north', 'east'].map(key => params.get(key));
  if (values.some(value => value === null || value.trim() === '')) return null;
  const [south, west, north, east] = values.map(Number);
  if (![south, west, north, east].every(Number.isFinite)
    || south < -90 || north > 90 || south >= north
    || west < -180 || east > 180 || west >= east) return null;
  return { south, west, north, east };
}

export function writePlaceMapParams(current: URLSearchParams, view: PlaceMapView, bounds?: PlaceMapBounds) {
  const params = new URLSearchParams(current);
  const normalized = normalizeMapView(view);
  params.set('lat', String(normalized.lat));
  params.set('lng', String(normalized.lng));
  params.set('level', String(normalized.level));
  if (bounds) {
    // 경계에 걸친 장소가 좌표 반올림 때문에 제외되지 않도록 바깥 방향으로 저장합니다.
    for (const key of ['south', 'west', 'north', 'east'] as const) {
      const value = bounds[key];
      const scaled = value * 1e6;
      // 이미 저장 정밀도에 맞는 좌표는 곱셈 오차로 경계가 더 넓어지지 않게 합니다.
      const nearest = Math.round(scaled) / 1e6;
      const round = key === 'south' || key === 'west' ? Math.floor : Math.ceil;
      params.set(key, String(value === nearest ? value : round(scaled) / 1e6));
    }
  }
  return params;
}

export function sameMapView(a: PlaceMapView, b: PlaceMapView) {
  const left = normalizeMapView(a);
  const right = normalizeMapView(b);
  return left.lat === right.lat && left.lng === right.lng && left.level === right.level;
}
