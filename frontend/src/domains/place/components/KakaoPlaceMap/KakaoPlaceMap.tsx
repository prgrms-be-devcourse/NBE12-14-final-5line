'use client';

import { useEffect, useMemo, useRef, useState } from 'react';
import { loadKakaoMaps } from '../../lib/loadKakaoMaps';
import { sameMapView } from '../../lib/placeMapParams';
import type { KakaoMap, KakaoMapsApi, KakaoOverlay, PlaceMapBounds, PlaceMapView, PlaceMapItem, PlaceMapSelection } from '../../types/kakaoMap.type';
import './KakaoPlaceMap.css';
import { clusterPlaces, type PlaceCluster } from '../../lib/clusterPlaces';

export const mapMessageClassName = 'absolute inset-x-[18px] top-[28%] z-[3] rounded-xl border border-[#e4e9f1] bg-[#fffffff5] p-5 text-center text-[13px]/[1.8] text-[#63718a]';
export const mapRetryButtonClassName = 'mt-[15px] cursor-pointer rounded-lg bg-[#5147ff] px-3.5 py-[9px] text-xs/[1.8] text-white';

type MapContext = { api: KakaoMapsApi; map: KakaoMap; moveWithoutSearch: (move: () => void) => void; fitInitial?: () => void };
type Pin = { place: PlaceCluster; overlay: KakaoOverlay; button: HTMLButtonElement; dispose: () => void };

function fitPlaces({ api, map }: Pick<MapContext, 'api' | 'map'>, places: PlaceMapItem[]) {
  if (places.length === 0) return;
  if (places.length === 1) {
    map.setCenter(new api.LatLng(places[0].latitude, places[0].longitude));
    map.setLevel(7);
    return;
  }
  const bounds = new api.LatLngBounds();
  places.forEach(place => bounds.extend(new api.LatLng(place.latitude, place.longitude)));
  map.setBounds(bounds, 70, 30, 180, 30);
}

