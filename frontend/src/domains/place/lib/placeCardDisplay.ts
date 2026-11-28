/** List cards show administrative areas only; the detail retains the original address. */
export function compactPlaceAddress(address: string | null | undefined): string | null {
  if (!address?.trim() || /미확인|정보 없음/.test(address)) return null;
  const parts = address.trim().split(/\s+/);
  const area: string[] = [];
  for (const part of parts) {
    if (/(?:특별자치도|특별자치시|특별시|광역시|도|시|군|구)$/.test(part) || (!area.length && /^(서울|경기|인천|강원|충북|충남|전북|전남|경북|경남|부산|대구|대전|광주|울산|세종|제주)$/.test(part))) area.push(part);
    else break;
  }
  return area.length ? area.join(' ') : null;
}
export function visibleCardPrice(price: string | null | undefined, category = ''): string | null {
  if (/해수욕장|스노클링/.test(category) || !price?.trim() || /미확인|정보\s*없음|미등록|UNKNOWN/i.test(price)) return null;
  return price.trim();
}
