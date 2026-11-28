'use client';

import Link from 'next/link';
import KakaoPlaceMap from '@/domains/place/components/KakaoPlaceMap/KakaoPlaceMap';
import PlaceMedia from '../PlaceMedia';
import PlaceDetailNavigation from '../PlaceDetailNavigation';
import MarineCommonNotice from '../MarineCommonNotice';
import ResearchEvidencePanel from '../ResearchEvidencePanel';
import { useSearchParams } from 'next/navigation';
import { useEffect } from 'react';
import { PlacePhotoCredit, getPlacePhoto } from '@/domains/place/components/PlacePhoto/PlacePhoto';
import CopyPlaceLinkButton from '@/domains/place/components/CopyPlaceLinkButton/CopyPlaceLinkButton';
import { usePlaceDetail } from '@/domains/place/hooks/usePlaces';
import { environmentLabel, seasonLabel } from '@/domains/place/api/placeApi';
import { safeHttpUrl } from '@/domains/place/lib/placeLinks';
import { ApiError } from '@/shared/lib/http/apiError';
import { getListReturnUrl, scrollToPlaceNotice } from '@/domains/place/lib/placeNavigation';
import PlaceReviews from '@/domains/review/components/PlaceReviews/PlaceReviews';
import FavoritePlaceButton from '../FavoritePlaceButton';
import { placeFactsClassName } from '../OperatingInfoPanel/OperatingInfoPanel';
import FisheryProtectionPanel from '../FisheryProtectionPanel/FisheryProtectionPanel';
import PlaceVerifiedPricePanel from '../PlaceVerifiedPricePanel/PlaceVerifiedPricePanel';
import PlaceActivityDetailsPanel from '../PlaceActivityDetailsPanel/PlaceActivityDetailsPanel';
import SnorkelingSafetyPanel from '../SnorkelingSafetyPanel/SnorkelingSafetyPanel';

