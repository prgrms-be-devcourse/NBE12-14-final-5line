'use client';

import Link from 'next/link';
import FavoritePlaceButton from '../../places/detail/_components/FavoritePlaceButton';
import { Home, Bookmark, Map as MapIcon, List, Compass, Footprints, Umbrella, Fish, Waves, Flag, Mountain, Snowflake } from 'lucide-react';
import PlaceCardSkeleton from './PlaceCardSkeleton';
import MobilePlaceFilters from './MobilePlaceFilters';
import { useSearchParams } from 'next/navigation';
import { useEffect, useMemo, useRef, useState } from 'react';
import type { MouseEvent } from 'react';
import { changePlaceSearchFilters, defaultFilters, placeSearchFormId, readPlaceSearchParams, seasons } from './placeSearchParams';
import PlacePhoto from '@/domains/place/components/PlacePhoto/PlacePhoto';
import { compactPlaceAddress, visibleCardPrice } from '@/domains/place/lib/placeCardDisplay';
import CopyPlaceLinkButton from '@/domains/place/components/CopyPlaceLinkButton/CopyPlaceLinkButton';
import KakaoPlaceMap, { mapMessageClassName, mapRetryButtonClassName } from '@/domains/place/components/KakaoPlaceMap/KakaoPlaceMap';
import type { PlaceMapBounds, PlaceMapSelection, PlaceMapView } from '@/domains/place/types/kakaoMap.type';
import { readPlaceMapBounds, readPlaceMapParams, writePlaceMapParams } from '@/domains/place/lib/placeMapParams';
import { usePlaces } from '@/domains/place/hooks/usePlaces';
import { getPlaceDetailUrl, rememberPlaceListPosition, takePlaceListPosition } from '@/domains/place/lib/placeNavigation';
import './PlaceExplorer.css';

type MapSearch = { from: string; to: string };
type ListWindow = { key: string; count: number; restore?: NonNullable<ReturnType<typeof takePlaceListPosition>> };
const cardBatchSize = 10;
function CategoryIcon({ name }: { name: string }) {
  const Icon = name.includes('아이스') ? Footprints : name.includes('해수욕') ? Umbrella : name.includes('스노클') ? Fish : name.includes('워터') ? Waves : /루지|레일|레이싱/.test(name) ? Flag : name.includes('스키') ? Mountain : name.includes('썰매') ? Snowflake : Compass;
  return <Icon size={23} strokeWidth={1.6} aria-hidden="true" />;
}
const filterLabelClassName = 'min-w-0 flex-1 md:flex-none';
const filterSelectClassName = 'w-full cursor-pointer rounded-lg border border-[#e4e9f1] bg-white px-2 py-[9px] text-[11px] md:w-auto md:max-w-[120px] md:text-[12px]';
const noticeClassName = 'mt-2 mb-[22px] text-[11px]/[1.7] text-[#7f8b9c]';
const emptyClassName = 'rounded-xl border border-dashed border-[#dfe4ed] px-3 py-[70px] text-center';
const emptyHeadingClassName = 'mt-2.5 text-[17px] font-bold';
const emptyTextClassName = 'mt-2.5 text-[13px] text-[#7c899d]';
const emptyButtonClassName = 'mt-[22px] cursor-pointer rounded-lg bg-[#5147ff] px-[15px] py-2.5 text-[12px] text-white';

function countThroughCard(index: number) {
  return Math.max(cardBatchSize, Math.ceil((index + 1) / cardBatchSize) * cardBatchSize);
}

function mapSearchKey(params: URLSearchParams) {
  const key = new URLSearchParams(params);
  key.delete('sort');
  return key.toString();
}

