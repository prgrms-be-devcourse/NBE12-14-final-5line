import type { PlaceMapItem } from '../types/kakaoMap.type';
export type PlaceCluster = PlaceMapItem & { memberIds: number[] };
/** Zoom-dependent spatial buckets; source IDs remain unchanged and no data is merged. */
export function clusterPlaces(places: PlaceMapItem[], level: number, activeId?: number | null, selectedId?: number): PlaceCluster[] {
  const buckets = new Map<string, PlaceMapItem[]>();
  const cell = 360 / 2 ** (20 - level) * .25;
  for (const place of places) {
    const key = level <= 7 || (place.id === activeId || place.id === selectedId) ? `place:${place.id}` : `${Math.floor(place.latitude / cell)}:${Math.floor(place.longitude / cell)}`;
    const bucket = buckets.get(key) ?? [];
    bucket.push(place);
    buckets.set(key, bucket);
  }
  return Array.from(buckets.values(), members => ({
    ...members[0],
    latitude: members.reduce((sum, place) => sum + place.latitude, 0) / members.length,
    longitude: members.reduce((sum, place) => sum + place.longitude, 0) / members.length,
    memberIds: members.map(place => place.id),
  }));
}