const eyebrowClassName = 'text-[10px] font-extrabold tracking-[1.7px] text-[#6156ff]';
const actionClassName = 'mt-5 block rounded-lg bg-[#5147ff] p-3 text-center text-[12px] text-white no-underline';
const emptyClassName = 'py-[70px] text-center md:px-5 md:py-[100px]';
const emptyTitleClassName = 'mt-5 text-[26px] font-bold';
const emptyTextClassName = 'mt-4 text-[13px] text-[#718096]';
const sectionClassName = 'my-5 rounded-2xl border border-[#e9edf3] p-5 sm:p-6 [&>p]:mt-2.5 [&>p]:text-[13px]/[1.9] [&>p]:text-[#718096]';
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
  const search = params.toString();
  const phone = place?.operatingInfo?.contactPhone?.trim();
  const phoneHref = phone && /^[+\d\s()-]+$/.test(phone) ? `tel:${phone.replace(/[^+\d]/g, '')}` : null;
  const isMarine = place?.activities.some(activity => ['해수욕장', '스노클링'].includes(activity.activityTypeName)) ?? false;

  useEffect(() => {
    if (!place?.notices) return;

    scrollToPlaceNotice();
    window.addEventListener('hashchange', scrollToPlaceNotice);
    return () => window.removeEventListener('hashchange', scrollToPlaceNotice);
  }, [search, place?.notices]);

  return (
    <div className="[&_a:focus-visible]:outline-3 [&_a:focus-visible]:outline-offset-4 [&_a:focus-visible]:outline-[#5147ff]">
      <main className="m-auto max-w-[1440px] p-5 pb-28 md:px-9 md:py-7">
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
            {place.name?.startsWith('[데모]') && <p className="mt-5 rounded-lg bg-[#f5f4ff] px-4 py-3 text-xs/[1.8] text-[#716a9b]">개발용 가상 장소 · 실제 시설·주소·운영 정보가 아닙니다.</p>}
            <div className="mt-7 mb-8 grid grid-cols-1 items-center gap-[26px] md:mb-10 md:grid-cols-2 md:gap-9">
              <div className="md:col-start-2 md:row-start-1">
                <nav aria-label="경로 탐색" className="flex flex-wrap items-center gap-2 text-xs text-slate-500"><Link href="/">홈</Link><span aria-hidden="true">›</span><Link href="/explore/">탐색</Link>{place.activities[0] && <><span aria-hidden="true">›</span><Link href={`/explore/?category=${encodeURIComponent(place.activities[0].activityTypeName)}`}>{place.activities[0].activityTypeName}</Link></>}</nav>
                <p className="mt-4 text-[13px] text-[#7c899d] md:mt-6">{place.address ?? '주소 미확인'}</p>
                <h1 className="mt-2.5 text-[30px] font-extrabold tracking-[-1.5px] md:text-[clamp(28px,3vw,42px)]">{place.name ?? '이름 미확인'}</h1>

                <div className="mt-6 flex flex-wrap gap-[7px]">{place.activities.map(activity => <span key={activity.placeActivityId} className="rounded-md bg-[#f3f2ff] px-2.5 py-1.5 text-[12px] text-[#6156cc]">{activity.activityTypeName}</span>)}</div>
                <div className="mt-5 flex flex-wrap items-start gap-2">
                  <FavoritePlaceButton placeId={place.id} />
                  <CopyPlaceLinkButton label="장소 링크 복사" path={`/places/detail/?id=${place.id}`} />
                  {place.address && <CopyPlaceLinkButton label="주소 복사" text={place.address}/>}
                </div>
              </div>
              <div className="row-start-1 min-w-0 [&>div]:rounded-[18px] md:row-start-1 md:col-start-1"><PlaceMedia key={place.id} place={place} /></div>
            </div>

            <div className="mb-7 flex flex-wrap gap-3 [&>a]:rounded-xl [&>a]:border [&>a]:border-[#e5e3ef] [&>a]:px-5 [&>a]:py-3 [&>a]:text-sm [&>a]:font-semibold [&>a]:text-[#5942FF] [&>a:hover]:bg-[#F3F0FF]">
              {place.latitude !== null && place.longitude !== null && <a href={`https://map.kakao.com/link/to/${encodeURIComponent(place.name ?? '장소')},${place.latitude},${place.longitude}`} target="_blank" rel="noopener noreferrer">길찾기 ↗</a>}
              {phoneHref && <a href={phoneHref}>전화하기</a>}
              {homepage && <a href={homepage} target="_blank" rel="noopener noreferrer">홈페이지 ↗</a>}
            </div>
            <PlaceDetailNavigation key={place.id}/>
            <div className="grid grid-cols-1 items-start gap-6 md:grid-cols-[minmax(0,1fr)_minmax(250px,.55fr)] md:gap-[60px]">
              <div>
                {place.description?.trim() && <section className={sectionClassName} aria-labelledby="place-introduction">
                  <h2 className={sectionHeadingClassName} id="place-introduction">장소 소개</h2>
                  {place.description?.trim() && (place.description.length > 220 ? <details className="group text-sm leading-7 text-slate-600"><summary className="cursor-pointer list-none rounded focus-visible:outline-2 focus-visible:outline-[#5942FF]"><span className="block group-open:hidden">{place.description.slice(0, 220)}…</span><span className="mt-2 block text-xs font-semibold text-[#5942FF] group-open:hidden">소개 더 보기 ↓</span><span className="hidden text-xs font-semibold text-[#5942FF] group-open:block">소개 접기 ↑</span></summary><p className="mt-3 whitespace-pre-line">{place.description}</p></details> : <p>{place.description}</p>)}
                </section>}
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
                {place.activities.some(activity => activity.activityTypeName === '스노클링') &&
                  <SnorkelingSafetyPanel placeId={place.id}
                    activityDetails={place.verifiedDetails?.activityDetails ?? {}} />}
                {place.activities.some(activity => activity.activityTypeName === '스노클링') &&
                  <FisheryProtectionPanel placeId={place.id} name={place.name} address={place.address}
                    latitude={place.latitude} longitude={place.longitude}
                    managementPhone={place.operatingInfo?.contactPhone ?? null}
                    phoneVerified={place.operatingInfo?.verificationStatus === 'VERIFIED'} />}
                <PlaceActivityDetailsPanel activities={place.activities.map(activity => activity.activityTypeName)}
                  facts={place.verifiedDetails?.activityDetails ?? {}} operating={place.verifiedDetails?.operating ?? {}} />
                <PlaceVerifiedPricePanel prices={place.verifiedDetails?.prices ?? []} hideEmpty />
                <ResearchEvidencePanel placeId={place.id} activities={place.activities.map(activity => activity.activityTypeName)} operatingInfo={place.operatingInfo} />
                {isMarine && <MarineCommonNotice />}
                {place.latitude !== null && place.longitude !== null && <section className={sectionClassName} aria-labelledby="place-location"><h2 id="place-location" className={sectionHeadingClassName}>위치 지도</h2>{place.address && <p>{place.address}</p>}<div className="relative mt-4 h-[300px] overflow-hidden rounded-xl"><KakaoPlaceMap places={[{id:place.id,name:place.name ?? '장소',latitude:place.latitude,longitude:place.longitude}]} selection={null} onSelect={()=>{}}/></div></section>}
                <section className={sectionClassName} aria-labelledby="place-notices">
                  <h2 className={sectionHeadingClassName} id="place-notices">운영 공지</h2>
                  {place.notices.length ? place.notices.map(notice => (
                    <article className="mt-3 rounded-lg border border-[#e9edf3] p-4 text-[13px]"
                      data-place-notice-id={notice.placeNoticeId} key={notice.placeNoticeId}>
                      <h3 className="font-bold">{notice.title ?? '공지'}</h3>
                      <p className="mt-2 leading-[1.8] whitespace-pre-wrap text-[#718096]">{notice.content}</p>
                    </article>
                  )) : <p>등록된 운영 공지가 없습니다.</p>}
                </section>

              </div>
              <aside className="rounded-[14px] border border-[#e9edf3] bg-[#fafbfe] p-[25px] md:sticky md:top-[calc(var(--service-header-height)+24px)]" aria-labelledby="visit-information">
                <h2 className={sectionHeadingClassName} id="visit-information">방문 전 확인하세요</h2>
                <dl className={placeFactsClassName}>
                  <div><dt>상세 주소</dt><dd>{place.address ?? '미확인'}</dd></div>
                  {place.operationStatus && place.operationStatus !== 'UNVERIFIED' && <div><dt>현재 운영 여부</dt><dd>{place.operationStatus}</dd></div>}
                  {homepage && <div><dt>공식 홈페이지</dt><dd><a href={homepage} target="_blank" rel="noopener noreferrer">공식 안내 열기 ↗</a></dd></div>}
                </dl>
                {place.latitude !== null && place.longitude !== null && <a className={actionClassName} target="_blank" rel="noopener noreferrer" href={`https://map.kakao.com/link/to/${encodeURIComponent(place.name ?? '장소')},${place.latitude},${place.longitude}`}>카카오맵 길찾기 ↗</a>}
                <p className="mt-[22px] text-[11px]/[1.8] text-[#8793a7]">{place.name?.startsWith('[데모]') ? '방문 전 공식 안내를 확인하세요. [데모] 장소의 좌표와 주소는 실제 시설 위치가 아닙니다.' : '방문 전 운영시간과 이용 조건을 공식 안내에서 확인하세요.'}</p>
                <a href="tel:119" className="mt-4 block rounded-lg bg-red-50 p-3 text-center text-sm font-semibold text-red-600">긴급 신고 119</a>
                <Link href={returnUrl} scroll={false} className={actionClassName}>다른 장소 살펴보기</Link>
              </aside>
            </div>
            <PlaceReviews placeId={place.id} />
            <div className="fixed inset-x-0 bottom-0 z-20 flex items-center justify-between gap-3 border-t border-[#ececf2] bg-white px-5 pt-3 pb-[calc(env(safe-area-inset-bottom)+12px)] md:hidden">
              <FavoritePlaceButton placeId={place.id} iconOnly placeName={place.name}/>
              {place.latitude !== null && place.longitude !== null && <a href={`https://map.kakao.com/link/to/${encodeURIComponent(place.name ?? '장소')},${place.latitude},${place.longitude}`} target="_blank" rel="noopener noreferrer" className="flex min-h-11 flex-1 items-center justify-center rounded-full bg-[#5942FF] text-sm font-semibold text-white">길찾기 ↗</a>}
            </div>
            {(getPlacePhoto(place) || place.sources.some(source => safeHttpUrl(source.url))) && <section aria-labelledby="place-sources" className="mt-10 border-t border-[#e9edf3] py-5 text-[12px]/6 text-[#5b687d]">
              <h2 id="place-sources" className="mb-2 text-xs font-semibold">정보 제공 및 사진 출처</h2>
              <PlacePhotoCredit place={place} className="text-[12px]/6" />
              {[...new Map(place.sources.filter(source => safeHttpUrl(source.url) && safeHttpUrl(source.url) !== getPlacePhoto(place)?.source).map(source => [safeHttpUrl(source.url), source])).values()].map(source => <p key={source.sourceRecordId}><a href={safeHttpUrl(source.url)!} target="_blank" rel="noopener noreferrer" className="underline underline-offset-2">출처 안내 열기 ↗</a>{source.verifiedAt && <span> · 자료 확인일 <time dateTime={source.verifiedAt}>{source.verifiedAt.replace('T', ' ')}</time></span>}</p>)}
            </section>}
          </>
        )}
        <footer className="mt-10 border-t border-[#e9edf3] py-6 text-[11px] text-[#8793a7]">OUTPICK · 밖으로, 더 멀리</footer>
      </main>
    </div>
  );
}
