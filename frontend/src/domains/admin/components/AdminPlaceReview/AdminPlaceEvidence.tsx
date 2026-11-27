import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { FormEvent } from 'react';
import { createAdminFact, createAdminPrice, getAdminEvidence, reviewAdminEvidence } from '../../api/adminApi';
import { placeField, placeFieldLabel, placeSecondaryButton } from '../adminPlaceStyles';

type ReviewStatus = 'VERIFIED' | 'NEEDS_REVIEW' | 'REJECTED';
const labels: Record<ReviewStatus, string> = { VERIFIED: '검증됨', NEEDS_REVIEW: '검수 대기', REJECTED: '제외' };

export default function AdminPlaceEvidence({ placeId }: { placeId: number }) {
  const client = useQueryClient();
  const key = ['admin', 'places', 'evidence', placeId];
  const evidence = useQuery({ queryKey: key, queryFn: ({ signal }) => getAdminEvidence(placeId, signal) });
  const addPrice = useMutation({ mutationFn: (input: Record<string, unknown>) => createAdminPrice(placeId, input),
    onSuccess: async () => { await client.invalidateQueries({ queryKey: key }); } });
  const addFact = useMutation({ mutationFn: (input: Record<string, unknown>) => createAdminFact(placeId, input),
    onSuccess: async () => { await client.invalidateQueries({ queryKey: key }); } });
  const text = (data: FormData, name: string) => String(data.get(name) ?? '').trim();
  const optional = (data: FormData, name: string) => text(data, name) || null;
  const number = (data: FormData, name: string) => text(data, name) === '' ? null : Number(text(data, name));
  const addPriceDraft = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault(); const form = event.currentTarget; const data = new FormData(form);
    addPrice.mutate({ priceKind: text(data, 'priceKind'), ticketName: optional(data, 'ticketName'),
      amountKrw: number(data, 'amountKrw'), freeOfCharge: data.has('freeOfCharge'),
      ageBand: optional(data, 'ageBand'), dayType: optional(data, 'dayType'), seasonLabel: optional(data, 'seasonLabel'),
      priceUnit: optional(data, 'priceUnit'), durationMinutes: number(data, 'durationMinutes'),
      useCount: number(data, 'useCount'), passengerCount: number(data, 'passengerCount'),
      equipmentType: optional(data, 'equipmentType'), rawPriceText: optional(data, 'rawPriceText'),
      sourceKind: text(data, 'sourceKind'), sourceUrl: text(data, 'sourceUrl'),
      checkedAt: text(data, 'checkedAt'), validFrom: optional(data, 'validFrom'), validUntil: optional(data, 'validUntil') },
      { onSuccess: () => form.reset() });
  };
  const addFactDraft = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault(); const form = event.currentTarget; const data = new FormData(form);
    addFact.mutate({ fieldKey: text(data, 'fieldKey'), valueJson: text(data, 'valueJson'),
      sourceKind: text(data, 'sourceKind'), sourceUrl: text(data, 'sourceUrl'),
      checkedAt: text(data, 'checkedAt'), validFrom: optional(data, 'validFrom'), validUntil: optional(data, 'validUntil') },
      { onSuccess: () => form.reset() });
  };
  const review = useMutation({ mutationFn: ({ kind, id, status }: { kind: 'prices' | 'facts'; id: number; status: ReviewStatus }) =>
    reviewAdminEvidence(placeId, kind, id, status),
    onSuccess: async () => { await client.invalidateQueries({ queryKey: key }); } });
  const controls = (kind: 'prices' | 'facts', id: number, status: ReviewStatus) => <div className="mt-2 flex flex-wrap gap-2">
    {(['VERIFIED', 'NEEDS_REVIEW', 'REJECTED'] as const).filter(next => next !== status).map(next =>
      <button key={next} type="button" className={placeSecondaryButton} disabled={review.isPending}
        onClick={() => review.mutate({ kind, id, status: next })}>{labels[next]}</button>)}
  </div>;
  return <section aria-labelledby="admin-evidence-heading" className="space-y-3">
    <h4 id="admin-evidence-heading" className="text-sm font-bold">가격·상세정보 검수</h4>
    <p className="text-xs text-slate-600">원본과 적용 기간을 직접 확인한 정보만 검증해 주세요. 미검증 가격은 공개되지 않습니다.</p>
    <details className="rounded-lg border border-slate-200 p-3 text-sm">
      <summary className="cursor-pointer font-semibold">가격 후보 추가</summary>
      <form className="mt-3 grid gap-2 sm:grid-cols-2" onSubmit={addPriceDraft}>
        <label className={placeFieldLabel}>가격 유형<select name="priceKind" className={placeField} defaultValue="ADMISSION"><option value="ADMISSION">입장료</option><option value="RENTAL">장비 대여료</option><option value="PARKING">주차료</option><option value="EXPERIENCE">체험료</option><option value="LIFT">리프트권</option></select></label>
        <label className={placeFieldLabel}>이용권 이름<input name="ticketName" className={placeField} maxLength={160} /></label>
        <label className={placeFieldLabel}>금액(원)<input name="amountKrw" className={placeField} type="number" min="0" /></label>
        <label className={placeFieldLabel}>무료 확인<input name="freeOfCharge" type="checkbox" /></label>
        <label className={placeFieldLabel}>연령 구분<input name="ageBand" className={placeField} /></label>
        <label className={placeFieldLabel}>요일 구분<input name="dayType" className={placeField} /></label>
        <label className={placeFieldLabel}>시즌<input name="seasonLabel" className={placeField} /></label>
        <label className={placeFieldLabel}>가격 단위<select name="priceUnit" className={placeField} defaultValue=""><option value="">미지정</option><option value="PERSON">1인</option><option value="VEHICLE">차량</option><option value="EQUIPMENT">장비</option><option value="GROUP">단체</option></select></label>
        <label className={placeFieldLabel}>이용 시간(분)<input name="durationMinutes" type="number" min="1" className={placeField} /></label>
        <label className={placeFieldLabel}>이용 횟수<input name="useCount" type="number" min="1" className={placeField} /></label>
        <label className={placeFieldLabel}>탑승 인원<input name="passengerCount" type="number" min="1" className={placeField} /></label>
        <label className={placeFieldLabel}>장비 종류<input name="equipmentType" className={placeField} /></label>
        <label className={placeFieldLabel}>적용 시작<input name="validFrom" type="date" className={placeField} /></label>
        <label className={placeFieldLabel}>적용 종료<input name="validUntil" type="date" className={placeField} /></label>
        <label className={placeFieldLabel}>출처 종류<select name="sourceKind" className={placeField}><option value="OFFICIAL_WEBSITE">공식 홈페이지</option><option value="OFFICIAL_PUBLIC_DATA">공공데이터</option><option value="OFFICIAL_TOURISM_ARTICLE">관광공사</option><option value="TOUR_API">TourAPI</option></select></label>
        <label className={placeFieldLabel}>출처 URL<input name="sourceUrl" type="url" required className={placeField} /></label>
        <label className={placeFieldLabel}>확인 시각<input name="checkedAt" type="datetime-local" required className={placeField} /></label>
        <label className={`${placeFieldLabel} sm:col-span-2`}>원문 요금<input name="rawPriceText" className={placeField} /></label>
        <button className={placeSecondaryButton} disabled={addPrice.isPending} type="submit">검수 대기로 저장</button>
        {addPrice.isError && <p role="alert" className="text-red-700">가격 후보를 저장하지 못했어요. 금액과 기간을 확인해 주세요.</p>}
      </form>
    </details>
    <details className="rounded-lg border border-slate-200 p-3 text-sm">
      <summary className="cursor-pointer font-semibold">상세·안전정보 후보 추가</summary>
      <form className="mt-3 grid gap-2 sm:grid-cols-2" onSubmit={addFactDraft}>
        <label className={placeFieldLabel}>정보 키<input name="fieldKey" required className={placeField} placeholder="예: snorkelingSafety" /></label>
        <label className={`${placeFieldLabel} sm:col-span-2`}>JSON 값<textarea name="valueJson" required className={placeField} rows={3} placeholder='{"entryStatus":"UNKNOWN"}' /></label>
        <label className={placeFieldLabel}>출처 종류<select name="sourceKind" className={placeField}><option value="OFFICIAL_WEBSITE">공식 홈페이지</option><option value="OFFICIAL_PUBLIC_DATA">공공데이터</option><option value="OFFICIAL_TOURISM_ARTICLE">관광공사</option><option value="TOUR_API">TourAPI</option></select></label>
        <label className={placeFieldLabel}>출처 URL<input name="sourceUrl" type="url" required className={placeField} /></label>
        <label className={placeFieldLabel}>확인 시각<input name="checkedAt" type="datetime-local" required className={placeField} /></label>
        <label className={placeFieldLabel}>적용 시작<input name="validFrom" type="date" className={placeField} /></label>
        <label className={placeFieldLabel}>적용 종료<input name="validUntil" type="date" className={placeField} /></label>
        <button className={placeSecondaryButton} disabled={addFact.isPending} type="submit">검수 대기로 저장</button>
        {addFact.isError && <p role="alert" className="text-red-700">상세정보 후보를 저장하지 못했어요. JSON과 출처를 확인해 주세요.</p>}
      </form>
    </details>
    {evidence.isPending && <p className="text-sm">정보를 불러오는 중…</p>}
    {evidence.isError && <p className="text-sm text-red-700">검수 정보를 불러오지 못했어요.</p>}
    {review.isError && <p role="alert" className="text-sm text-red-700">검수 상태를 저장하지 못했어요. 출처·확인일·적용 기간을 확인해 주세요.</p>}
    {evidence.data && <>
      <h5 className="text-sm font-semibold">가격 {evidence.data.prices.length}건</h5>
      {evidence.data.prices.length === 0 && <p className="text-sm text-slate-600">등록된 가격이 없어요.</p>}
      <ul className="space-y-2">{evidence.data.prices.map(price => <li key={price.id} className="min-w-0 rounded-lg border border-slate-200 p-3 text-xs wrap-anywhere">
        <strong>{price.ticketName || price.priceKind} · {price.freeOfCharge ? '무료' : price.amountKrw === null ? '금액 미확인' : `${price.amountKrw.toLocaleString()}원`}</strong>
        <p>{price.priceBasis} · {labels[price.status]} · 적용 {price.validFrom ?? '시작 미확인'} ~ {price.validUntil ?? '종료 미확인'}</p>
        <p>출처 {price.sourceUrl ?? '미등록'} · 확인일 {price.checkedAt?.slice(0, 10) ?? '미확인'}</p>
        {controls('prices', price.id, price.status)}
      </li>)}</ul>
      <h5 className="text-sm font-semibold">상세·안전정보 {evidence.data.facts.length}건</h5>
      {evidence.data.facts.length === 0 && <p className="text-sm text-slate-600">등록된 상세정보가 없어요.</p>}
      <ul className="space-y-2">{evidence.data.facts.map(fact => <li key={fact.id} className="min-w-0 rounded-lg border border-slate-200 p-3 text-xs wrap-anywhere">
        <strong>{fact.fieldKey} · {labels[fact.status]}</strong>
        <p>출처 {fact.sourceUrl ?? '미등록'} · 확인일 {fact.checkedAt?.slice(0, 10) ?? '미확인'}</p>
        <p>적용 {fact.validFrom ?? '시작 미확인'} ~ {fact.validUntil ?? '종료 미확인'}</p>
        <details><summary>원본 값 보기</summary><pre className="mt-2 whitespace-pre-wrap">{fact.valueJson}</pre></details>
        {controls('facts', fact.id, fact.status)}
      </li>)}</ul>
    </>}
  </section>;
}
