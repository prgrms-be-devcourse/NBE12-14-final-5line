'use client';

import { useSearchParams } from 'next/navigation';
import { useState } from 'react';
import type { FormEvent } from 'react';
import { changePlaceSearchFilters, placeSearchFormId, readPlaceSearchParams } from './PlaceExplorer/placeSearchParams';

export default function PlaceSearchForm() {
  const { query } = readPlaceSearchParams(useSearchParams());
  return <SearchForm key={query} initialQuery={query} />;
}

/** 입력 중인 검색어는 폼에 두고 제출한 값만 URL에 반영합니다. */
function SearchForm({ initialQuery }: { initialQuery: string }) {
  const [query, setQuery] = useState(initialQuery);

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    changePlaceSearchFilters({ query: query.trim() });
  }

  return (
    <form
      id={placeSearchFormId}
      className="order-3 mx-auto flex w-full items-center gap-3 rounded-[28px] border border-transparent
        bg-[#f3f5f8] py-[5px] pr-2.5 pl-[18px] text-[#8191a7] focus-within:border-[#b8b4ff] md:order-none"
      onSubmit={submitSearch}
      role="search"
      onReset={event => {
        event.preventDefault();
        setQuery('');
      }}
    >
      <label htmlFor="place-search" className="sr-only">장소명 검색</label>
      <svg width="19" height="19" viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <circle cx="10.5" cy="10.5" r="6.5" stroke="currentColor" strokeWidth="1.6" />
        <path d="m16 16 4 4" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
      </svg>
      <input
        id="place-search"
        className="w-full min-w-0 bg-transparent py-1.5 text-[13px] text-[#16243b] outline-none focus-visible:outline-solid"
        value={query}
        onChange={event => setQuery(event.target.value)}
        placeholder="어떤 장소를 찾고 있나요?"
      />
      <button type="submit" className="shrink-0 cursor-pointer rounded-[20px] bg-white px-3.5 py-[7px] text-[12px] font-bold text-[#5147ff]">
        검색
      </button>
    </form>
  );
}
