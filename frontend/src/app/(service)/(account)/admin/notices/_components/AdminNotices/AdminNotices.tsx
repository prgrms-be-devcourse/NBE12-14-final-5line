'use client';

import { useState } from 'react';
import type { FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ChevronLeft, ChevronRight, Search } from 'lucide-react';
import { changeNoticeVisibility } from '@/domains/admin/api/adminNoticeApi';
import { adminNoticeListQuery, noticePlaceSearchQuery, refreshNoticeQueries } from '@/domains/admin/queries/adminNoticeQueries';
import type { AdminNotice } from '@/domains/admin/types/adminNotice.type';
import type { PlaceSummary } from '@/domains/place/types/placeApi.type';
import { ApiError } from '@/shared/lib/http/apiError';
import NoticeForm from './NoticeForm';

const panel = 'rounded-2xl border border-slate-200 bg-white p-4 sm:p-6';
const secondaryButton = 'inline-flex items-center justify-center gap-1.5 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 disabled:cursor-not-allowed disabled:opacity-50';
const statusLabel = { DRAFT: '초안', PUBLISHED: '공개', HIDDEN: '숨김' } satisfies Record<AdminNotice['publishStatus'], string>;
const visibilityActionLabel = { DRAFT: '공개', PUBLISHED: '숨기기', HIDDEN: '재공개' } satisfies Record<AdminNotice['publishStatus'], string>;

type Editor = { kind: 'new' } | { kind: 'edit'; notice: AdminNotice };

function Pagination({ label, page, size, total, onPageChange }: {
  label: string;
  page: number;
  size: number;
  total: number;
  onPageChange: (page: number) => void;
}) {
  const pageCount = Math.ceil(total / size);
  if (pageCount < 2) return null;

  return (
    <nav aria-label={label} className="mt-4 flex flex-wrap items-center justify-between gap-2 text-sm text-slate-600">
      <button type="button" className={secondaryButton} disabled={page === 0} onClick={() => onPageChange(page - 1)}>
        <ChevronLeft size={16} aria-hidden /> 이전
      </button>
      <span>{page + 1} / {pageCount} 페이지</span>
      <button type="button" className={secondaryButton} disabled={page + 1 >= pageCount} onClick={() => onPageChange(page + 1)}>
        다음 <ChevronRight size={16} aria-hidden />
      </button>
    </nav>
  );
}

function NoticeWorkspace({ place }: { place: PlaceSummary }) {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [editor, setEditor] = useState<Editor | null>(null);
  const [feedback, setFeedback] = useState('');
  const list = useQuery(adminNoticeListQuery(place.id, page));
  const visibility = useMutation({
    mutationFn: ({ noticeId, visible }: { noticeId: number; visible: boolean }) =>
      changeNoticeVisibility({ placeId: place.id, noticeId, visible }),
    onSuccess: async (_, { visible }) => {
      await refreshNoticeQueries(queryClient, place.id, true);
      setFeedback(visible ? '공지를 공개했어요.' : '공지를 숨겼어요.');
    },
  });
  const actionDisabled = editor !== null || visibility.isPending;

  function openEditor(next: Editor) {
    setFeedback('');
    visibility.reset();
    setEditor(next);
  }

  function changeVisibility(notice: AdminNotice) {
    setFeedback('');
    visibility.mutate({ noticeId: notice.placeNoticeId, visible: notice.publishStatus !== 'PUBLISHED' });
  }

  return (
    <section aria-label="선택한 장소의 공지" className="mt-6">
      <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 className="text-xl font-bold break-words">{place.name || '이름 미확인'}</h2>
          {place.address ? <p className="mt-1 text-sm break-words text-slate-600">{place.address}</p> : null}
        </div>
        <button type="button" className={secondaryButton} disabled={actionDisabled} onClick={() => openEditor({ kind: 'new' })}>
          새 공지
        </button>
      </div>
      {feedback ? <p role="status" className="mb-4 text-sm font-semibold text-emerald-700">{feedback}</p> : null}
      {visibility.isError ? (
        <p role="alert" className="mb-4 text-sm text-red-700">
          {visibility.error instanceof ApiError ? visibility.error.message : '공지 공개 상태를 변경하지 못했어요.'}
        </p>
      ) : null}
      <div className={editor ? 'grid items-start gap-4 xl:grid-cols-2' : 'grid gap-4'}>
        <section aria-label="공지 목록" className={`${panel} min-w-0`}>
          <h3 className="text-base font-bold">공지 목록</h3>
          {list.isPending ? <p role="status" className="mt-3 text-sm text-slate-600">공지를 불러오고 있어요.</p> : null}
          {list.isError ? (
            <div className="mt-3 flex flex-wrap items-center gap-2">
              <p role="alert" className="text-sm text-red-700">공지 목록을 불러오지 못했어요.</p>
              <button type="button" className={secondaryButton} onClick={() => void list.refetch()}>다시 조회</button>
            </div>
          ) : null}
          {list.data ? (
            <>
              {list.data.items.length === 0 ? <p className="mt-3 text-sm text-slate-600">등록된 공지가 없어요.</p> : null}
              <ul className="divide-y divide-slate-200">
                {list.data.items.map(notice => (
                  <li key={notice.placeNoticeId} className="py-4">
                    <div className="flex flex-wrap items-center gap-2">
                      <h4 className="min-w-0 font-semibold break-words">{notice.title || '제목 없음'}</h4>
                      <span className="rounded-md bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-700">
                        {statusLabel[notice.publishStatus]}
                      </span>
                    </div>
                    {notice.content ? <p className="mt-2 text-sm whitespace-pre-wrap break-words text-slate-700">{notice.content}</p> : null}
                    <dl className="mt-3 space-y-1 text-sm break-words text-slate-600">
                      <div><dt className="inline font-semibold">유효기간 </dt><dd className="inline">
                        {notice.validFrom?.replace('T', ' ') ?? '시작 제한 없음'} ~ {notice.validUntil?.replace('T', ' ') ?? '종료 제한 없음'}
                      </dd></div>
                      <div><dt className="inline font-semibold">출처 </dt><dd className="inline">{notice.source.url || 'URL 미등록'}</dd></div>
                      <div><dt className="inline font-semibold">확인일 </dt><dd className="inline">{notice.source.verifiedAt?.slice(0, 10) ?? '미확인'}</dd></div>
                    </dl>
                    <div className="mt-3 flex flex-wrap gap-2">
                      <button
                        type="button"
                        aria-label={`${notice.title || '제목 없음'} 수정`}
                        className={secondaryButton}
                        disabled={actionDisabled}
                        onClick={() => openEditor({ kind: 'edit', notice })}
                      >
                        수정
                      </button>
                      <button
                        type="button"
                        aria-label={`${notice.title || '제목 없음'} ${visibilityActionLabel[notice.publishStatus]}`}
                        className={secondaryButton}
                        disabled={actionDisabled}
                        onClick={() => changeVisibility(notice)}
                      >
                        {visibility.isPending && visibility.variables.noticeId === notice.placeNoticeId
                          ? '변경 중…'
                          : visibilityActionLabel[notice.publishStatus]}
                      </button>
                    </div>
                  </li>
                ))}
              </ul>
              <Pagination label="공지 목록 페이지" page={page} size={list.data.size} total={list.data.totalElements} onPageChange={setPage} />
            </>
          ) : null}
        </section>
        {editor ? (
          <NoticeForm
            placeId={place.id}
            notice={editor.kind === 'edit' ? editor.notice : undefined}
            onCancel={() => setEditor(null)}
            onSaved={published => {
              if (editor.kind === 'new') setPage(0);
              setEditor(null);
              setFeedback(published ? '공지를 공개했어요.'
                : editor.kind === 'new' || editor.notice.publishStatus === 'DRAFT'
                  ? '공지 초안을 저장했어요.'
                  : '공지 변경사항을 저장했어요.');
            }}
          />
        ) : null}
      </div>
    </section>
  );
}

