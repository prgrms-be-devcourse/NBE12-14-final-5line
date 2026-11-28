import { describe, expect, it } from 'vitest';
import { compactPlaceAddress, visibleCardPrice } from './placeCardDisplay';
describe('장소 카드 표시', () => {
  it.each(['요금 미확인', '정보 없음', '', null])('미확인 요금을 숨긴다: %s', price => expect(visibleCardPrice(price)).toBeNull());
  it.each(['해수욕장', '스노클링'])('%s는 무료를 포함한 가격 영역을 숨긴다', category => expect(visibleCardPrice('무료', category)).toBeNull());
  it('실제 정상 요금은 유지한다', () => expect(visibleCardPrice('기본 입장료 15,000원')).toBe('기본 입장료 15,000원'));
  it.each([['서울특별시 송파구 올림픽로 240', '서울특별시 송파구'], ['경기도 고양시 일산동구 중앙로 1', '경기도 고양시 일산동구'], ['세종특별자치시 한누리대로 1', '세종특별자치시'], ['제주특별자치도 서귀포시 성산읍 해안로 1', '제주특별자치도 서귀포시']])('상세 주소는 노출하지 않는다: %s', (address, result) => expect(compactPlaceAddress(address)).toBe(result));
});
