'use client';

import Link from 'next/link';
import Image from 'next/image';
import { useEffect, useState } from 'react';
import { currentMember, type Member } from '@/domains/member/api/memberApi';
import { ApiError } from '@/shared/lib/http/apiError';
import { createReview, deleteReview, deleteReviewPhoto, listReviews, photoUrl, reportReview,
  updateReview, uploadReviewPhoto, type Review } from '../../api/reviewApi';

const actionButtonClassName = 'cursor-pointer rounded-[7px] border-0 px-3 py-2 disabled:cursor-default disabled:opacity-[.55]';
const reviewButtonClassName = `${actionButtonClassName} bg-[#f0efff] text-[#5147d9]`;
const photoAndReportButtonClassName = 'mt-[7px] cursor-pointer rounded-md border-0 bg-[#f0efff] px-[9px] py-[7px] text-[#5147d9]';
const actionsClassName = 'mt-2.5 flex items-center justify-end gap-2.5';
const emptyClassName = 'py-[18px] text-[13px] text-[#718096]';
const messageClassName = 'my-3 rounded-lg px-3 py-2.5 text-[13px]';

export default function PlaceReviews({ placeId }: { placeId: number }) {
  return <PlaceReviewsForPlace key={placeId} placeId={placeId} />;
}

function PlaceReviewsForPlace({ placeId }: { placeId: number }) {
  const [reviews, setReviews] = useState<Review[]>([]);
  const [member, setMember] = useState<Member | null>(null);
  const [loading, setLoading] = useState(true);
  const [memberCheckFailed, setMemberCheckFailed] = useState(false);
  const [body, setBody] = useState('');
  const [editingId, setEditingId] = useState<number | null>(null);
  const [confirmDeleteId, setConfirmDeleteId] = useState<number | null>(null);
  const [reportingId, setReportingId] = useState<number | null>(null);
  const [reportReason, setReportReason] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    Promise.allSettled([listReviews(placeId), currentMember()]).then(([reviewResult, memberResult]) => {
      if (!active) return;
      if (reviewResult.status === 'fulfilled') setReviews(reviewResult.value);
      else setError('후기를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.');
      if (memberResult.status === 'fulfilled') setMember(memberResult.value);
      else if (!(memberResult.reason instanceof ApiError && memberResult.reason.details.kind === 'http' && memberResult.reason.details.status === 401)) {
        setMemberCheckFailed(true);
      }
      setLoading(false);
    });
    return () => { active = false; };
  }, [placeId]);

  async function refresh() {
    setReviews(await listReviews(placeId));
  }

  async function save() {
    const text = body.trim();
    if (!text || text.length > 2000 || busy) return;
    setBusy(true);
    setError('');
    try {
      if (editingId === null) await createReview(placeId, text);
      else await updateReview(editingId, text);
      await refresh();
      setBody('');
      setEditingId(null);
    } catch {
      setError('후기를 저장하지 못했어요. 다시 시도해 주세요.');
    } finally { setBusy(false); }
  }

  async function remove(reviewId: number) {
    if (busy) return;
    setBusy(true);
    setError('');
    try {
      await deleteReview(reviewId);
      await refresh();
      setConfirmDeleteId(null);
      if (editingId === reviewId) { setEditingId(null); setBody(''); }
    } catch {
      setError('후기를 삭제하지 못했어요. 다시 시도해 주세요.');
    } finally { setBusy(false); }
  }

  async function addPhoto(reviewId: number, files: FileList | null) {
    if (!files?.length || busy) return;
    const file = files[0];
    if (!['image/jpeg', 'image/png'].includes(file.type) || file.size > 5 * 1024 * 1024) {
      setError('JPEG 또는 PNG 사진을 5MB 이내로 선택해 주세요.'); return;
    }
    setBusy(true); setError('');
    try { await uploadReviewPhoto(reviewId, file); await refresh(); }
    catch { setError('사진을 올리지 못했어요. 파일을 확인하고 다시 시도해 주세요.'); }
    finally { setBusy(false); }
  }

  async function removePhoto(reviewId: number, photoId: number) {
    if (busy) return;
    setBusy(true); setError('');
    try { await deleteReviewPhoto(reviewId, photoId); await refresh(); }
    catch { setError('사진을 삭제하지 못했어요. 다시 시도해 주세요.'); }
    finally { setBusy(false); }
  }

  async function sendReport(reviewId: number) {
    if (!reportReason.trim() || busy) return;
    setBusy(true); setError(''); setNotice('');
    try { await reportReview(reviewId, reportReason.trim()); setReportingId(null); setReportReason(''); setNotice('신고가 접수됐어요.'); }
    catch (cause) {
      setError(cause instanceof ApiError && cause.details.kind === 'http' && cause.details.status === 409
        ? '이미 신고한 후기예요.' : '신고를 접수하지 못했어요. 다시 시도해 주세요.');
    } finally { setBusy(false); }
  }

  return <section className="mt-6 border-t border-t-[#e9edf3] py-8 md:mt-[42px]" aria-labelledby="place-reviews-title">
    <div className="mb-[22px] flex items-end justify-between gap-4"><div><p className="mb-2 text-[10px] font-extrabold tracking-[1.5px] text-[#6156ff]">VISIT STORIES</p><h2 id="place-reviews-title" className="text-[21px]">이 장소의 후기</h2></div><span className="text-[13px] text-[#718096]">{reviews.length}개</span></div>
    {loading ? <p role="status">후기를 불러오고 있어요.</p> : <>
      {member ? <div className="rounded-[14px] border border-[#e4e7ef] bg-[#fafbff] p-[15px] md:p-5">
        <label className="mb-2.5 block text-[14px] font-bold" htmlFor="review-body">{editingId === null ? '방문 후기 남기기' : '후기 수정하기'}</label>
        <textarea className="w-full resize-y rounded-lg border border-[#d7deea] p-3 leading-[1.6] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#6156ff]" id="review-body" value={body} maxLength={2000} rows={4}
          placeholder="이 장소에서 경험한 내용을 적어 주세요." onChange={event => setBody(event.target.value)} />
        <div className={actionsClassName}><span className="text-[12px] text-[#8793a7]">{body.length}/2,000</span>
          {editingId !== null && <button className={reviewButtonClassName} type="button" onClick={() => { setEditingId(null); setBody(''); }}>취소</button>}
          <button className={`${actionButtonClassName} bg-[#5147ff] text-white`} type="button" disabled={busy || !body.trim()} onClick={() => void save()}>{busy ? '저장 중...' : editingId === null ? '후기 등록' : '수정 완료'}</button>
        </div>
      </div> : memberCheckFailed ? <p className={emptyClassName}>로그인 상태를 확인하지 못했어요. 연결 상태를 확인한 뒤 새로고침해 주세요.</p>
        : <p className={emptyClassName}>후기는 <Link className="font-bold text-[#5147d9]" href="/login/">로그인</Link> 후 작성할 수 있어요.</p>}
      {error && <p className={`${messageClassName} bg-[#fff0f1] text-[#ab303c]`} role="alert">{error}</p>}
      {notice && <p className={`${messageClassName} bg-[#ecf8f2] text-[#267454]`} role="status">{notice}</p>}
      {reviews.length ? <ul className="mt-[18px] list-none p-0">{reviews.map(review => <li key={review.id} className="border-b border-[#e9edf3] py-5">
        <div className="flex items-center justify-between gap-3 text-[13px]"><strong>{review.authorNickname}</strong><time className="text-[12px] text-[#8793a7]" dateTime={review.createdAt}>{review.createdAt.slice(0, 10)}</time></div>
        <p className="mt-3 text-sm/[1.7] wrap-anywhere whitespace-pre-wrap">{review.body}</p>
        {!!review.photos?.length && <div className="mt-3.5 flex flex-wrap gap-2.5">{review.photos.map(photo => <div key={photo.id} className="w-28">
          <a href={photoUrl(photo)} target="_blank" rel="noopener noreferrer" aria-label="후기 사진 크게 보기">
            <Image className="size-28 rounded-[9px] object-cover" unoptimized src={photoUrl(photo)} alt="방문 후기 사진" width={photo.width} height={photo.height} />
          </a>
          {member?.id === review.authorId && <button className={photoAndReportButtonClassName} type="button" disabled={busy}
            onClick={() => void removePhoto(review.id, photo.id)}>사진 삭제</button>}
        </div>)}</div>}
        {member?.id === review.authorId && <div className={`${actionsClassName} text-[12px]`}>
          <button className={reviewButtonClassName} type="button" onClick={() => { setEditingId(review.id); setBody(review.body); setConfirmDeleteId(null); }}>수정</button>
          {confirmDeleteId === review.id ? <><span>삭제할까요?</span><button className={reviewButtonClassName} type="button" disabled={busy} onClick={() => void remove(review.id)}>삭제 확인</button><button className={reviewButtonClassName} type="button" onClick={() => setConfirmDeleteId(null)}>취소</button></> :
            <button className={reviewButtonClassName} type="button" onClick={() => setConfirmDeleteId(review.id)}>삭제</button>}
        </div>}
        {member?.id === review.authorId && (review.photos?.length ?? 0) < 3 && <label className="mt-3 inline-block cursor-pointer rounded-[7px] bg-[#f0efff] px-2.5 py-2 text-[12px] text-[#5147d9]">
          사진 추가 (JPEG/PNG · 5MB 이하)
          <input className="mt-1.5 block max-w-[190px] text-[11px]" type="file" accept="image/jpeg,image/png" disabled={busy}
            onChange={event => { void addPhoto(review.id, event.target.files); event.target.value = ''; }} />
        </label>}
        {member && member.id !== review.authorId && <div className="mt-2.5 text-right text-[12px]">
          {reportingId === review.id ? <div><label className="block text-left font-bold" htmlFor={`report-${review.id}`}>신고 사유</label>
            <textarea className="mt-[7px] w-full resize-y rounded-[7px] border border-[#d7deea] p-[9px]" id={`report-${review.id}`} value={reportReason} maxLength={500} rows={2}
              onChange={event => setReportReason(event.target.value)} placeholder="문제가 되는 내용을 구체적으로 적어 주세요." />
            <button className={photoAndReportButtonClassName} type="button" disabled={busy || !reportReason.trim()} onClick={() => void sendReport(review.id)}>신고 접수</button>
            <button className={photoAndReportButtonClassName} type="button" onClick={() => { setReportingId(null); setReportReason(''); }}>취소</button></div>
            : <button className={photoAndReportButtonClassName} type="button" onClick={() => { setReportingId(review.id); setReportReason(''); }}>신고</button>}
        </div>}
      </li>)}</ul> : <p className={emptyClassName}>아직 등록된 후기가 없어요. 첫 후기를 남겨 주세요.</p>}
    </>}
  </section>;
}