export default function KakaoPlaceMap({ places, totalPlaces, searchingArea = false, selection, onSelect, onBoundsChange, view = null, hasSearchBounds = false, hoveredId = null }: {
  hoveredId?: number | null;
  places: PlaceMapItem[];
  totalPlaces?: number;
  searchingArea?: boolean;
  selection: PlaceMapSelection | null;
  onSelect: (selection: PlaceMapSelection) => void;
  onBoundsChange?: (bounds: PlaceMapBounds, view: PlaceMapView) => void;
  view?: PlaceMapView | null;
  hasSearchBounds?: boolean;
}) {
  const container = useRef<HTMLDivElement>(null);
  const pins = useRef(new Map<number, Pin>());
  const previousPlaceCount = useRef(places.length);
  const previousView = useRef<PlaceMapView | null>(view);
  const latest = useRef({ places, onSelect, onBoundsChange, view, hasSearchBounds });
  const [context, setContext] = useState<MapContext | null>(null);
  const [error, setError] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [zoomLevel, setZoomLevel] = useState(view?.level ?? 11);
  const groupedPlaces = useMemo(() => clusterPlaces(places, zoomLevel, hoveredId, selection?.id), [places, zoomLevel, hoveredId, selection?.id]);

  useEffect(() => {
    latest.current = { places, onSelect, onBoundsChange, view, hasSearchBounds };
  }, [places, onSelect, onBoundsChange, view, hasSearchBounds]);

  useEffect(() => {
    const element = container.current;
    if (!element) return;
    let active = true;
    let observer: ResizeObserver | undefined;
    let disposeEvents: (() => void) | undefined;
    loadKakaoMaps().then(api => {
      if (!active) return;
      const initialView = latest.current.view;
      const map = new api.Map(element, {
        center: new api.LatLng(initialView?.lat ?? 37.6, initialView?.lng ?? 128),
        level: initialView?.level ?? 11,
      });
      map.addControl(new api.ZoomControl(), api.ControlPosition.RIGHT);
      let timer: ReturnType<typeof setTimeout> | undefined;
      let fitted = false;
      let searchPending = false;
      const cancelSearch = () => clearTimeout(timer);
      const moveWithoutSearch = (move: () => void) => {
        move();
        // SDK 이벤트는 코드에서 중심이나 확대 수준을 바꿀 때도 발생합니다.
        searchPending = false;
        cancelSearch();
      };
      const next: MapContext = { api, map, moveWithoutSearch };
      const startSearch = () => {
        searchPending = true;
        cancelSearch();
      };
      const search = () => {
        setZoomLevel(map.getLevel());
        if (!searchPending) return;
        cancelSearch();
        timer = setTimeout(() => {
          if (!active || !element.clientWidth || !element.clientHeight) return;
          searchPending = false;
          const bounds = map.getBounds();
          const sw = bounds.getSouthWest();
          const ne = bounds.getNorthEast();
          const center = map.getCenter();
          latest.current.onBoundsChange?.(
            { south: sw.getLat(), west: sw.getLng(), north: ne.getLat(), east: ne.getLng() },
            { lat: center.getLat(), lng: center.getLng(), level: map.getLevel() }
          );
        }, 300);
      };
      api.event.addListener(map, 'dragstart', startSearch);
      api.event.addListener(map, 'zoom_start', startSearch);
      api.event.addListener(map, 'bounds_changed', cancelSearch);
      api.event.addListener(map, 'idle', search);
      disposeEvents = () => {
        cancelSearch();
        api.event.removeListener(map, 'dragstart', startSearch);
        api.event.removeListener(map, 'zoom_start', startSearch);
        api.event.removeListener(map, 'bounds_changed', cancelSearch);
        api.event.removeListener(map, 'idle', search);
      };
      const resize = () => {
        if (element.clientWidth === 0 || element.clientHeight === 0) return;
        // Wait for the first API result before fitting/searching; otherwise Seoul bounds can hide coastal results.
        if (!fitted && !latest.current.places.length && !latest.current.view && !latest.current.hasSearchBounds) { map.relayout(); return; }
        const shouldSearch = searchPending || (!fitted && !latest.current.hasSearchBounds);
        const center = map.getCenter();
        moveWithoutSearch(() => {
          map.relayout();
          if (fitted) {
            map.setCenter(center);
            return;
          }
          const savedView = latest.current.view;
          if (savedView) {
            map.setLevel(savedView.level);
            map.setCenter(new api.LatLng(savedView.lat, savedView.lng));
          } else fitPlaces(next, latest.current.places);
        });
        // 첫 진입에서 잡은 초기 범위나 대기 중인 사용자 조작의 검색만 이어갑니다.
        if (shouldSearch) {
          startSearch();
          search();
        }
        fitted = true;
      };
      next.fitInitial = () => { if(!fitted)resize(); };
      observer = new ResizeObserver(resize);
      observer.observe(element);
      setZoomLevel(map.getLevel());
      setContext(next);
    }).catch(() => {
      if (active) setError(true);
    });
    return () => {
      active = false;
      observer?.disconnect();
      disposeEvents?.();
      element.replaceChildren();
    };
  }, [attempt]);

  useEffect(() => {
    const becameReady = previousPlaceCount.current === 0 && places.length > 0;
    previousPlaceCount.current = places.length;
    if (becameReady) context?.fitInitial?.();
  }, [context, places]);

  const lat = view?.lat;
  const lng = view?.lng;
  const level = view?.level;
  useEffect(() => {
    if (!context) return;
    const target = lat !== undefined && lng !== undefined && level !== undefined
      ? { lat, lng, level }
      : previousView.current ? { lat: 37.6, lng: 128, level: 11 } : null;
    previousView.current = lat !== undefined && lng !== undefined && level !== undefined ? { lat, lng, level } : null;
    if (!target) return;
    const center = context.map.getCenter();
    const current = { lat: center.getLat(), lng: center.getLng(), level: context.map.getLevel() };
    // URL 복원만 지도에 적용하고 지도에서 기록한 같은 좌표는 다시 적용하지 않습니다.
    if (sameMapView(current, target)) return;
    context.moveWithoutSearch(() => {
      context.map.setLevel(target.level);
      context.map.setCenter(new context.api.LatLng(target.lat, target.lng));
    });
  }, [context, lat, lng, level]);

  useEffect(() => {
    if (!context) return;
    const currentPins = pins.current;
    return () => {
      currentPins.forEach(pin => pin.dispose());
      currentPins.clear();
    };
  }, [context]);

  useEffect(() => {
    if (!context) return;
    const { api, map } = context;
    const currentPins = pins.current;
    const nextIds = new Set(groupedPlaces.map(place => place.id));
    for (const [id, pin] of currentPins) {
      if (!nextIds.has(id)) {
        pin.dispose();
        currentPins.delete(id);
      }
    }

    for (const place of groupedPlaces) {
      const pin = currentPins.get(place.id);
      if (pin) {
        const clustered = place.memberIds.length > 1;
        pin.button.textContent = clustered ? String(place.memberIds.length) : place.name;
        pin.button.className = clustered ? 'outpick-mapCluster' : 'outpick-mapPin';
        pin.button.setAttribute('aria-label', clustered ? `${place.memberIds.length}개 장소 확대` : `${place.name} 지도에서 선택`);
        if (place.latitude !== pin.place.latitude || place.longitude !== pin.place.longitude) {
          pin.overlay.setPosition(new api.LatLng(place.latitude, place.longitude));
        }
        pin.place = place;
        continue;
      }
      const button = document.createElement('button');
      button.type = 'button';
      const clustered = place.memberIds.length > 1;
      button.className = clustered ? 'outpick-mapCluster' : 'outpick-mapPin';
      button.textContent = clustered ? String(place.memberIds.length) : place.name;
      button.setAttribute('aria-label', clustered ? `${place.memberIds.length}개 장소 확대` : `${place.name} 지도에서 선택`);
      button.setAttribute('aria-pressed', 'false');
      const select = () => {
        const current = currentPins.get(place.id)?.place;
        if (!current) return;
        if (current.memberIds.length > 1) {
          map.setCenter(new api.LatLng(current.latitude, current.longitude));
          map.setLevel(Math.max(1, map.getLevel() - 2));
          setZoomLevel(map.getLevel());
        } else latest.current.onSelect({ id: current.id, source: 'map' });
      };
      button.addEventListener('click', select);
      const overlay = new api.CustomOverlay({
        position: new api.LatLng(place.latitude, place.longitude),
        content: button,
        yAnchor: 1.2,
        zIndex: 1,
      });
      overlay.setMap(map);
      const dispose = () => {
        button.removeEventListener('click', select);
        overlay.setMap(null);
      };
      currentPins.set(place.id, { place, overlay, button, dispose });
    }
  }, [context, groupedPlaces]);

  const selectedId = selection?.id;
  useEffect(() => {
    if (!context) return;
    pins.current.forEach(pin => {
      const selected = pin.place.id === selectedId;
      const pressed = String(selected);
      pin.button.setAttribute('aria-pressed', pressed);
      pin.button.dataset.highlighted = String(pin.place.id === hoveredId);
      pin.overlay.setZIndex(selected || pin.place.id === hoveredId ? 10 : 1);
    });
  }, [context, groupedPlaces, selectedId, hoveredId]);

  useEffect(() => {
    if (!context || selection?.source !== 'list') return;
    const selected = latest.current.places.find(place => place.id === selection.id);
    if (!selected) return;
    const { api, map, moveWithoutSearch } = context;
    const bounds = map.getBounds();
    const sw = bounds.getSouthWest();
    const ne = bounds.getNorthEast();
    const { latitude, longitude } = selected;
    if (latitude >= sw.getLat() && latitude <= ne.getLat()
      && longitude >= sw.getLng() && longitude <= ne.getLng()) return;
    moveWithoutSearch(() => map.setCenter(new api.LatLng(latitude, longitude)));
  }, [context, selection]);

  return (
    <div className="relative size-full bg-[#edf1f5]">
      <div ref={container} className="absolute inset-0" role="region" aria-label="장소 지도" />
      <p className="pointer-events-none absolute top-4 left-4 z-[2] rounded-lg bg-[#fffffff0] px-3 py-2 text-[11px] text-[#596a7e] shadow-[0_2px_8px_#16243b12]" role="status">
        {searchingArea ? '이 영역의 장소를 찾는 중' : totalPlaces && places.length === 0 ? '위치 정보가 없는 장소는 목록에서 살펴보세요.' : totalPlaces !== undefined && totalPlaces > places.length
          ? `${totalPlaces}곳 중 ${places.length}곳을 표시 중이에요. 지도를 확대해 좁은 범위를 살펴보세요.`
          : '지도 범위의 장소를 표시합니다'}
        {places.some(place => place.name.startsWith('[데모]')) && ' · 데모 좌표는 실제 시설 위치가 아닙니다'}
      </p>
      {!context && !error && <p className={mapMessageClassName} role="status">지도를 불러오고 있어요.</p>}
      {error && (
        <div className={mapMessageClassName} role="alert">
          <p>지도를 불러오지 못했어요. 목록으로 계속 살펴볼 수 있어요.</p>
          <p className="mt-2 text-[11px]">카카오맵 사용 설정·허용 도메인 또는 네트워크 연결을 확인해 주세요.</p>
          <button className={mapRetryButtonClassName} type="button" onClick={() => { setError(false); setContext(null); setAttempt(value => value + 1); }}>지도 다시 불러오기</button>
        </div>
      )}
    </div>
  );
}
