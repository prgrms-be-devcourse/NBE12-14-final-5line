/** 이 화면에서 사용하는 공식 Kakao Maps SDK 인터페이스의 부분 타입입니다. */
export type KakaoLatLng = { getLat(): number; getLng(): number };
export type PlaceMapBounds = { south: number; west: number; north: number; east: number };
export type PlaceMapView = { lat: number; lng: number; level: number };
export type PlaceMapItem = { id: number; name: string; latitude: number; longitude: number };
export type PlaceMapSelection = { id: number; source: 'list' | 'map' | 'restore' };
export type KakaoBounds = {
  extend(position: KakaoLatLng): void;
  getSouthWest(): KakaoLatLng;
  getNorthEast(): KakaoLatLng;
};
export type KakaoMap = {
  getBounds(): KakaoBounds;
  getCenter(): KakaoLatLng;
  getLevel(): number;
  setBounds(bounds: KakaoBounds, top?: number, right?: number, bottom?: number, left?: number): void;
  setCenter(position: KakaoLatLng): void;
  setLevel(level: number): void;
  relayout(): void;
  addControl(control: object, position: number): void;
};
export type KakaoOverlay = {
  setMap(map: KakaoMap | null): void;
  setPosition(position: KakaoLatLng): void;
  setZIndex(index: number): void;
};
type KakaoMapEvent = 'idle' | 'bounds_changed' | 'dragstart' | 'zoom_start';
export type KakaoMapsApi = {
  event: {
    addListener(map: KakaoMap, name: KakaoMapEvent, handler: () => void): void;
    removeListener(map: KakaoMap, name: KakaoMapEvent, handler: () => void): void;
  };
  load(callback: () => void): void;
  Map: new (element: HTMLElement, options: { center: KakaoLatLng; level: number }) => KakaoMap;
  LatLng: new (latitude: number, longitude: number) => KakaoLatLng;
  LatLngBounds: new () => KakaoBounds;
  CustomOverlay: new (options: { position: KakaoLatLng; content: HTMLElement; yAnchor: number; zIndex: number }) => KakaoOverlay;
  ZoomControl: new () => object;
  ControlPosition: { RIGHT: number };
};

declare global {
  interface Window {
    kakao?: { maps: KakaoMapsApi };
  }
}
