import { useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createNotice, updateNotice } from '@/domains/admin/api/adminNoticeApi';
import { refreshNoticeQueries } from '@/domains/admin/queries/adminNoticeQueries';
import type { AdminNotice, AdminNoticeDraftInput } from '@/domains/admin/types/adminNotice.type';
import { usePlaceDetail } from '@/domains/place/hooks/usePlaces';
import { ApiError } from '@/shared/lib/http/apiError';

const label = 'flex min-w-0 flex-col gap-1.5 text-sm font-semibold text-slate-700';
const field = 'w-full min-w-0 rounded-lg border border-slate-300 bg-white px-3 py-2 font-normal text-slate-900 focus-visible:outline-2 focus-visible:outline-indigo-600';
const datetimeLimits = { step: 1, min: '1000-01-01T00:00', max: '9999-12-31T23:59:59' };

function localDateTime(value: string) {
  if (!value) return null;
  return value.length === 16 ? `${value}:00` : value;
}

function focusEditorHeading(heading: HTMLHeadingElement | null) {
  heading?.focus();
}

type Props = {
  placeId: number;
  notice?: AdminNotice;
  onCancel: () => void;
  onSaved: (published: boolean) => void;
};

export default function NoticeForm({ placeId, notice, onCancel, onSaved }: Props) {
  const queryClient = useQueryClient();
  const placeDetail = usePlaceDetail(placeId);
  const [title, setTitle] = useState(notice?.title ?? '');
  const [content, setContent] = useState(notice?.content ?? '');
  const [validFrom, setValidFrom] = useState(notice?.validFrom ?? '');
  const [validUntil, setValidUntil] = useState(notice?.validUntil ?? '');
  const [sourceMode, setSourceMode] = useState<'existing' | 'new'>(notice ? 'existing' : 'new');
  const [sourceId, setSourceId] = useState(notice ? String(notice.source.sourceRecordId) : '');
  const [sourceUrl, setSourceUrl] = useState('');
  const [verifiedDate, setVerifiedDate] = useState('');
  const [formError, setFormError] = useState('');
  const publicationRequestId = useRef<string | null>(null);

  const currentSource = notice?.source;
  const publicSources = placeDetail.data?.sources ?? [];
  const availableSources = currentSource
    ? [currentSource, ...publicSources.filter(source => source.sourceRecordId !== currentSource.sourceRecordId)]
    : publicSources;
  const selectedSource = availableSources.find(source => String(source.sourceRecordId) === sourceId);
  const editingDraft = notice?.publishStatus === 'DRAFT';
  const save = useMutation({
    mutationFn: ({ input, publish }: { input: AdminNoticeDraftInput; publish: boolean }) => {
      if (notice) {
        return updateNotice({ placeId, noticeId: notice.placeNoticeId, input, publish });
      }
      if (publish) {
        publicationRequestId.current ??= crypto.randomUUID();
        return createNotice({ placeId, input, publicationRequestId: publicationRequestId.current });
      }
      return createNotice({ placeId, input });
    },
    onSuccess: async (_, { publish }) => {
      await refreshNoticeQueries(queryClient, placeId, publish || notice?.publishStatus === 'PUBLISHED');
      onSaved(publish);
    },
  });

  const responseError = save.error instanceof ApiError ? save.error.details : null;
  const responseFields = responseError?.kind === 'http' ? responseError.fieldErrors : [];
  const errorMessage = formError || (save.isError ? responseError?.message ?? '공지를 저장하지 못했어요.' : '');

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (save.isPending) return;
    const submitter = event.nativeEvent instanceof SubmitEvent ? event.nativeEvent.submitter : null;
    const publish = submitter instanceof HTMLButtonElement && submitter.value === 'publish';
    save.reset();
    setFormError('');

    const input = {
      title: title.trim(),
      content: content.trim(),
      validFrom: localDateTime(validFrom),
      validUntil: localDateTime(validUntil),
    };
    if (!input.title || !input.content) {
      setFormError('제목과 본문을 입력해 주세요.');
      return;
    }
    if (input.validFrom && input.validUntil && input.validFrom >= input.validUntil) {
      setFormError('종료 시각은 시작 시각보다 늦어야 해요.');
      return;
    }
    if (sourceMode === 'existing') {
      if (!selectedSource) {
        setFormError('공지에 연결할 출처를 선택해 주세요.');
        return;
      }
      save.mutate({ input: { ...input, sourceRecordId: selectedSource.sourceRecordId }, publish });
      return;
    }

    const url = sourceUrl.trim();
    if (!URL.canParse(url) || !/^https?:\/\//i.test(url) || !verifiedDate) {
      setFormError('HTTP 또는 HTTPS 주소와 확인일을 입력해 주세요.');
      return;
    }
    save.mutate({ input: { ...input, source: { url, verifiedAt: `${verifiedDate}T00:00:00` } }, publish });
  }

  return (
    <form
      aria-labelledby="notice-editor-heading"
      onSubmit={submit}
      className="min-w-0 rounded-2xl border border-slate-200 bg-white p-4 sm:p-6"
    >
      <h3 id="notice-editor-heading" ref={focusEditorHeading} tabIndex={-1} className="scroll-mt-28 text-base font-bold">
        {notice ? editingDraft ? '공지 초안 수정' : '공지 수정' : '새 공지 작성'}
      </h3>
      <fieldset disabled={save.isPending} className="mt-4 min-w-0 space-y-4">
        <label className={label}>
          제목
          <input className={field} required maxLength={50} value={title} onChange={event => setTitle(event.target.value)} />
        </label>
        <label className={label}>
          본문
          <textarea className={field} required rows={5} value={content} onChange={event => setContent(event.target.value)} />
        </label>
        <fieldset className="min-w-0 space-y-2">
          <legend className="text-sm font-semibold text-slate-700">유효기간 (선택, 한국 시간)</legend>
          <label className={label}>
            시작 시각
            <input type="datetime-local" className={field} {...datetimeLimits} value={validFrom} onChange={event => setValidFrom(event.target.value)} />
          </label>
          <label className={label}>
            종료 시각
            <input type="datetime-local" className={field} {...datetimeLimits} value={validUntil} onChange={event => setValidUntil(event.target.value)} />
          </label>
        </fieldset>
        <fieldset className="min-w-0 space-y-3 border-t border-slate-200 pt-3">
          <legend className="pr-2 text-sm font-semibold text-slate-700">출처</legend>
          <div className="flex flex-wrap gap-x-4 gap-y-2 text-sm">
            <label className="flex items-center gap-2">
              <input type="radio" name="notice-source" checked={sourceMode === 'existing'} onChange={() => setSourceMode('existing')} />
              기존 출처
            </label>
            <label className="flex items-center gap-2">
              <input type="radio" name="notice-source" checked={sourceMode === 'new'} onChange={() => setSourceMode('new')} />
              새 출처
            </label>
          </div>
          {sourceMode === 'existing' ? (
            <>
              {placeDetail.isPending ? <p role="status" className="text-sm text-slate-600">장소 출처를 불러오고 있어요.</p> : null}
              {placeDetail.isError ? (
                <p role="alert" className="text-sm text-red-700">
                  장소 출처를 불러오지 못했어요.{' '}
                  <button type="button" className="font-semibold underline" onClick={() => void placeDetail.refetch()}>다시 조회</button>
                </p>
              ) : null}
              {availableSources.length > 0 ? (
                <>
                  <label className={label}>
                    연결할 출처
                    <select className={field} required value={sourceId} onChange={event => setSourceId(event.target.value)}>
                      <option value="">선택해 주세요</option>
                      {availableSources.map(source => (
                        <option value={source.sourceRecordId} key={source.sourceRecordId}>
                          {source.url || `출처 ${source.sourceRecordId}`} · {source.verifiedAt?.slice(0, 10) ?? '미확인'}
                        </option>
                      ))}
                    </select>
                  </label>
                  {selectedSource ? <p className="text-sm text-slate-600">확인일 {selectedSource.verifiedAt?.slice(0, 10) ?? '미확인'}</p> : null}
                </>
              ) : null}
              {placeDetail.isSuccess && availableSources.length === 0 ? (
                <p className="text-sm text-slate-600">선택할 기존 출처가 없어요. 새 출처를 입력해 주세요.</p>
              ) : null}
            </>
          ) : (
            <>
              <label className={label}>
                공식 출처 URL
                <input type="url" className={field} required maxLength={100} value={sourceUrl} onChange={event => setSourceUrl(event.target.value)} />
              </label>
              <label className={label}>
                확인일
                <input type="date" className={field} required min="1000-01-01" max="9999-12-31"
                  value={verifiedDate} onChange={event => setVerifiedDate(event.target.value)} />
              </label>
            </>
          )}
        </fieldset>
        {errorMessage ? (
          <div role="alert" className="text-sm text-red-700">
            <p>{errorMessage}</p>
            {responseFields.length > 0 ? (
              <ul className="mt-1 list-disc pl-5">
                {responseFields.map(({ field, message }, index) => <li key={`${field}:${index}`}>{message}</li>)}
              </ul>
            ) : null}
          </div>
        ) : null}
        <div className="flex flex-wrap gap-2">
          <button type="submit" disabled={sourceMode === 'existing' && !selectedSource}
            className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 disabled:cursor-not-allowed disabled:opacity-50">
            {save.isPending ? '저장 중…' : !notice || editingDraft ? '초안 저장' : '변경사항 저장'}
          </button>
          {!notice || editingDraft ? (
            <button
              type="submit"
              value="publish"
              disabled={sourceMode === 'existing' && !selectedSource?.verifiedAt}
              className="rounded-lg border border-indigo-600 px-4 py-2 text-sm font-semibold text-indigo-700 hover:bg-indigo-50 focus-visible:outline-2 focus-visible:outline-indigo-600 disabled:cursor-not-allowed disabled:opacity-50"
            >
              저장 후 공개
            </button>
          ) : null}
          <button type="button" onClick={onCancel}
            className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50">
            취소
          </button>
        </div>
      </fieldset>
    </form>
  );
}
