'use client';

import Link from 'next/link';
import { useState } from 'react';
import { useExampleQuery } from '@/domains/example/hooks/useExampleQuery';
import { ApiError } from '@/shared/lib/http/apiError';

/** 일반 레이아웃의 페이지 전용 본문입니다. 도메인 조회 Hook으로 로딩·성공·오류와 수동 재조회를 보여 줍니다. */
export default function MainExampleContent() {
  const [id, setId] = useState(1);
  const query = useExampleQuery(id);
  const errorMessage = query.error instanceof ApiError
    ? query.error.message
    : '예시를 조회하지 못했습니다.';

  return (
    <section className="max-w-3xl rounded-2xl border border-slate-200 bg-white p-6 md:p-8">
      <h1 className="text-3xl font-bold">일반 레이아웃 예시</h1>
      <p className="mt-4 text-slate-700">일반 헤더와 사이드바가 페이지 본문을 감쌉니다.</p>
      <p className="mt-3 text-slate-600">이 컴포넌트는 이 페이지에서만 사용하므로 경로 안의 _components에 둡니다.</p>
      <div className="mt-6 space-y-4 border-t border-slate-200 pt-6">
        <h2 className="text-xl font-semibold">API 조회 예시</h2>
        <label className="flex flex-wrap items-center gap-3">
          조회할 예시
          <select
            className="rounded border border-slate-300 bg-white p-2"
            value={id}
            onChange={event => setId(Number(event.target.value))}
          >
            <option value={1}>ID 1 — 성공 예시</option>
            <option value={2}>ID 2 — 업무 오류 예시</option>
          </select>
        </label>
        {query.isPending && <p role="status">예시를 불러오는 중입니다.</p>}
        {query.isError && <p role="alert" className="text-red-700">{errorMessage}</p>}
        {query.data && (
          <dl className="space-y-2" aria-label="조회 결과">
            <div><dt className="font-semibold">이름</dt><dd>{query.data.name}</dd></div>
            <div><dt className="font-semibold">참여 인원</dt><dd>{query.data.participantCount}명</dd></div>
          </dl>
        )}
        {query.isFetching && !query.isPending && <p role="status">예시를 다시 조회하는 중입니다.</p>}
        <button
          type="button"
          className="rounded bg-blue-700 px-4 py-2 text-white disabled:opacity-50"
          disabled={query.isFetching}
          onClick={() => { void query.refetch(); }}
        >
          다시 조회
        </button>
      </div>
      <Link className="mt-6 inline-block rounded px-2 py-1 text-blue-700 underline" href="/">
        예시 목록으로 돌아가기
      </Link>
    </section>
  );
}
