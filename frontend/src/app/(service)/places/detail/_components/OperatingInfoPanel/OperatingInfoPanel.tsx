import type { PlaceOperatingInfoData } from '@/domains/place/types/placeApi.type';
import { safeHttpUrl } from '@/domains/place/lib/placeLinks';

export const placeFactsClassName = '[&>div]:mt-[15px] [&>div]:grid [&>div]:grid-cols-[95px_1fr] [&>div]:gap-3 [&>div]:text-xs/[1.8] [&_dt]:text-[#8793a7] [&_dd]:wrap-anywhere';

export default function OperatingInfoPanel({ info }: { info?: PlaceOperatingInfoData | null }) {
  if (!info) return <p>아직 수집된 운영 안내가 없습니다. 방문 전 공식 안내를 확인하세요.</p>;
  const verified = info.verificationStatus === 'VERIFIED' && info.verifiedAt !== null;
  const label = verified ? '검수된 안내' : info.verificationStatus === 'NEEDS_REVIEW' ? '확인 필요' : '미검수';
  const source = safeHttpUrl(info.sourceUrl);
  const official = safeHttpUrl(info.officialSourceUrl);
  return <div>
    <p><strong>{label}</strong> · 공공데이터에서 수집한 참고 안내</p>
    <dl className={placeFactsClassName}>
      <div><dt>이용시간</dt><dd className="wrap-anywhere whitespace-pre-line">{info.operatingHours ?? '제공된 정보 없음'}</dd></div>
      <div><dt>휴무일</dt><dd className="wrap-anywhere whitespace-pre-line">{info.restDays ?? '제공된 정보 없음'}</dd></div>
      <div><dt>문의전화</dt><dd>{info.contactPhone ?? '제공된 정보 없음'}</dd></div>
      <div><dt>수집일</dt><dd>{info.collectedAt.replace('T', ' ')} (한국 시간)</dd></div>
      <div><dt>검수일</dt><dd>{verified ? info.verifiedAt!.replace('T', ' ') : '검수 완료 전'}</dd></div>
      {source && <div><dt>수집 출처</dt><dd><a href={source} target="_blank" rel="noopener noreferrer">공공데이터 제공 안내 ↗</a></dd></div>}
    </dl>
    {info.reviewNote && <p className="border-l-[3px] border-[#b68618] bg-[#fff8e6] p-3 wrap-anywhere whitespace-pre-line">{info.reviewNote}</p>}
    {info.comparedAt && <p>공식 안내 대조일: {info.comparedAt.slice(0, 10)}</p>}
    {official && <p><a href={official} target="_blank" rel="noopener noreferrer">대조한 공식 안내 확인 ↗</a></p>}
    {!verified && <p>수집일은 최신 정보가 확인된 날짜가 아닙니다. 방문 전 공식 안내나 문의전화로 확인하세요.</p>}
  </div>;
}
