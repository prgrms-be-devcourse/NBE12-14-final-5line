import { useQuery } from '@tanstack/react-query';
import { environmentLabel, seasonLabel } from '@/domains/place/api/placeApi';
import { adminPlaceDetailQuery } from '../../queries/adminPlacesQuery';
import { placeSecondaryButton } from '../adminPlaceStyles';

function focusHeading(heading: HTMLHeadingElement | null) {
  heading?.focus();
}

export default function AdminPlaceDetail({ placeId }: { placeId: number }) {
  const detail = useQuery(adminPlaceDetailQuery(placeId));
  const place = detail.data;

  return (
    <div className="mt-4 space-y-4 border-t border-slate-200 pt-4">
      <h3 ref={focusHeading} tabIndex={-1} className="text-base font-bold">장소 상세</h3>
      {detail.isPending && <p role="status" className="text-sm text-slate-600">장소 상세를 불러오고 있어요.</p>}
      {detail.isError && (
        <div className="flex flex-wrap items-center gap-2">
          <p role="alert" className="text-sm text-red-700">장소 상세를 불러오지 못했어요.</p>
          <button type="button" onClick={() => void detail.refetch()} disabled={detail.isFetching}
            className={placeSecondaryButton}>
            다시 시도
          </button>
        </div>
      )}
      {place && (
        <>
          <dl className="grid grid-cols-[max-content_minmax(0,1fr)] gap-x-4 gap-y-2 text-sm text-slate-700 [&_dd]:wrap-anywhere">
            <dt className="font-semibold">장소명</dt><dd>{place.name ?? '이름 없음'}</dd>
            <dt className="font-semibold">주소</dt><dd>{place.address ?? '주소 없음'}</dd>
            <dt className="font-semibold">지역</dt><dd>{place.regionName ?? '이름 없음'}</dd>
            <dt className="font-semibold">위도</dt><dd>{place.latitude ?? '미입력'}</dd>
            <dt className="font-semibold">경도</dt><dd>{place.longitude ?? '미입력'}</dd>
            <dt className="font-semibold">소개</dt><dd className="whitespace-pre-wrap">{place.description || '소개 없음'}</dd>
            {place.nameAlias && <><dt className="font-semibold">별칭</dt><dd>{place.nameAlias}</dd></>}
            {place.homepage && <><dt className="font-semibold">홈페이지</dt><dd>{place.homepage}</dd></>}
          </dl>
          <section>
            <h4 className="mb-2 text-sm font-bold">종목</h4>
            {place.activities.length === 0 ? <p className="text-sm text-slate-600">등록된 종목이 없어요.</p> : (
              <ul className="space-y-2 text-sm text-slate-700">
                {place.activities.map(activity => (
                  <li key={activity.placeActivityId} className="rounded-lg border border-slate-200 p-3">
                    <p className="wrap-anywhere font-semibold">{activity.activityTypeName ?? '이름 없음'}</p>
                    <p>{environmentLabel(activity.environmentType)}</p>
                    {activity.seasons.length > 0 && <p>{activity.seasons.map(seasonLabel).join(', ')}</p>}
                  </li>
                ))}
              </ul>
            )}
          </section>
          <section>
            <h4 className="mb-2 text-sm font-bold">출처</h4>
            {place.sources.length === 0 ? <p className="text-sm text-slate-600">등록된 출처가 없어요.</p> : (
              <ul className="space-y-2 text-sm text-slate-700">
                {place.sources.map(source => (
                  <li key={source.sourceRecordId} className="rounded-lg border border-slate-200 p-3">
                    <p className="wrap-anywhere">{source.url ?? 'URL 없음'}</p>
                    <p className="mt-1 text-slate-600">확인일 {source.verifiedAt?.slice(0, 10) ?? '미확인'}</p>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </>
      )}
    </div>
  );
}