export default function PlaceExplorer() {
  const searchParams = useSearchParams();
  const { query: search, category, region, season, environment, sort } = readPlaceSearchParams(searchParams);
  const [selection, setSelection] = useState<PlaceMapSelection | null>(null);
  const [mobileMap, setMobileMap] = useState(false);
  const [sheetSize, setSheetSize] = useState(0);
  const sheetPointerY = useRef<number | null>(null);
  const sheetDragged = useRef(false);
  const [hoveredId, setHoveredId] = useState<number | null>(null);
  const [mapSearch, setMapSearch] = useState<MapSearch | null>(null);
  const resultsPane = useRef<HTMLElement>(null);
  const placeList = useRef<HTMLUListElement>(null);
  const listEnd = useRef<HTMLDivElement>(null);
  const mapBounds = useMemo(() => readPlaceMapBounds(searchParams), [searchParams]);
  const paramsKey = searchParams.toString();
  const listUrl = `${typeof window === 'undefined' ? '/explore/' : window.location.pathname}${paramsKey ? `?${paramsKey}` : ''}`;
  const mapView = readPlaceMapParams(searchParams);
  const areaKey = mapSearchKey(searchParams);
  const activeMapSearch = mapSearch?.to === areaKey;
  const filters = { query: search, category, region, season, environment };
  const { catalog, places: placeQuery } = usePlaces(filters, mapBounds, activeMapSearch);
  const searchingArea = activeMapSearch && placeQuery.fetchStatus !== 'idle';
  const listIsPrevious = placeQuery.isPlaceholderData;
  const loading = catalog.isPending || placeQuery.isPending;
  const failed = catalog.isError || placeQuery.isError;
  const listKey = JSON.stringify([filters, mapBounds, sort]);
  const [listWindow, setListWindow] = useState<ListWindow>(() => ({ key: listKey, count: cardBatchSize }));
  const categories = ['전체', ...(catalog.data?.categories.map(item => item.name) ?? [])];
  const locatedPlaces = useMemo(() => (placeQuery.data?.items ?? []).flatMap(place => {
    const { latitude, longitude } = place;
    if (latitude === null || longitude === null) return [];
    return [{ ...place, name: place.name ?? '이름 미확인', latitude, longitude }];
  }), [placeQuery.data]);
  const allPlaces = useMemo(() => (placeQuery.data?.items ?? []).map(place => ({...place, name: place.name ?? '이름 미확인'})), [placeQuery.data]);
  const places = sort === 'name-desc' ? allPlaces.toReversed() : allPlaces;
  const selectedIndex = places.findIndex(place => place.id === selection?.id);
  const selectedPlace = places[selectedIndex];
  const selectedCardCount = countThroughCard(selectedIndex);
  const mapSearchFinished = activeMapSearch && !searchingArea && placeQuery.isSuccess && !listIsPrevious;
  if (mapSearchFinished) {
    if (selection && !selectedPlace) setSelection(null);
    setMapSearch(null);
  } else if (mapSearch && !activeMapSearch && mapSearch.from !== areaKey) {
    setMapSearch(null);
  }

  if (!listIsPrevious && listWindow.key !== listKey) {
    setListWindow({ key: listKey, count: activeMapSearch ? selectedCardCount : cardBatchSize });
  } else if (mapSearchFinished && selectedCardCount > listWindow.count) {
    setListWindow({ ...listWindow, count: selectedCardCount });
  }
  const visibleCount = Math.min(places.length, listWindow.count);
  const hasMore = visibleCount < places.length;

  function togglePlaceSelection(nextSelection: PlaceMapSelection) {
    if (nextSelection.id === selection?.id) {
      setSelection(null);
      return;
    }

    if (nextSelection.source === 'map') {
      const index = places.findIndex(place => place.id === nextSelection.id);
      if (index < 0) return;
      const count = countThroughCard(index);
      setListWindow(current => count > current.count ? { ...current, count } : current);
      setMobileMap(false);
    }

    setSelection(nextSelection);
  }

  useEffect(() => {
    if (selection?.source !== 'map') return;
    const card = placeList.current?.querySelector(`[data-place-id="${selection.id}"]`);
    card?.scrollIntoView({ block: 'nearest', inline: 'nearest' });
  }, [selection]);

  function changeMapBounds(bounds: PlaceMapBounds, view: PlaceMapView) {
    const currentParams = new URLSearchParams(window.location.search);
    const params = writePlaceMapParams(currentParams, view, bounds);
    if (params.toString() !== currentParams.toString()) params.delete('page');
    const nextUrl = `${window.location.pathname}?${params}${window.location.hash}`;
    const currentUrl = `${window.location.pathname}${window.location.search}${window.location.hash}`;
    // 지도 이동은 현재 기록만 갱신해서 뒤로 가기에 지도 이동 기록이 쌓이지 않게 합니다.
    if (nextUrl === currentUrl) return;
    setMapSearch({ from: mapSearchKey(currentParams), to: mapSearchKey(params) });
    window.history.replaceState(null, '', nextUrl);
  }

  useEffect(() => {
    const sentinel = listEnd.current;
    if (!sentinel || listIsPrevious || typeof IntersectionObserver === 'undefined') return;
    const observer = new IntersectionObserver(entries => {
      if (entries.some(entry => entry.isIntersecting)) {
        setListWindow(current => current.key === listKey && current.count === visibleCount
          ? { ...current, count: visibleCount + cardBatchSize }
          : current);
      }
    });
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [hasMore, visibleCount, listIsPrevious, listKey, loading, failed]);

  useEffect(() => {
    if (loading || failed || listIsPrevious) return;
    const frame = window.requestAnimationFrame(() => {
      const restore = takePlaceListPosition(listUrl);
      if (!restore) return;
      const selectedIndex = places.findIndex(place => place.id === restore.selectedPlaceId);
      const restoredSelection = places[selectedIndex];
      if (restoredSelection) setSelection({ id: restoredSelection.id, source: 'restore' });

      const selectedCardCount = countThroughCard(selectedIndex);
      const detailIndex = places.findIndex(place => place.id === restore.placeId);
      if (!placeQuery.isFetchedAfterMount && detailIndex >= 0) {
        setListWindow({ key: listKey, count: Math.max(restore.visibleCount, detailIndex + 1, selectedCardCount), restore });
      } else if (restoredSelection) {
        setListWindow(current => ({ ...current, count: Math.max(current.count, selectedCardCount) }));
      }
    });
    return () => window.cancelAnimationFrame(frame);
  }, [loading, failed, listIsPrevious, listUrl, listKey, places, placeQuery.isFetchedAfterMount]);

  useEffect(() => {
    const restore = listWindow.restore;
    if (!restore) return;
    const frame = window.requestAnimationFrame(() => {
      const card = placeList.current?.querySelector(`[data-place-id="${restore.placeId}"]`);
      const pane = resultsPane.current;
      const options: ScrollToOptions = { top: restore.y, behavior: 'instant' };
      if (pane && getComputedStyle(pane).overflowY === 'auto') pane.scrollTo(options);
      else window.scrollTo(options);
      card?.scrollIntoView({ block: 'nearest', inline: 'nearest' });
    });
    return () => window.cancelAnimationFrame(frame);
  }, [listWindow.restore]);

  function openPlaceDetail(event: MouseEvent<HTMLAnchorElement>, placeId: number) {
    if (event.button === 0 && !event.metaKey && !event.ctrlKey && !event.shiftKey && !event.altKey) {
      if (selection?.id !== placeId) setSelection({ id: placeId, source: 'list' });
      const pane = resultsPane.current;
      const scrollTop = pane && getComputedStyle(pane).overflowY === 'auto' ? pane.scrollTop : window.scrollY;
      rememberPlaceListPosition(placeId, visibleCount, placeId, scrollTop);
    }
  }

  function retrySearch() {
    if (catalog.isError) void catalog.refetch();
    else void placeQuery.refetch();
  }

  function resetFilters() {
    changePlaceSearchFilters(defaultFilters);
    setSelection(null);
  }

  return (
    <div style={{'--sheet-height': ['110px', '46dvh', '72dvh'][sheetSize]} as React.CSSProperties} className={`outpick-explorer ${mobileMap ? 'outpick-mobileMapMode' : ''} bg-white text-[#16243b] md:flex md:min-h-0 md:flex-col
        [&_:is(a,button,input,select):focus-visible]:outline-3 [&_:is(a,button,input,select):focus-visible]:outline-offset-4
        [&_:is(a,button,input,select):focus-visible]:outline-[#5147ff]`}>
      {searchParams.has('withdrawn') && <p className="mx-9 mb-4 rounded-xl bg-[#e8f7ef] px-4 py-3 text-[14px] font-bold text-[#176547] md:shrink-0" role="status">회원 탈퇴가 완료되었습니다.</p>}

      <div className="border-b border-[#e9edf3] px-5 md:flex md:shrink-0 md:flex-wrap md:justify-between md:px-6 min-[68.8125rem]:gap-5 min-[68.8125rem]:px-9">
        <nav aria-label="활동 카테고리" className="flex w-full gap-6 overflow-x-auto md:gap-7">
          {categories.map(item => (
            <button
              key={item}
              type="button"
              className="flex shrink-0 cursor-pointer flex-col items-center gap-2 border-b-2 border-transparent py-4 text-[12px] font-semibold text-[#617088]
                aria-pressed:border-[#5147ff] aria-pressed:text-[#5147ff]"
              aria-pressed={category === item}
              onClick={() => changePlaceSearchFilters({ category: item })}
            >
              <CategoryIcon name={item} />{item}
            </button>
          ))}
        </nav>
        <div className="hidden items-center gap-1.5 py-3 md:flex md:gap-[7px] md:pt-0 min-[68.8125rem]:pb-3">
          <label className={filterLabelClassName}><span className="sr-only">지역</span><select className={filterSelectClassName} value={region} onChange={event => changePlaceSearchFilters({ region: event.target.value })}>
            <option value="">지역 전체</option>
            {region && !catalog.data?.regions.some(item => item.name === region) && <option value={region}>{region} (미제공)</option>}
            {catalog.data?.regions.map(item => <option key={item.id} value={item.name}>{item.name}</option>)}
          </select></label>
          <label className={filterLabelClassName}><span className="sr-only">시즌</span><select className={filterSelectClassName} value={season} onChange={event => changePlaceSearchFilters({ season: event.target.value })}>
            <option value="">시즌 전체</option>{seasons.map(item => <option key={item}>{item}</option>)}
          </select></label>
          <label className={filterLabelClassName}><span className="sr-only">실내외</span><select className={filterSelectClassName} value={environment} onChange={event => changePlaceSearchFilters({ environment: event.target.value })}>
            <option value="">실내외 전체</option><option>실내</option><option>실외</option>
          </select></label>
        </div>
        <MobilePlaceFilters filters={{region,season,environment}} regions={catalog.data?.regions ?? []} seasons={seasons} onApply={changePlaceSearchFilters}/>
      </div>

      <main className="relative md:grid md:min-h-0 md:flex-1 md:grid-cols-[55fr_45fr] md:grid-rows-[minmax(0,1fr)]">
        <section
          ref={resultsPane}
          aria-labelledby="results-title"
          className={`min-w-0 px-5 pt-[27px] pb-[85px] md:overflow-y-auto md:overscroll-y-contain md:px-6 md:py-7
            min-[68.8125rem]:p-9 min-[100rem]:px-14 min-[100rem]:py-11 ${mobileMap ? 'outpick-mobileSheet' : ''}`}
        >
          {mobileMap && <button type="button" aria-label="장소 목록 시트 높이 조절" aria-expanded={sheetSize > 0} onPointerDown={event => { sheetPointerY.current=event.clientY; sheetDragged.current=false; event.currentTarget.setPointerCapture(event.pointerId); }} onPointerUp={event => { const delta=(sheetPointerY.current ?? event.clientY)-event.clientY; sheetDragged.current=Math.abs(delta)>25; if(sheetDragged.current)setSheetSize(current=>Math.max(0,Math.min(2,current+(delta>0?1:-1)))); sheetPointerY.current=null; }} onClick={() => { if(!sheetDragged.current)setSheetSize(current=>(current+1)%3); }} onKeyDown={event => { if(event.key==='ArrowUp'||event.key==='ArrowDown'){event.preventDefault();setSheetSize(current=>Math.max(0,Math.min(2,current+(event.key==='ArrowUp'?1:-1))));} }} className="mx-auto mb-3 flex h-7 w-full touch-none flex-col items-center justify-center md:hidden"><span className="h-1 w-10 rounded-full bg-slate-300"/><span className="sr-only">접기·절반·전체 높이로 전환</span></button>}
          <div><h1 id="results-title" className="text-[22px] font-bold tracking-tight text-[#17172B]">{category === '전체' ? '새로운 즐거움을 발견하세요' : category}</h1></div>
          <div className="mt-4 flex items-center gap-[9px] text-[12px] md:gap-4">
            <p role="status">{loading ? '장소를 불러오고 있어요' : failed ? '조회 실패' : <>검색 결과 <strong className="text-[#5147ff]">{placeQuery.data?.totalElements ?? 0}곳</strong></>}</p>
            <label className="ml-auto"><span className="sr-only">정렬</span><select className="max-w-[115px] cursor-pointer bg-white p-2" value={sort} onChange={event => changePlaceSearchFilters({ sort: event.target.value })}>
              <option value="name">이름순</option><option value="name-desc">이름 역순</option>
            </select></label>
            <button type="reset" form={placeSearchFormId} className="cursor-pointer text-[#758299] underline underline-offset-3" onClick={resetFilters}>필터 초기화</button>
          </div>
          <div className="mt-2">
            <CopyPlaceLinkButton label="검색 링크 복사" />
          </div>
          {places.some(place => place.name?.startsWith('[데모]')) && <p className={noticeClassName}>[데모] 표시는 가상 장소이며 실제 시설 정보가 아닙니다.</p>}
          <p className={noticeClassName} role="status">{searchingArea ? '이 영역의 장소를 찾는 중' : ''}</p>
          {failed ? <div className={emptyClassName} role="alert"><h2 className={emptyHeadingClassName}>장소를 불러오지 못했어요</h2><p className={emptyTextClassName}>서버 연결을 확인하고 다시 시도해 주세요.</p><button className={emptyButtonClassName} type="button" onClick={retrySearch}>다시 시도</button></div> : loading ? <PlaceCardSkeleton /> : places.length > 0 ? (
            <ul ref={placeList} className="outpick-placeGrid grid list-none gap-x-5 gap-y-7 p-0" aria-label="장소 목록">
              {places.slice(0, visibleCount).map(place => (
                <li key={place.id} className="min-w-0">
                  <article data-place-id={place.id} onMouseEnter={() => setHoveredId(place.id)} onMouseLeave={() => setHoveredId(null)} onFocus={() => setHoveredId(place.id)} onBlur={() => setHoveredId(null)} className={`outpick-placeCard group relative h-full ${selectedPlace?.id === place.id ? 'outpick-placeCardSelected' : ''}`}>
                    <div className="absolute top-3 right-3 z-10"><FavoritePlaceButton placeId={place.id} placeName={place.name} iconOnly /></div>
                    <Link className="block h-full text-inherit no-underline" aria-label={`${place.name} 상세 보기`} href={getPlaceDetailUrl(place.id, listUrl)} onClick={event => openPlaceDetail(event, place.id)}>
                      <div className="outpick-cardPhoto aspect-[4/3] overflow-hidden rounded-[14px] bg-[#F5F6F8] [&>div]:h-full [&>div]:aspect-auto"><PlacePhoto place={place} category={category} /></div>
                      <div className="pt-3">
                        <p className="truncate text-[13px] text-[#717586]">{compactPlaceAddress(place.address)}</p>
                        <h2 className="mt-1 line-clamp-2 break-words text-[17px]/6 font-semibold tracking-[-.4px] text-[#17172B]">{place.name}</h2>
                        {category !== '전체' && <p className="mt-1 text-xs text-[#717586]">{category}</p>}
                        {visibleCardPrice(place.pricePreview, category) && <p className="mt-2 text-[13px] font-medium text-[#17172B]">{visibleCardPrice(place.pricePreview, category)}</p>}
                      </div>
                    </Link>
                  </article>
                </li>
              ))}
            </ul>
          ) : (
            <div className={emptyClassName}>
              <span className="text-[40px] text-[#a5a0d9]" aria-hidden="true">⌕</span>
              <h2 className={emptyHeadingClassName}>조건에 맞는 장소가 없어요</h2>
              <p className={emptyTextClassName}>지도를 이동·축소하거나 검색 조건을 바꿔 보세요.</p>
              <button className={emptyButtonClassName} type="reset" form={placeSearchFormId} onClick={resetFilters}>검색 필터 초기화</button>
            </div>
          )}
          {!loading && !failed && hasMore && <div ref={listEnd} aria-hidden="true" className="h-px" />}
          <footer className="mt-[38px] text-[10px] text-[#8995a6]">OUTPICK · 이번 주말, 밖에서 뭐 하지?</footer>
        </section>

        <aside aria-label="장소 지도" className={`relative max-md:h-[75dvh] max-md:min-h-[560px] md:border-l md:border-l-[#e5e9ef] ${!mobileMap ? 'max-md:hidden' : ''}`}>
          <KakaoPlaceMap places={locatedPlaces} hoveredId={hoveredId} totalPlaces={placeQuery.data?.totalElements} searchingArea={searchingArea} selection={selectedPlace ? selection : null} onSelect={togglePlaceSelection} onBoundsChange={changeMapBounds} view={mapView} hasSearchBounds={mapBounds !== null} />
          {failed && mobileMap && (
            <div className={mapMessageClassName} role="alert">
              <p>지도에 표시할 장소를 불러오지 못했어요.</p>
              <button className={mapRetryButtonClassName} type="button" onClick={retrySearch}>다시 시도</button>
            </div>
          )}
          {selectedPlace && (
            <div className="absolute inset-x-4 bottom-24 z-[2] rounded-xl border border-[#e7eae7] bg-[#fffffff5] px-5 py-[18px] shadow-[0_5px_20px_#34553a15] md:bottom-10" aria-live="polite">
              <div className="mx-auto mb-3 h-1 w-9 rounded-full bg-slate-200 md:hidden"/><span className="text-[10px] text-[#6156ff]">선택한 장소</span><h3 className="mt-[3px] text-[16px] font-bold">{selectedPlace.name}</h3>
              <p className="mt-[5px] text-[11px]/[1.7] text-[#7d899a]">{selectedPlace.address ?? '주소 미확인'}</p>
              <Link className="mt-3 block border-t border-[#edf0f4] pt-2.5 text-[11px] text-[#5147ff] no-underline" href={getPlaceDetailUrl(selectedPlace.id, listUrl)} onClick={event => openPlaceDetail(event, selectedPlace.id)}>장소 상세 보기 →</Link>
            </div>
          )}
        </aside>
      </main>
      <nav aria-label="모바일 탐색 메뉴" className="fixed inset-x-0 bottom-0 z-20 grid grid-cols-3 border-t border-[#ececf2] bg-white/95 px-4 pb-[env(safe-area-inset-bottom)] text-[11px] backdrop-blur md:hidden [&>a]:flex [&>a]:min-h-16 [&>a]:flex-col [&>a]:items-center [&>a]:justify-center [&>a]:gap-1">
        <Link href="/" className="text-[#717586]"><Home size={20} aria-hidden="true"/>홈</Link>
        <button type="button" aria-pressed={mobileMap} onClick={() => setMobileMap(!mobileMap)} className="flex min-h-16 flex-col items-center justify-center gap-1 font-semibold text-[#5942FF]">{mobileMap ? <List size={20} aria-hidden="true"/> : <MapIcon size={20} aria-hidden="true"/>}{mobileMap ? '목록 보기' : '지도 보기'}</button>
        <Link href="/mypage/favorites/" className="text-[#717586]"><Bookmark size={20} aria-hidden="true"/>관심 장소</Link>
      </nav>
    </div>
  );
}