export default function AdminNotices() {
  const [keyword, setKeyword] = useState('');
  const [search, setSearch] = useState({ query: '', page: 0 });
  const [selectedPlace, setSelectedPlace] = useState<PlaceSummary | null>(null);
  const places = useQuery(noticePlaceSearchQuery(search.query, search.page));

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSearch({ query: keyword.trim(), page: 0 });
  }

  return (
    <div>
      <h1 className="mb-5 text-3xl font-extrabold tracking-tight">공지 관리</h1>
      <section className={panel} aria-labelledby="notice-place-heading">
        <h2 id="notice-place-heading" className="text-base font-bold">장소 선택</h2>
        <form onSubmit={submitSearch} className="mt-3 flex flex-wrap items-end gap-2">
          <label className="flex min-w-0 flex-1 basis-44 flex-col gap-1 text-sm font-medium">
            장소명
            <input
              type="search"
              maxLength={100}
              value={keyword}
              onChange={event => setKeyword(event.target.value)}
              className="w-full rounded-lg border border-slate-300 px-3 py-2 focus-visible:outline-2 focus-visible:outline-indigo-600"
            />
          </label>
          <button type="submit" className={secondaryButton}><Search size={16} aria-hidden /> 검색</button>
        </form>
        {places.isPending ? <p role="status" className="mt-4 text-sm text-slate-600">장소를 검색하고 있어요.</p> : null}
        {places.isError ? (
          <div className="mt-4 flex flex-wrap items-center gap-2">
            <p role="alert" className="text-sm text-red-700">장소를 불러오지 못했어요.</p>
            <button type="button" className={secondaryButton} onClick={() => void places.refetch()}>다시 검색</button>
          </div>
        ) : null}
        {places.data ? (
          <>
            {places.data.items.length === 0 ? <p className="mt-4 text-sm text-slate-600">검색 결과가 없어요.</p> : null}
            <ul className="mt-4 grid gap-2 sm:grid-cols-2">
              {places.data.items.map(place => (
                <li key={place.id} className="min-w-0">
                  <button
                    type="button"
                    aria-pressed={selectedPlace?.id === place.id}
                    onClick={() => setSelectedPlace(place)}
                    className="flex h-full w-full flex-col gap-1 rounded-lg border border-slate-200 p-3 text-left text-sm break-words hover:bg-slate-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 aria-pressed:border-indigo-500 aria-pressed:bg-indigo-50"
                  >
                    <span className="max-w-full font-semibold">{place.name || '이름 미확인'}</span>
                    <span className="max-w-full text-xs text-slate-600">{place.address || `장소 ID ${place.id}`}</span>
                  </button>
                </li>
              ))}
            </ul>
            <Pagination label="장소 검색 결과 페이지" page={search.page} size={places.data.size} total={places.data.totalElements}
              onPageChange={page => setSearch(current => ({ ...current, page }))} />
          </>
        ) : null}
      </section>
      {selectedPlace ? <NoticeWorkspace key={selectedPlace.id} place={selectedPlace} /> : null}
    </div>
  );
}
