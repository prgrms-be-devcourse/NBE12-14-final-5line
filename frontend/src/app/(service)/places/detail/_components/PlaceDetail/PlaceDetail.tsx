'use client';

import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import PlacePhoto, { PlacePhotoCredit, getPlacePhoto } from '@/domains/place/components/PlacePhoto/PlacePhoto';
import CopyPlaceLinkButton from '@/domains/place/components/CopyPlaceLinkButton/CopyPlaceLinkButton';
import { usePlaceDetail } from '@/domains/place/hooks/usePlaces';
import { environmentLabel, seasonLabel } from '@/domains/place/api/placeApi';
import { safeHttpUrl } from '@/domains/place/lib/placeLinks';
import { ApiError } from '@/shared/lib/http/apiError';
import { getListReturnUrl } from '@/domains/place/lib/placeNavigation';
import PlaceReviews from '@/domains/review/components/PlaceReviews/PlaceReviews';
import FavoritePlaceButton from '../FavoritePlaceButton';
import OperatingInfoPanel, { placeFactsClassName } from '../OperatingInfoPanel/OperatingInfoPanel';

const eyebrowClassName = 'text-[10px] font-extrabold tracking-[1.7px] text-[#6156ff]';
const actionClassName = 'mt-5 block rounded-lg bg-[#5147ff] p-3 text-center text-[12px] text-white no-underline';
const emptyClassName = 'py-[70px] text-center md:px-5 md:py-[100px]';
const emptyTitleClassName = 'mt-5 text-[26px] font-bold';
const emptyTextClassName = 'mt-4 text-[13px] text-[#718096]';
const sectionClassName = 'border-t border-[#e9edf3] py-7 [&>p]:mt-2.5 [&>p]:text-[13px]/[1.9] [&>p]:text-[#718096]';
const sectionHeadingClassName = 'mb-[18px] text-[18px] font-bold';

