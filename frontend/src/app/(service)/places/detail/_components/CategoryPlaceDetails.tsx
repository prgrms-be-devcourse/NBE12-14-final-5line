import { safeHttpUrl } from '@/domains/place/lib/placeLinks';

export type PlaceEvidence = { label: string; text: string; sourceUrl: string; checkedAt: string; status: string };
const panel = 'rounded-2xl border border-[#e5e3ef] bg-white p-5 sm:p-6';
const pricePattern = /요금|가격|입장료|이용료|대여|비용|금액|이용권/;
const amenityPattern = /편의시설|주차|화장실|샤워|안전시설/;
const operationPattern = /운영|개장|폐장|휴무|출발|운행|자유 스케이팅|정빙/;
function EvidenceSection({ title, items }: { title: string; items: PlaceEvidence[] }) {
  if (!items.length) return null;
  return <section className={panel} aria-label={title}><h3 className="text-lg font-bold text-[#17172B]">{title}</h3>
    <dl className="mt-3 divide-y divide-slate-100">{items.map((item, index) => {
      const source = safeHttpUrl(item.sourceUrl);
      return <div key={`${item.label}-${index}`} className="grid gap-2 py-4 sm:grid-cols-[130px_minmax(0,1fr)]">
        <dt className="text-sm font-semibold text-slate-700">{item.label}</dt>
        <dd className="min-w-0 text-sm leading-7 text-slate-600"><p className="whitespace-pre-line break-words">{item.text}</p>
          <details className="mt-2 text-xs text-slate-500"><summary className="w-fit cursor-pointer rounded py-1 text-[#5942FF] focus-visible:outline-2">출처와 확인 상태</summary>
            <p className="mt-1">{item.status === 'VERIFIED' ? '확인된 안내' : '방문 전 현재 적용 여부를 확인해 주세요.'}{item.checkedAt ? ` · 자료 확인일 ${item.checkedAt.slice(0, 10)}` : ''}</p>
            {source && <a href={source} target="_blank" rel="noopener noreferrer" className="mt-1 inline-block underline">원문 안내 ↗</a>}
          </details>
        </dd>
      </div>;
    })}</dl>
  </section>;
}
function ActivityInformation({ items, operations, prices, specialty }: { items: PlaceEvidence[]; operations: string; prices: string; specialty: string }) {
  const meaningful = items.filter(item => item.text?.trim() && !['수집일', '검수일', '공식 안내 대조일', '주소', '문의전화'].includes(item.label));
  const operationItems = meaningful.filter(item => operationPattern.test(item.label) && !pricePattern.test(item.label));
  const priceItems = meaningful.filter(item => pricePattern.test(item.label));
  const amenities = meaningful.filter(item => amenityPattern.test(item.label) && !pricePattern.test(item.label));
  const remaining = meaningful.filter(item => !operationItems.includes(item) && !priceItems.includes(item) && !amenities.includes(item));
  return <div className="space-y-5"><EvidenceSection title={operations} items={operationItems}/><EvidenceSection title={prices} items={priceItems}/><EvidenceSection title={specialty} items={remaining}/><EvidenceSection title="주차 및 편의시설" items={amenities}/></div>;
}
export function SkiDetail({ items }: { items: PlaceEvidence[] }) { return <ActivityInformation items={items} operations="스키장 운영 안내" prices="리프트권 및 장비 대여 안내" specialty="스키 이용 안내"/>; }
export function LugeRailbikeDetail({ items, railbike }: { items: PlaceEvidence[]; railbike: boolean }) { return <ActivityInformation items={items} operations={railbike ? '운행 시간표' : '운영시간'} prices={railbike ? '차량·탑승 인원별 요금' : '탑승 횟수별 요금'} specialty={railbike ? '탑승 인원 및 운행 안내' : '탑승 제한 및 이용 안내'}/>; }
export function SnowSledDetail({ items }: { items: PlaceEvidence[] }) { return <ActivityInformation items={items} operations="눈썰매장 운영 안내" prices="입장 및 썰매 이용요금" specialty="장비 및 이용 제한"/>; }
export function WaterparkDetail({ items }: { items: PlaceEvidence[] }) { return <ActivityInformation items={items} operations="워터파크 운영 안내" prices="입장권 요금 안내" specialty="시설 및 이용 안내"/>; }
export function IceSkatingDetail({ items }: { items: PlaceEvidence[] }) { return <ActivityInformation items={items} operations="자유 스케이팅 및 정빙 시간" prices="입장료 안내" specialty="스케이팅 이용 제한"/>; }
export function SnorkelingDetail({ items }: { items: PlaceEvidence[] }) { return <ActivityInformation items={items.filter(item => !pricePattern.test(item.label))} operations="현장 안내" prices="" specialty="확인된 장소 및 위험정보"/>; }
export function BeachDetail({ items }: { items: PlaceEvidence[] }) { return <ActivityInformation items={items.filter(item => !pricePattern.test(item.label))} operations="해수욕장 개장 기간 및 운영 안내" prices="" specialty="해변 이용 안내"/>; }
export default function CategoryPlaceDetails({ activities, items }: { activities: string[]; items: PlaceEvidence[] }) {
  if (activities.includes('스노클링')) return <SnorkelingDetail items={items}/>;
  if (activities.includes('해수욕장')) return <BeachDetail items={items}/>;
  if (activities.includes('스키')) return <SkiDetail items={items}/>;
  if (activities.includes('눈썰매')) return <SnowSledDetail items={items}/>;
  if (activities.includes('워터파크')) return <WaterparkDetail items={items}/>;
  if (activities.includes('아이스스케이팅')) return <IceSkatingDetail items={items}/>;
  if (activities.some(name => /루지|레일바이크|중력/.test(name))) return <LugeRailbikeDetail items={items} railbike={activities.includes('레일바이크')}/>;
  return <ActivityInformation items={items} operations="운영 안내" prices="이용요금 안내" specialty="이용 안내"/>;
}
