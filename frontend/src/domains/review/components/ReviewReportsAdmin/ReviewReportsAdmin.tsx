'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { ApiError } from '@/shared/lib/http/apiError';
import { dismissReviewReport, hideReview, pendingReviewReports, type ReviewReport } from '../../api/reviewApi';

const descriptionClassName = 'mt-3 leading-[1.7] text-[#718096]';
const reportTextClassName = 'mt-3.5 leading-[1.7] wrap-anywhere whitespace-pre-wrap [&>strong]:mb-[5px] [&>strong]:block [&>strong]:text-[#16243b]';
const reviewActionClassName = 'cursor-pointer rounded-lg border-0 px-3.5 py-2.5 disabled:cursor-default disabled:opacity-[.55]';

export default function ReviewReportsAdmin() {
  const [reports, setReports] = useState<ReviewReport[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    pendingReviewReports().then(data => { if (active) setReports(data); })
      .catch(cause => {
        if (!active) return;
        setError(cause instanceof ApiError && cause.details.kind === 'http' && cause.details.status === 403
          ? '운영자만 신고 내역을 볼 수 있습니다.' : '신고 내역을 불러오지 못했어요.');
      }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  async function processReport(report: ReviewReport, action: 'hide' | 'dismiss') {
    if (busyId !== null) return;
    setBusyId(report.id); setError('');
    try {
      if (action === 'hide') await hideReview(report.reviewId);
      else await dismissReviewReport(report.id);
      setReports(await pendingReviewReports());
    } catch { setError('신고를 처리하지 못했어요. 다시 시도해 주세요.'); }
    finally { setBusyId(null); }
  }

  return <div>
    <p className="text-[11px] font-extrabold tracking-[1.3px] text-[#6156ff]">OUTPICK · MODERATION</p>
    <h1 className="mt-[9px] text-[32px]">후기 신고 검토</h1>
    <p className={descriptionClassName}>접수된 사유와 후기 본문을 확인한 뒤 숨기거나 신고를 반려할 수 있습니다.</p>
    {loading && <p className={descriptionClassName} role="status">신고 내역을 불러오고 있어요.</p>}
    {error && <p role="alert" className="mt-3 rounded-lg bg-[#fff0f1] p-3 leading-[1.7] text-[#ab303c]">{error}</p>}
    {!loading && !error && reports.length === 0 && <p className={descriptionClassName}>대기 중인 신고가 없습니다.</p>}
    <ul className="mt-[25px] list-none p-0">{reports.map(report => <li key={report.id} className="mt-3.5 rounded-[14px] border border-[#e4e7ef] p-5">
      <div className="flex justify-between gap-3 text-[12px] text-[#8793a7]"><span>신고 #{report.id}</span><time dateTime={report.createdAt}>{report.createdAt.slice(0, 16).replace('T', ' ')}</time></div>
      <p className={reportTextClassName}><strong>신고 사유</strong> {report.reason}</p>
      <p className={reportTextClassName}><strong>후기 내용</strong> {report.reviewBody}</p>
      <Link className="mt-3 inline-block text-[13px] text-[#6156cc]" href={`/places/detail/?id=${report.placeId}`}>해당 장소 보기 ↗</Link>
      <div className="mt-[18px] flex justify-end gap-2.5">
        <button className={`${reviewActionClassName} bg-[#f0efff] text-[#5147d9]`} type="button" disabled={busyId !== null} onClick={() => void processReport(report, 'dismiss')}>신고 반려</button>
        <button className={`${reviewActionClassName} bg-[#5147ff] text-white`} type="button" disabled={busyId !== null} onClick={() => void processReport(report, 'hide')}>후기 숨김</button>
      </div>
    </li>)}</ul>
  </div>;
}
