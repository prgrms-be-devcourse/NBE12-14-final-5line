'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { getOperatingChanges, resolveOperatingChange } from '../api/adminOperatingChangeApi';
import type { OperatingChange, OperatingChangeResolution } from '../api/adminOperatingChangeApi';
import { safeHttpUrl } from '@/domains/place/lib/placeLinks';
import { placeQueryKeys } from '@/domains/place/queries/placeQueryKeys';

const listKey = ['admin', 'operating-changes'] as const;
const fieldLabels: Record<string, string> = {
  operatingHours: '이용시간',
  restDays: '휴무일',
  contactPhone: '문의전화',
  openingPeriod: '개장 기간',
  usageFee: '이용요금',
};
const button = 'rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold disabled:opacity-50';

export default function AdminOperatingChanges() {
  const [page, setPage] = useState(0);
  const [message, setMessage] = useState('');
  const queryClient = useQueryClient();
  const query = useQuery({ queryKey: [...listKey, page], queryFn: ({ signal }) => getOperatingChanges(page, signal) });
  const mutation = useMutation({
    mutationFn: resolveOperatingChange,
    onMutate: () => setMessage(''),
    onSuccess: async (_, { placeId, decision }) => {
      setMessage(decision === 'APPROVED' ? '변경 내용을 공개 상세에 반영했어요.' : '변경 후보를 반려했어요.');
      if (page > 0 && query.data?.items.length === 1) setPage(page - 1);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: listKey }),
        decision === 'APPROVED'
          ? queryClient.invalidateQueries({ queryKey: placeQueryKeys.detail(placeId), exact: true })
          : undefined,
      ]);
    },
  });

  return (
    <section className="mx-auto max-w-4xl space-y-5">
      <header>
        <h1 className="text-3xl font-extrabold tracking-tight">운영 정보 검수</h1>
        <p className="mt-2 text-sm text-slate-600">이전 값과 새 값을 비교해 주세요. 승인하면 해당 항목이 장소 상세에 반영돼요.</p>
      </header>
      {message && <p role="status" className="text-sm text-emerald-700">{message}</p>}
      {query.isPending && <p role="status">검수 후보를 불러오고 있어요.</p>}
      {query.isError && <div role="alert" className="flex items-center gap-3">
        <p>검수 후보를 불러오지 못했어요.</p>
        <button className={button} onClick={() => void query.refetch()}>다시 시도</button>
      </div>}
      {query.data && <>
        <p className="text-sm text-slate-600">검수 대기 {query.data.totalElements}건</p>
        {query.data.items.length === 0 && <p className="rounded-xl border border-slate-200 p-5">이 페이지에 검수할 후보가 없어요.</p>}
        {query.data.items.map(candidate => (
          <CandidateCard key={candidate.candidateId} candidate={candidate}
            disabled={mutation.isPending}
            error={mutation.variables?.candidateId === candidate.candidateId ? mutation.error?.message : undefined}
            resolve={mutation.mutate} />
        ))}
        {(page > 0 || (page + 1) * query.data.size < query.data.totalElements) && (
          <nav aria-label="검수 후보 페이지" className="flex gap-3">
            <button className={button} disabled={page === 0 || mutation.isPending} onClick={() => setPage(page - 1)}>이전</button>
            <button className={button} disabled={(page + 1) * query.data.size >= query.data.totalElements || mutation.isPending}
              onClick={() => setPage(page + 1)}>다음</button>
          </nav>
        )}
      </>}
    </section>
  );
}

function CandidateCard({ candidate, disabled, error, resolve }: {
  candidate: OperatingChange;
  disabled: boolean;
  error?: string;
  resolve: (input: OperatingChangeResolution) => void;
}) {
  const [reason, setReason] = useState('');
  const field = fieldLabels[candidate.field] ?? candidate.field;
  const reasonId = `review-reason-${candidate.candidateId}`;

  function submit(decision: OperatingChangeResolution['decision']) {
    resolve({ candidateId: candidate.candidateId, placeId: candidate.placeId, decision, reason: reason.trim() });
  }

  return (
    <article className="space-y-4 rounded-xl border border-slate-200 bg-white p-5">
      <header>
        <h2 className="text-lg font-bold">{candidate.placeName ?? `장소 #${candidate.placeId}`}</h2>
        <p className="text-sm text-slate-600">{field} · {candidate.detectedAt.replace('T', ' ').slice(0, 19)}</p>
      </header>
      <dl className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <ComparedValue label="이전 값" value={candidate.previousValue} url={candidate.previousSourceUrl} />
        <ComparedValue label="새 값" value={candidate.newValue} url={candidate.newSourceUrl} />
      </dl>
      <div>
        <label htmlFor={reasonId} className="text-sm font-semibold">검수 의견 <span className="font-normal text-slate-600">(반려 시 필수)</span></label>
        <textarea id={reasonId} rows={2} maxLength={500} value={reason} disabled={disabled}
          className="mt-1 block w-full rounded-lg border border-slate-300 p-2 text-sm"
          onChange={event => setReason(event.target.value)} />
      </div>
      {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
      <div className="flex justify-end gap-3">
        <button type="button" className={button} disabled={disabled || !reason.trim()} onClick={() => submit('REJECTED')}>반려</button>
        <button type="button" className={`${button} border-indigo-700 bg-indigo-700 text-white`}
          disabled={disabled} onClick={() => submit('APPROVED')}>승인</button>
      </div>
    </article>
  );
}

function ComparedValue({ label, value, url }: { label: string; value: string | null; url: string }) {
  const link = safeHttpUrl(url);
  return <div className="min-w-0 rounded-lg bg-slate-50 p-3">
    <dt className="text-sm font-semibold text-slate-600">{label}</dt>
    <dd className="mt-1 whitespace-pre-wrap wrap-anywhere">{value ?? '—'}</dd>
    {link && <dd className="mt-2 text-sm">
      <a className="text-indigo-700 underline" href={link} target="_blank" rel="noopener noreferrer">{label} 출처</a>
    </dd>}
  </div>;
}
