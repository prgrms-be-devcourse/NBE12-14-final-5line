'use client';

import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { getFisheryProtection } from '@/domains/place/api/placeApi';
import type { FisheryRule } from '@/domains/place/types/placeApi.type';

function localDate() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
}
function RuleList({ rules }: { rules: FisheryRule[] }) {
  return <ul className="mt-3 space-y-3">{rules.map(rule =>
    <li className="rounded-lg border border-[#e2e6ee] bg-white p-3 text-sm" key={rule.ruleId}>
      <strong>{rule.resourceName}</strong> · {rule.ruleKind === 'CLOSED_SEASON' ? `금어기 ${rule.period ?? '미확인'}` : rule.threshold ?? '제한 내용 미확인'}
      <p className="mt-1 text-xs text-[#596579]">적용 지역: {rule.region ?? '미확인'} · 자료: {rule.sourceDocument}</p>
      {rule.noticeNumber && <p className="text-xs text-[#596579]">고시 번호: {rule.noticeNumber}</p>}
      <p className="mt-1 text-xs text-[#596579]">근거 문구: {rule.sourceExcerpt}</p>
      {rule.verificationStatus === 'VERIFIED' && <p className="text-xs text-[#596579]">최근 검증: {rule.lastVerifiedAt?.slice(0, 10) ?? '미확인'}</p>}
    </li>)}</ul>;
}

