'use client';

import Link from 'next/link';
import Image from 'next/image';
import { useEffect, useState } from 'react';
import { currentMember, type Member } from '@/domains/member/api/memberApi';
import { ApiError } from '@/shared/lib/http/apiError';
import { createReview, deleteReview, deleteReviewPhoto, listReviews, photoUrl, reportReview,
  updateReview, uploadReviewPhoto, type Review } from '../api/reviewApi';
import './PlaceReviews.css';

export default function PlaceReviews({ placeId }: { placeId: number }) {
  return <PlaceReviewsForPlace key={placeId} placeId={placeId} />;
}

function PlaceReviewsForPlace({ placeId }: { placeId: number }) {
  const [reviews, setReviews] = useState<Review[]>([]);
  const [member, setMember] = useState<Member | null>(null);
  const [loading, setLoading] = useState(true);
  const [memberCheckFailed, setMemberCheckFailed] = useState(false);
  const [body, setBody] = useState('');
  const [draftPhotos, setDraftPhotos] = useState<File[]>([]);
  const [createdReviewId, setCreatedReviewId] = useState<number | null>(null);
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
    if ((!text && createdReviewId === null) || text.length > 2000 || busy) return;
    setBusy(true);
    setError('');
    let savedReviewId = createdReviewId;
    try {
      if (editingId !== null) {
        await updateReview(editingId, text);
      } else {
        const reviewId = createdReviewId ?? (await createReview(placeId, text)).id;
        savedReviewId = reviewId;
        setCreatedReviewId(reviewId);
        for (const file of draftPhotos) {
          await uploadReviewPhoto(reviewId, file);
          setDraftPhotos(current => current.filter(item => item !== file));
        }
      }
      await refresh();
      setBody('');
      setEditingId(null);
      setCreatedReviewId(null);
    } catch {
      setError(savedReviewId !== null && editingId === null
        ? '후기는 저장됐어요. 남은 사진은 다시 눌러 업로드해 주세요.'
        : '후기를 저장하지 못했어요. 다시 시도해 주세요.');
      void refresh().catch(() => {});
    } finally { setBusy(false); }
  }

  function selectDraftPhotos(files: FileList | null) {
    if (!files?.length) return;
    const next = Array.from(files);
    if (draftPhotos.length + next.length > 3 || next.some(file =>
      !['image/jpeg', 'image/png'].includes(file.type) || file.size > 5 * 1024 * 1024)) {
      setError('사진은 JPEG/PNG 파일로 최대 3장, 각 5MB 이내로 선택해 주세요.');
      return;
    }
    setError('');
    setDraftPhotos(current => [...current, ...next]);
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

  return <section className="outpick-reviews" aria-labelledby="place-reviews-title">
    <div className="outpick-reviewsHeading"><div><p>VISIT STORIES</p><h2 id="place-reviews-title">이 장소의 후기</h2></div><span>{reviews.length}개</span></div>
    {loading ? <p role="status">후기를 불러오고 있어요.</p> : <>
      {member ? <div className="outpick-reviewEditor">
        <label htmlFor="review-body">{editingId === null ? '방문 후기 남기기' : '후기 수정하기'}</label>
        <textarea id="review-body" value={body} maxLength={2000} rows={4}
          disabled={createdReviewId !== null}
          placeholder="이 장소에서 경험한 내용을 적어 주세요." onChange={event => setBody(event.target.value)} />
        {editingId === null && <div className="outpick-reviewDraftPhotos">
          <label htmlFor="review-draft-photos">사진 추가 (최대 3장 · JPEG/PNG · 각 5MB 이하)</label>
          <input id="review-draft-photos" type="file" accept="image/jpeg,image/png" multiple
            disabled={busy || createdReviewId !== null || draftPhotos.length >= 3}
            onChange={event => { selectDraftPhotos(event.target.files); event.target.value = ''; }} />
          {draftPhotos.length > 0 && <ul>{draftPhotos.map((file, index) => <li key={`${file.name}-${index}`}>
            <span>{file.name}</span>
            <button type="button" disabled={busy || createdReviewId !== null}
              onClick={() => setDraftPhotos(current => current.filter((_, position) => position !== index))}>제외</button>
          </li>)}</ul>}
        </div>}
        <div className="outpick-reviewEditorActions"><span>{body.length}/2,000</span>
          {editingId !== null && <button type="button" onClick={() => { setEditingId(null); setBody(''); }}>취소</button>}
          <button type="button" disabled={busy || (!body.trim() && createdReviewId === null)} onClick={() => void save()}>{busy ? '저장 중...' : createdReviewId !== null ? '사진 업로드 다시 시도' : editingId === null ? '후기 등록' : '수정 완료'}</button>
        </div>
      </div> : memberCheckFailed ? <p className="outpick-reviewLogin">로그인 상태를 확인하지 못했어요. 연결 상태를 확인한 뒤 새로고침해 주세요.</p>
        : <p className="outpick-reviewLogin">후기는 <Link href="/login/">로그인</Link> 후 작성할 수 있어요.</p>}
      {error && <p className="outpick-reviewError" role="alert">{error}</p>}
      {notice && <p className="outpick-reviewNotice" role="status">{notice}</p>}
      {reviews.length ? <ul className="outpick-reviewList">{reviews.map(review => <li key={review.id}>
        <div className="outpick-reviewMeta"><strong>{review.authorNickname}</strong><time dateTime={review.createdAt}>{review.createdAt.slice(0, 10)}</time></div>
        <p>{review.body}</p>
        {!!review.photos?.length && <div className="outpick-reviewPhotos">{review.photos.map(photo => <div key={photo.id}>
          <a href={photoUrl(photo)} target="_blank" rel="noopener noreferrer" aria-label="후기 사진 크게 보기">
            <Image unoptimized src={photoUrl(photo)} alt="방문 후기 사진" width={photo.width} height={photo.height} />
          </a>
          {member?.id === review.authorId && <button type="button" disabled={busy}
            onClick={() => void removePhoto(review.id, photo.id)}>사진 삭제</button>}
        </div>)}</div>}
        {member?.id === review.authorId && <div className="outpick-reviewActions">
          <button type="button" onClick={() => { setEditingId(review.id); setBody(review.body); setConfirmDeleteId(null); }}>수정</button>
          {confirmDeleteId === review.id ? <><span>삭제할까요?</span><button type="button" disabled={busy} onClick={() => void remove(review.id)}>삭제 확인</button><button type="button" onClick={() => setConfirmDeleteId(null)}>취소</button></> :
            <button type="button" onClick={() => setConfirmDeleteId(review.id)}>삭제</button>}
        </div>}
        {member?.id === review.authorId && (review.photos?.length ?? 0) < 3 && <label className="outpick-reviewPhotoAdd">
          사진 추가 (JPEG/PNG · 5MB 이하)
          <input type="file" accept="image/jpeg,image/png" disabled={busy}
            onChange={event => { void addPhoto(review.id, event.target.files); event.target.value = ''; }} />
        </label>}
        {member && member.id !== review.authorId && <div className="outpick-reviewReport">
          {reportingId === review.id ? <div><label htmlFor={`report-${review.id}`}>신고 사유</label>
            <textarea id={`report-${review.id}`} value={reportReason} maxLength={500} rows={2}
              onChange={event => setReportReason(event.target.value)} placeholder="문제가 되는 내용을 구체적으로 적어 주세요." />
            <button type="button" disabled={busy || !reportReason.trim()} onClick={() => void sendReport(review.id)}>신고 접수</button>
            <button type="button" onClick={() => { setReportingId(null); setReportReason(''); }}>취소</button></div>
            : <button type="button" onClick={() => { setReportingId(review.id); setReportReason(''); }}>신고</button>}
        </div>}
      </li>)}</ul> : <p className="outpick-reviewEmpty">아직 등록된 후기가 없어요. 첫 후기를 남겨 주세요.</p>}
    </>}
  </section>;
}
