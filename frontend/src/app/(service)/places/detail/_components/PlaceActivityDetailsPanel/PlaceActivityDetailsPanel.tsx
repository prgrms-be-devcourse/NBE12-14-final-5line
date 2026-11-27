const labels: Record<string, string> = {
  freeSkatingHours: '자유 스케이팅', resurfacingTimes: '정빙 시간', skateRental: '스케이트 대여',
  openingDate: '개장일', closingDate: '폐장일', swimmingHours: '입수 가능 시간',
  amenities: '편의시설', safetyInfo: '안전 안내', siteType: '장소 유형',
  experiencePrograms: '체험 프로그램', coastalTerrain: '해안 지형', depthRange: '수심',
  underwaterVisibility: '수중 시야', entryExit: '입수·출수 환경',
  ticketTypes: '이용권 종류', indoorFacilities: '실내 시설', outdoorFacilities: '실외 시설',
  usageRestrictions: '이용 제한', course: '코스', passengerLimit: '탑승 인원',
  liftHours: '리프트 운영', slopes: '슬로프', operatingSeason: '운영 시즌',
  ageRestriction: '연령 제한', guardianRule: '보호자 동반',
};
const keys: Record<string, string[]> = {
  아이스스케이팅: ['freeSkatingHours', 'resurfacingTimes', 'skateRental'],
  해수욕장: ['openingDate', 'closingDate', 'swimmingHours', 'amenities', 'safetyInfo'],
  스노클링: ['siteType', 'experiencePrograms', 'coastalTerrain', 'depthRange', 'underwaterVisibility', 'entryExit'],
  워터파크: ['ticketTypes', 'indoorFacilities', 'outdoorFacilities', 'operatingSeason', 'usageRestrictions'],
  루지: ['course', 'passengerLimit', 'usageRestrictions'],
  레일바이크: ['course', 'passengerLimit', 'usageRestrictions'],
  '중력 레이싱': ['course', 'passengerLimit', 'usageRestrictions'],
  스키: ['liftHours', 'slopes', 'operatingSeason'],
  눈썰매: ['operatingSeason', 'ageRestriction', 'guardianRule'],
};
function display(value: unknown): string {
  if (typeof value === 'string' || typeof value === 'number') return String(value);
  if (Array.isArray(value)) return value.map(display).join(', ');
  if (value && typeof value === 'object' && 'text' in value && typeof value.text === 'string') return value.text;
  return '공식 안내에서 확인';
}
export default function PlaceActivityDetailsPanel({ activities, facts, operating }: {
  activities: string[]; facts: Record<string, unknown>; operating: Record<string, unknown>;
}) {
  const visible = [...new Set(activities)].map(name => ({ name, fields: (keys[name] ?? [])
    .filter(key => Object.hasOwn(facts, key) || Object.hasOwn(operating, key)) }))
    .filter(item => item.fields.length > 0);
  return <section aria-labelledby="activity-details-heading" className="border-t border-[#e9edf3] py-7">
    <h2 id="activity-details-heading" className="mb-3 text-[18px] font-bold">종목별 확인 정보</h2>
    {visible.length === 0 ? <p className="text-[13px] text-[#718096]">검증된 종목별 상세정보가 없습니다. 방문 전 공식 안내를 확인하세요.</p> :
      visible.map(item => <div key={item.name} className="mb-4 rounded-lg border border-[#e9edf3] p-4 text-[13px]">
        <h3 className="font-bold">{item.name}</h3><dl className="mt-2 space-y-2">{item.fields.map(key =>
          <div key={key} className="grid grid-cols-[120px_1fr] gap-3"><dt className="text-[#8793a7]">{labels[key]}</dt>
            <dd className="wrap-anywhere">{display(facts[key] ?? operating[key])}</dd></div>)}</dl>
      </div>)}
  </section>;
}
