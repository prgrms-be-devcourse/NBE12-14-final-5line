import { describe, expect, it } from 'vitest';
import { clusterPlaces } from './clusterPlaces';
const places = [{ id: 1, name: '장소 1', latitude: 37.50001, longitude: 127.10001 }, { id: 2, name: '장소 2', latitude: 37.50002, longitude: 127.10002 }];
describe('실제 장소 좌표 클러스터', () => {
  it('축소할 때만 가까운 장소를 묶고 확대하면 개별 ID를 보존한다', () => {
    expect(clusterPlaces(places, 11)[0].memberIds).toEqual([1,2]);
    expect(clusterPlaces(places, 7).map(place => place.memberIds)).toEqual([[1],[2]]);
    expect(places[0].latitude).toBe(37.50001);
  });
  it('선택한 장소와 가리킨 장소를 동시에 개별 표시한다', () => {
    expect(clusterPlaces([...places,{...places[0],id:3}],11,2,3).map(place=>place.memberIds)).toEqual([[1],[2],[3]]);
  });
  it('카드에서 선택하거나 가리킨 장소는 묶음에서 분리한다', () => {
    expect(clusterPlaces(places, 11, 2).map(place => place.memberIds)).toEqual([[1],[2]]);
  });
});
