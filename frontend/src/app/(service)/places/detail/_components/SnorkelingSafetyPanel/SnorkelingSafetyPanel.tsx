'use client';

import { useQuery } from '@tanstack/react-query';
import { getSnorkelingSafety } from '@/domains/place/api/placeApi';
import { safeHttpUrl } from '@/domains/place/lib/placeLinks';

const statusLabels: Record<string, string> = {
  ALLOWED: '입수 허용 확인', RESTRICTED: '입수 제한', PROHIBITED: '입수 금지', UNKNOWN: '입수 가능 여부 미확인',
};
const typeLabels: Record<string, string> = {
  DESIGNATED_BEACH: '지정 해수욕장', NATURAL_BEACH: '자연해변', ROCKY_COAST: '암반 해안',
  PORT: '포구', BREAKWATER: '방파제 인근', FISHING_VILLAGE: '어촌체험마을',
  EXPERIENCE_CENTER: '전문 체험시설', GUIDED_TOUR: '가이드 체험',
};
const safetyLabels: Record<string, string> = {
  allowedHours: '입수 허용 시간', allowedArea: '입수 허용 구역', controlStatus: '현장 통제 상태',
  lifeguardStatus: '안전요원', vesselTraffic: '선박 통행', tetrapodRisk: '테트라포드',
  slipperyRocks: '미끄러운 암반', suddenDepthChange: '급격한 수심 변화',
  currentsAndWaves: '조류·파도', requiredSafetyEquipment: '필수 안전장비',
  guardianRule: '보호자 동반 규정', managementContact: '관리기관 연락처',
};
const detailLabels: Record<string, string> = {
  coastalTerrain: '해안 지형', depthRange: '수심 범위', underwaterVisibility: '수중 시야',
  entryExit: '입수·출수 환경', marineLife: '해양생물 관찰 정보', currentInfo: '조류 정보',
  entryPoint: '입수 지점', amenities: '편의시설', requiredItems: '준비물',
  reservationInfo: '예약 안내', programInfo: '체험 프로그램',
};

function display(value: unknown): string {
  if (value == null) return '미확인';
  if (typeof value === 'string') return value;
  if (typeof value === 'boolean') return value ? '확인됨' : '없음이 확인됨';
  if (Array.isArray(value)) return value.map(display).join(', ');
  if (typeof value === 'object' && 'text' in value && typeof value.text === 'string') return value.text;
  return '상세 내용은 출처에서 확인';
}

export default function SnorkelingSafetyPanel({ placeId, activityDetails }: {
  placeId: number; activityDetails: Record<string, unknown>;
}) {
  const safety = useQuery({ queryKey: ['places', placeId, 'snorkeling-safety'],
    queryFn: ({ signal }) => getSnorkelingSafety(placeId, signal) });
  if (safety.isPending) return <section aria-label="스노클링 안전정보" className="border-t border-[#e9edf3] py-7">안전정보를 확인하고 있어요.</section>;
  if (safety.isError) return <section aria-label="스노클링 안전정보" className="border-t border-[#e9edf3] py-7" role="alert">안전정보를 불러오지 못했습니다. 입수 가능 여부를 현장에서 확인해 주세요.</section>;
  const data = safety.data;
  const source = safeHttpUrl(data.sourceUrl);
  const details = Object.entries(data.verifiedSafetyDetails ?? {})
    .filter(([key]) => key in safetyLabels);
  const siteDetails = Object.entries(activityDetails).filter(([key]) => key in detailLabels);
  return (
    <section aria-labelledby="snorkeling-safety-heading" className="border-t border-[#e9edf3] py-7">
      <h2 id="snorkeling-safety-heading" className="mb-4 text-[18px] font-bold">스노클링 안전·이용 안내</h2>
      <div className="rounded-xl border border-[#d9deef] bg-[#f7f8ff] p-5">
        <p className="font-bold">{statusLabels[data.entryStatus] ?? statusLabels.UNKNOWN}</p>
        <p className="mt-2 text-sm text-[#596579]">{data.recommendable
          ? '확인된 허용 기간과 안전정보를 확인하세요. 방문 전 현장 통제 안내도 다시 확인해 주세요.'
          : '현재 스노클링 방문 추천 대상이 아닙니다. 입수 가능 여부를 추정하지 마세요.'}</p>
        <p className="mt-3 text-sm">장소 유형: {typeLabels[data.siteType] ?? '미확인'}</p>
      </div>
      {details.length > 0 && <div className="mt-5"><h3 className="font-bold">확인된 현장 정보</h3>
        <dl className="mt-2 space-y-2 text-sm">{details.map(([key, value]) =>
          <div key={key} className="flex gap-3"><dt className="min-w-28 text-[#596579]">{safetyLabels[key]}</dt><dd>{display(value)}</dd></div>)}</dl>
      </div>}
      {data.confirmedWarnings.length > 0 && <div className="mt-5"><h3 className="font-bold">확인된 주의사항</h3>
        <ul className="mt-2 list-disc pl-5 text-sm">{data.confirmedWarnings.map(item => <li key={item}>{item}</li>)}</ul></div>}
      <div className="mt-5"><h3 className="font-bold">유형별 공통 주의사항</h3>
        <p className="mt-1 text-xs text-[#718096]">아래 내용은 공통 안전 수칙이며, 해당 장소에서 위험이 확인됐다는 뜻은 아닙니다.</p>
        <ul className="mt-2 list-disc pl-5 text-sm">{data.commonPrecautions.map(item => <li key={item}>{item}</li>)}</ul></div>
      <div className="mt-5"><h3 className="font-bold">자연환경·편의·준비 정보</h3>
        {siteDetails.length ? <dl className="mt-2 space-y-2 text-sm">{siteDetails.map(([key, value]) =>
          <div key={key} className="flex gap-3"><dt className="min-w-28 text-[#596579]">{detailLabels[key]}</dt>
            <dd>{display(value)}</dd></div>)}</dl> :
          <p className="mt-2 text-sm text-[#718096]">검증된 현장 정보가 없습니다.</p>}
      </div>
      <p className="mt-5 text-xs text-[#718096]">최근 확인: {data.checkedAt?.slice(0, 10) ?? '미확인'} · 정보 출처: {source
        ? <a href={source} target="_blank" rel="noopener noreferrer" className="underline">공식 안내 열기</a> : '미확인'}</p>
    </section>
  );
}