export default function PlaceDetail() {
  const params = useSearchParams();
  const ids = params.getAll('id');
  const id = ids.length === 1 && /^[1-9]\d*$/.test(ids[0]) ? Number(ids[0]) : NaN;
  const validId = Number.isSafeInteger(id) && id > 0;
  const detail = usePlaceDetail(id);
  const place = detail.data;
  const failure = detail.error instanceof ApiError ? detail.error.details : null;
  const missing = !validId || failure?.kind === 'http' && failure.status === 404;
  const homepage = safeHttpUrl(place?.homepage ?? null);
  const returnUrl = getListReturnUrl(params.get('returnTo'));

  return (
    <div className="[&_a:focus-visible]:outline-3 [&_a:focus-visible]:outline-offset-4 [&_a:focus-visible]:outline-[#5147ff]">
      <main className="m-auto max-w-[1160px] p-5 md:px-9 md:py-7">
        <Link className="inline-block py-2 text-[13px] text-[#6156cc] no-underline" href={returnUrl} scroll={false}>← 장소 목록으로 돌아가기</Link>
        {missing ? (
          <section className={emptyClassName}>
            <p className={eyebrowClassName}>PLACE NOT FOUND</p>
            <h1 className={emptyTitleClassName}>장소를 찾을 수 없어요</h1>
            <p className={emptyTextClassName}>장소 주소를 확인하거나 목록에서 다른 장소를 찾아보세요.</p>
          </section>
        ) : detail.isError ? (
          <section className={emptyClassName} role="alert"><h1 className={emptyTitleClassName}>장소 정보를 불러오지 못했어요</h1><p className={emptyTextClassName}>서버 연결을 확인하고 다시 시도해 주세요.</p><button type="button" className={actionClassName} onClick={() => void detail.refetch()}>다시 시도</button></section>
        ) : !place ? <p role="status">장소 정보를 불러오고 있어요.</p> : (
          <>
            <p className="mt-5 rounded-lg bg-[#f5f4ff] px-4 py-3 text-xs/[1.8] text-[#716a9b]">{place.name?.startsWith('[데모]') ? '개발용 가상 장소 · 실제 시설·주소·운영 정보가 아닙니다.' : '방문 전 공식 안내를 확인하세요. 미확인 항목은 미확인으로 표시합니다.'}</p>
            <div className="mt-7 mb-8 grid grid-cols-1 items-center gap-[26px] md:mb-10 md:grid-cols-2 md:gap-9">
              <div>
                <p className={eyebrowClassName}>EXPLORE THE OUTSIDE</p>
                <p className="mt-4 text-[13px] text-[#7c899d] md:mt-6">{place.address ?? '주소 미확인'}</p>
                <h1 className="mt-2.5 text-[30px] font-extrabold tracking-[-1.5px] md:text-[clamp(28px,3vw,42px)]">{place.name ?? '이름 미확인'}</h1>
                <p className="mt-4 text-sm/[1.8] text-[#718096]">{place.description ?? '등록된 소개가 없습니다.'}</p>
                <div className="mt-6 flex flex-wrap gap-[7px]">{place.activities.map(activity => <span key={activity.placeActivityId} className="rounded-md bg-[#f3f2ff] px-2.5 py-1.5 text-[12px] text-[#6156cc]">{activity.activityTypeName}</span>)}</div>
                <div className="mt-5 flex flex-wrap items-start gap-2">
                  <FavoritePlaceButton placeId={place.id} />
                  <CopyPlaceLinkButton label="장소 링크 복사" path={`/places/detail/?id=${place.id}`} />
                </div>
              </div>
              <div className="row-start-1 min-w-0 [&>div]:rounded-[18px] md:row-start-auto"><PlacePhoto place={place} /><PlacePhotoCredit place={place} /></div>
            </div>

            <div className="grid grid-cols-1 items-start gap-6 md:grid-cols-[minmax(0,1fr)_minmax(250px,.55fr)] md:gap-[60px]">
              <div>
                <section className={sectionClassName} aria-labelledby="place-introduction">
                  <h2 className={sectionHeadingClassName} id="place-introduction">장소 소개</h2>
                  <p>{place.description ?? '등록된 소개가 없습니다.'}</p>
                </section>
                <section className={sectionClassName} aria-labelledby="place-activities">
                  <h2 className={sectionHeadingClassName} id="place-activities">즐길 수 있는 활동</h2>
                  <ul className="list-none p-0">{place.activities.map(activity => (
                    <li key={activity.placeActivityId} className="mt-2.5 rounded-[10px] border border-[#e9edf3] px-[18px] py-[15px]">
                      <h3 className="text-[14px] font-bold">{activity.activityTypeName}</h3>
                      <p className="mt-[7px] text-xs/[1.7] text-[#718096]">{environmentLabel(activity.environmentType)} · {activity.seasons.length ? activity.seasons.map(seasonLabel).join(' · ') : '시즌 미확인'}</p>
                    </li>
                  ))}</ul>
                  {place.activities.length === 0 && <p>등록된 활동 정보가 없습니다.</p>}
                  <p>시즌은 활동을 찾기 위한 분류입니다. 현재 운영 여부와 방문 가능한 날짜는 공식 안내를 확인하세요.</p>
                </section>
                <section className={sectionClassName} aria-labelledby="place-operating-info">
                  <h2 className={sectionHeadingClassName} id="place-operating-info">운영 정보</h2>
                  <OperatingInfoPanel info={place.operatingInfo} />
                </section>
                <section className={sectionClassName} aria-labelledby="place-notices">
                  <h2 className={sectionHeadingClassName} id="place-notices">운영 공지</h2>
                  {place.notices.length ? place.notices.map(notice => <article className="mt-3 rounded-lg border border-[#e9edf3] p-4 text-[13px]" key={notice.placeNoticeId}><h3 className="font-bold">{notice.title ?? '공지'}</h3><p className="mt-2 leading-[1.8] whitespace-pre-wrap text-[#718096]">{notice.content}</p></article>) : <p>등록된 운영 공지가 없습니다.</p>}
                </section>
                <section className={sectionClassName} aria-labelledby="place-sources">
                  <h2 className={sectionHeadingClassName} id="place-sources">정보 출처 및 확인일</h2>
                  {place.sources.length ? place.sources.map(source => <dl className={placeFactsClassName} key={source.sourceRecordId}>
                    <div><dt>출처</dt><dd>{safeHttpUrl(source.url) ? <a href={safeHttpUrl(source.url)!} target="_blank" rel="noopener noreferrer">출처 안내 열기 ↗</a> : '출처 링크 미등록'}</dd></div>
                    <div><dt>확인 항목</dt><dd>{source.verifiedFields ?? '미등록'}</dd></div>
                    <div><dt>확인일</dt><dd>{source.verifiedAt.replace('T', ' ')}</dd></div>
                  </dl>) : <p>확인된 출처가 없습니다.</p>}
                  <p>{getPlacePhoto(place) ? '시설 사진의 출처와 이용조건은 사진 아래에서 확인할 수 있습니다.' : '사진이 없거나 불러오지 못하면 시설 사진이 아닌 안내용 일러스트를 표시합니다.'}</p>
                </section>
              </div>
              <aside className="rounded-[14px] border border-[#e9edf3] bg-[#fafbfe] p-[25px] md:sticky md:top-[calc(var(--service-header-height)+24px)]" aria-labelledby="visit-information">
                <h2 className={sectionHeadingClassName} id="visit-information">방문 전 확인하세요</h2>
                <dl className={placeFactsClassName}>
                  <div><dt>상세 주소</dt><dd>{place.address ?? '미확인'}</dd></div>
                  <div><dt>현재 운영 여부</dt><dd>{place.operationStatus ?? '미확인'}</dd></div>
                  <div><dt>공식 홈페이지</dt><dd>{homepage ? <a href={homepage} target="_blank" rel="noopener noreferrer">공식 안내 열기 ↗</a> : '미등록'}</dd></div>
                </dl>
                {place.latitude !== null && place.longitude !== null && <a className={actionClassName} target="_blank" rel="noopener noreferrer" href={`https://map.kakao.com/link/to/${encodeURIComponent(place.name ?? '장소')},${place.latitude},${place.longitude}`}>카카오맵 길찾기 ↗</a>}
                <p className="mt-[22px] text-[11px]/[1.8] text-[#8793a7]">{place.name?.startsWith('[데모]') ? '방문 전 공식 안내를 확인하세요. [데모] 장소의 좌표와 주소는 실제 시설 위치가 아닙니다.' : '방문 전 운영시간과 이용 조건을 공식 안내에서 확인하세요.'}</p>
                <Link href={returnUrl} scroll={false} className={actionClassName}>다른 장소 살펴보기</Link>
              </aside>
            </div>
            <PlaceReviews placeId={place.id} />
          </>
        )}
        <footer className="mt-10 border-t border-[#e9edf3] py-6 text-[11px] text-[#8793a7]">OUTPICK · 밖으로, 더 멀리</footer>
      </main>
    </div>
  );
}
