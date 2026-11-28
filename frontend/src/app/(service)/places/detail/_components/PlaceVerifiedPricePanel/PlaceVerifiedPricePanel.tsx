import type { VerifiedPlacePrice } from '@/domains/place/types/placeApi.type';
import { safeHttpUrl } from '@/domains/place/lib/placeLinks';

const priceKinds: Record<string, string> = { ADMISSION: '이용권', RENTAL: '장비 대여', PARKING: '주차', FACILITY: '편의시설', EXPERIENCE: '체험' };
const units: Record<string, string> = { PERSON: '1인', VEHICLE: '차량', EQUIPMENT: '장비', GROUP: '단체' };
const days: Record<string, string> = { WEEKDAY: '평일', WEEKEND: '주말', HOLIDAY: '공휴일' };
const ages: Record<string, string> = { ADULT: '성인', YOUTH: '청소년', CHILD: '어린이' };

export default function PlaceVerifiedPricePanel({ prices, hideEmpty = true }: { prices: VerifiedPlacePrice[]; hideEmpty?: boolean }) {
  if (hideEmpty && prices.length === 0) return null;
  return <section aria-labelledby="verified-prices-heading" className="my-5 rounded-2xl border border-[#e9edf3] p-5 sm:p-6">
    <h2 id="verified-prices-heading" className="mb-3 text-[18px] font-bold">확인된 정상 이용요금</h2>
    {prices.length === 0 ? <p className="text-[13px] text-[#718096]">검증된 현재 요금이 없습니다. 가격 미확인은 무료를 뜻하지 않습니다.</p> :
      <details open={prices.length <= 4} className="group"><summary className="mb-4 cursor-pointer rounded-lg bg-[#F5F6F8] px-4 py-3 text-sm font-medium text-[#5942FF]">이용요금 {prices.length}개 · 상세 조건 보기</summary><ul className="space-y-3">{prices.map((price, index) => {
        const details = [price.ticketName || priceKinds[price.priceKind] || price.priceKind,
          price.ageBand && (ages[price.ageBand] ?? price.ageBand),
          price.dayType && (days[price.dayType] ?? price.dayType), price.seasonLabel,
          price.durationMinutes && `${price.durationMinutes}분`, price.useCount && `${price.useCount}회`,
          price.passengerCount && `${price.passengerCount}인승`, price.equipmentType,
          price.priceUnit && (units[price.priceUnit] ?? price.priceUnit),
          price.residencyCondition].filter(Boolean);
        const source = safeHttpUrl(price.sourceUrl);
        return <li key={`${price.priceKind}-${index}`} className="rounded-lg border border-[#e9edf3] p-4 text-[13px]">
          <div className="flex flex-wrap items-baseline justify-between gap-2"><strong>{details.join(' · ')}</strong>
            <span className="font-bold text-[#5147ff]">{price.freeOfCharge ? '무료' : price.amountKrw === null ? '금액 미확인' : `${price.amountKrw.toLocaleString()}원`}</span></div>
          {(price.mandatoryExtraCost || price.equipmentIncluded !== null) && <p className="mt-2 text-[#596579]">
            {price.mandatoryExtraCost ? '필수 추가 비용 · ' : ''}{price.equipmentIncluded === null ? '' : price.equipmentIncluded ? '장비 포함' : '장비 별도'}</p>}
          {price.rawPriceText && <p className="mt-2 text-[#596579]">원문 요금: {price.rawPriceText}</p>}
          <p className="mt-2 text-xs text-[#8793a7]">적용 기간: {price.validFrom ?? '시작일 미확인'} ~ {price.validUntil ?? '종료일 미확인'} · 검증일: {price.verifiedAt?.slice(0, 10) ?? '미확인'}
            {source && <> · <a className="underline" href={source} target="_blank" rel="noopener noreferrer">출처</a></>}</p>
        </li>;
      })}</ul></details>}
  </section>;
}