export default function FisheryProtectionPanel({ placeId, name, address, latitude, longitude, managementPhone, phoneVerified }: {
  placeId: number; name: string | null; address: string | null; latitude: number | null; longitude: number | null;
  managementPhone: string | null; phoneVerified: boolean;
}) {
  const [visitDate, setVisitDate] = useState(localDate);
  const [position, setPosition] = useState<string | null>(null);
  const [gpsMessage, setGpsMessage] = useState('');
  const [copyMessage, setCopyMessage] = useState('');
  const rules = useQuery({ queryKey: ['fishery-protection', placeId, visitDate],
    queryFn: ({ signal }) => getFisheryProtection(placeId, visitDate, signal), enabled: /^\d{4}-\d{2}-\d{2}$/.test(visitDate) });
  function locate() {
    if (!navigator.geolocation) { setGpsMessage('이 브라우저는 위치 확인을 지원하지 않습니다.'); return; }
    setGpsMessage('현재 위치를 확인하고 있어요.');
    navigator.geolocation.getCurrentPosition(p => {
      setPosition(`${p.coords.latitude.toFixed(6)}, ${p.coords.longitude.toFixed(6)}`);
      setGpsMessage('현재 위치를 확인했습니다.');
    }, () => setGpsMessage('위치를 확인할 수 없습니다. 위치 권한을 확인해 주세요.'),
    { enableHighAccuracy: true, timeout: 10000 });
  }
  async function copy(text: string) {
    try { await navigator.clipboard.writeText(text); setCopyMessage('복사했습니다.'); }
    catch { setCopyMessage('복사에 실패했습니다. 아래 정보를 길게 눌러 직접 복사해 주세요.'); }
  }
  const placeInfo = `${name ?? '장소명 미확인'}\n${address ?? '주소 미확인'}\n${latitude == null || longitude == null ? '장소 좌표 미확인' : `장소 좌표: ${latitude}, ${longitude}`}${position ? `\n현재 GPS: ${position}` : ''}`;
  const verifiedPhone = phoneVerified && managementPhone && /^[+\d()\s-]+$/.test(managementPhone) ? managementPhone : null;
  return <section aria-labelledby="fishery-heading" className="border-t border-[#e9edf3] py-7">
    <h2 id="fishery-heading" className="mb-3 text-[18px] font-bold">수산자원 보호·긴급 안내</h2>
    <p className="text-sm text-[#596579]">이 목록은 해당 장소에 생물이 서식한다는 뜻이 아닙니다. 입수 허용과 채취 허용은 별개입니다.</p>
    <label className="mt-5 block text-sm font-semibold" htmlFor="fishery-visit-date">방문 예정일</label>
    <input id="fishery-visit-date" type="date" value={visitDate} onChange={event => setVisitDate(event.target.value)}
      className="mt-2 rounded-lg border border-[#cbd3e0] p-2 text-sm" />
    {rules.isPending && <p className="mt-3 text-sm">규정을 확인하고 있어요.</p>}
    {rules.isError && <p className="mt-3 text-sm" role="alert">규정 정보를 불러오지 못했습니다. 채취 여부를 현장에서 확인해 주세요.</p>}
    {rules.data && <>
      <p className="mt-4 rounded-lg bg-[#fff6e5] p-3 text-sm">채취 가능 여부: <strong>미확인</strong>. {rules.data.advisory}</p>
      <h3 className="mt-5 font-bold">검증된 금어기 해당 항목</h3>
      {rules.data.confirmedClosed.length ? <RuleList rules={rules.data.confirmedClosed} /> : <p className="mt-2 text-sm text-[#596579]">현재 제공할 검증 완료 항목이 없습니다. 채취 허용을 뜻하지 않습니다.</p>}
      <h3 className="mt-5 font-bold">검증된 금지체장·체중</h3>
      {rules.data.confirmedLimits.length ? <RuleList rules={rules.data.confirmedLimits} /> : <p className="mt-2 text-sm text-[#596579]">검증 완료 항목이 없습니다.</p>}
      <details className="mt-5 rounded-lg border border-[#e2e6ee] p-3 text-sm"><summary className="cursor-pointer font-semibold">검수 대기 참고자료 {rules.data.reviewCandidates.length}건</summary>
        <p className="mt-2 text-xs text-[#596579]">2026년 1월 1일 기준 자료로 최신 법령·지역 고시 확인 전입니다. 법적 판단이나 채취 허용 근거로 사용하지 마세요.</p>
        <RuleList rules={rules.data.reviewCandidates} />
      </details>
    </>}
    <div className="mt-6 rounded-xl bg-[#f7f8ff] p-4 text-sm">
      <h3 className="font-bold">안전 수칙</h3>
      <p className="mt-2">구명조끼를 착용하고 혼자 입수하지 마세요. 음주 후나 기상 악화 시에는 입수하지 말고, 현장 통제와 마을어장·보호구역 안내를 따르세요. 채취 허용 여부를 확인하지 못했다면 관찰만 하세요.</p>
      <h3 className="mt-4 font-bold">긴급 신고</h3>
      <a className="mt-2 inline-block rounded-lg bg-[#5147ff] px-4 py-2 text-white" href="tel:119">119 전화 연결</a>
      {verifiedPhone && <a className="ml-2 inline-block rounded-lg border border-[#cbd3e0] px-4 py-2" href={`tel:${verifiedPhone.replace(/[^+\d]/g, '')}`}>확인된 관리기관 전화</a>}
      <p className="mt-3 text-xs text-[#596579]">관할 해양경찰서 번호는 아직 확인되지 않아 표시하지 않습니다.</p>
      <div className="mt-4 flex flex-wrap gap-2"><button type="button" onClick={locate} className="rounded-lg border border-[#cbd3e0] px-3 py-2">현재 GPS 확인</button>
        <button type="button" onClick={() => void copy(placeInfo)} className="rounded-lg border border-[#cbd3e0] px-3 py-2">장소 정보 복사</button>
        {position && <button type="button" onClick={() => void copy(position)} className="rounded-lg border border-[#cbd3e0] px-3 py-2">GPS 좌표 복사</button>}</div>
      {gpsMessage && <p className="mt-2" role="status">{gpsMessage}</p>}{copyMessage && <p className="mt-2" role="status">{copyMessage}</p>}
      <pre className="mt-3 whitespace-pre-wrap text-xs">{placeInfo}</pre>
    </div>
  </section>;
}
