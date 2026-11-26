import type { PlaceSearchFilters } from './place-list.type';

export const categories = ['전체', '수상', '산악', '항공', '스키·보드', '체험', '실내'];
export const seasons = ['봄', '여름', '가을', '겨울'];

export const defaultFilters: PlaceSearchFilters = {
  query: '',
  category: '전체',
  region: '',
  season: '',
  environment: '',
  sort: 'name',
};

function allowedValue(value: string | null, allowed: string[], fallback = '') {
  return value !== null && allowed.includes(value) ? value : fallback;
}

function optionName(value: string | null, fallback = '') {
  const normalized = value?.trim();
  return normalized && normalized.length <= 50 ? normalized : fallback;
}

/** 화면에서 지원하지 않는 URL 값은 기본 조건으로 해석합니다. */
export function readPlaceSearchParams(params: Pick<URLSearchParams, 'get'>): PlaceSearchFilters {
  return {
    query: (params.get('query') ?? '').trim(),
    category: optionName(params.get('category'), '전체'),
    region: optionName(params.get('region')),
    season: allowedValue(params.get('season'), seasons),
    environment: allowedValue(params.get('environment'), ['실내', '실외']),
    sort: allowedValue(params.get('sort'), ['name', 'name-desc'], 'name'),
  };
}

/** 기본값은 생략하고 이 화면이 소유하지 않는 쿼리 파라미터는 보존합니다. */
export function writePlaceSearchParams(current: URLSearchParams, changes: Partial<PlaceSearchFilters>) {
  const params = new URLSearchParams(current);
  const filters = { ...readPlaceSearchParams(current), ...changes };
  const normalized = readPlaceSearchParams(new URLSearchParams(filters));

  for (const key of Object.keys(defaultFilters) as (keyof PlaceSearchFilters)[]) {
    if (normalized[key] === defaultFilters[key]) {
      params.delete(key);
    } else {
      params.set(key, normalized[key]);
    }
  }
  return params;
}

export const placeSearchFormId = 'place-search-form';

/** 검색 조건을 URL에 반영하고 이전 검색 주소를 브라우저 기록에 남깁니다. */
export function changePlaceSearchFilters(changes: Partial<PlaceSearchFilters>) {
  const { pathname, search, hash } = window.location;
  const params = writePlaceSearchParams(new URLSearchParams(search), changes);
  params.delete('page');
  const query = params.toString();
  const nextUrl = `${pathname}${query ? `?${query}` : ''}${hash}`;
  if (nextUrl !== `${pathname}${search}${hash}`) window.history.pushState(null, '', nextUrl);
}
