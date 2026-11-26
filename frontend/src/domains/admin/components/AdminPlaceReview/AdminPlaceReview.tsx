import { useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ChevronLeft, ChevronRight, Search } from 'lucide-react';
import { adminDraftPlacesQuery } from '../../queries/adminPlacesQuery';
import { placeField, placePanel, placeSecondaryButton } from '../adminPlaceStyles';
import AdminPlaceDetail from './AdminPlaceDetail';

export default function AdminPlaceReview() {
  const [search, setSearch] = useState('');
  const [filters, setFilters] = useState({ query: '', page: 0 });
  const [placeId, setPlaceId] = useState<number | null>(null);
  const headingRef = useRef<HTMLHeadingElement>(null);
  const places = useQuery(adminDraftPlacesQuery(filters));
  const result = places.data;
  const hasNext = result !== undefined && (result.page + 1) * result.size < result.totalElements;

  function searchPlaces(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFilters({ query: search.trim(), page: 0 });
  }

  function changePage(page: number) {
    setFilters(previous => ({ ...previous, page }));
    headingRef.current?.focus();
  }

  function returnToList() {
    setPlaceId(null);
    headingRef.current?.focus();
  }

  return (
    <section className={placePanel} aria-labelledby="draft-places-heading">
      <h2 id="draft-places-heading" ref={headingRef} tabIndex={-1} className="text-base font-bold">
        검수 전 장소
      </h2>
      {placeId === null ? (
        <>
          <form onSubmit={searchPlaces} role="search" aria-label="검수 전 장소 검색"
            className="mt-3 flex flex-wrap items-end gap-2">
            <label className="flex min-w-0 flex-1 basis-44 flex-col gap-1 text-sm font-medium">
              장소명 또는 별칭
              <input type="search" maxLength={100} value={search} onChange={event => setSearch(event.target.value)}
                className={placeField} />
            </label>
            <button type="submit" className={placeSecondaryButton}><Search size={16} aria-hidden /> 검색</button>
          </form>

          {places.isPending && <p className="mt-4 text-sm text-slate-600" role="status">장소 목록을 불러오고 있어요.</p>}
          {places.isError && (
            <div className="mt-4 flex flex-wrap items-center gap-2">
              <p role="alert" className="text-sm text-red-700">장소 목록을 불러오지 못했어요.</p>
              <button type="button" className={placeSecondaryButton} disabled={places.isFetching} onClick={() => void places.refetch()}>
                다시 시도
              </button>
            </div>
          )}
          {places.isSuccess && result && (
            <>
              {result.items.length === 0 ? (
                <p className="mt-4 text-sm text-slate-600">
                  {filters.query ? '검색 조건에 맞는 검수 전 장소가 없어요.' : '검수 전 장소가 없어요.'}
                </p>
              ) : (
                <ul className="mt-4 grid gap-2 sm:grid-cols-2 lg:grid-cols-1 xl:grid-cols-2">
                  {result.items.map(place => (
                    <li key={place.id} className="min-w-0">
                      <button type="button" onClick={() => setPlaceId(place.id)}
                        className="flex h-full w-full flex-col gap-1 rounded-lg border border-slate-200 p-3 text-left text-sm wrap-anywhere hover:bg-slate-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600">
                        <span className="max-w-full font-semibold">{place.name ?? '이름 없음'}</span>
                        <span className="max-w-full text-xs text-slate-600">#{place.id} · {place.address ?? '주소 없음'}</span>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
              {(result.page > 0 || hasNext) && (
                <nav aria-label="검수 전 장소 페이지" className="mt-4 flex flex-wrap items-center justify-between gap-2">
                  <button type="button" className={placeSecondaryButton} disabled={result.page === 0} onClick={() => changePage(result.page - 1)}>
                    <ChevronLeft size={16} aria-hidden /> 이전
                  </button>
                  <span className="text-sm text-slate-600">{result.page + 1}페이지</span>
                  <button type="button" className={placeSecondaryButton} disabled={!hasNext} onClick={() => changePage(result.page + 1)}>
                    다음 <ChevronRight size={16} aria-hidden />
                  </button>
                </nav>
              )}
            </>
          )}
        </>
      ) : (
        <div className="mt-3">
          <button type="button" className={placeSecondaryButton} onClick={returnToList}>
            <ChevronLeft size={16} aria-hidden /> 목록으로
          </button>
          <AdminPlaceDetail placeId={placeId} />
        </div>
      )}
    </section>
  );
}
