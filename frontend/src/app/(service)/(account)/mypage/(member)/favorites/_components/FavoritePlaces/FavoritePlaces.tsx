'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import {
  changeFavoriteNotification,
  removeFavoritePlace,
  type FavoritePlace,
} from '@/domains/favorite/api/favoriteApi';
import { favoritePlacesQuery, useFavoritePlaces } from '@/domains/favorite/hooks/useFavoritePlaces';
import { useCurrentMember } from '@/domains/member/hooks/useCurrentMember';
import PlacePhoto, { PlacePhotoCredit } from '@/domains/place/components/PlacePhoto/PlacePhoto';
import { ApiError } from '@/shared/lib/http/apiError';

const linkClassName = 'font-bold text-[#5147d9] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#5147ff]';
const buttonClassName = `${linkClassName} cursor-pointer rounded-lg border border-[#ddd9ff] px-3 py-2 text-sm disabled:cursor-wait disabled:opacity-60`;

export default function FavoritePlaces() {
  const { data: member } = useCurrentMember();
  return member ? <FavoritePlacesList key={member.id} memberId={member.id} /> : null;
}

function FavoritePlacesList({ memberId }: { memberId: number }) {
  const favorites = useFavoritePlaces(memberId);

  if (favorites.isPending) return <p className="text-sm text-slate-600" role="status">관심 장소를 불러오고 있어요.</p>;
  if (favorites.isError && !favorites.data) {
    return <p className="text-sm text-red-700" role="alert">
      관심 장소를 불러오지 못했어요. <button className={linkClassName} type="button" onClick={() => void favorites.refetch()}>다시 시도</button>
    </p>;
  }
  if (!favorites.data?.length) {
    return <p className="text-sm text-slate-600">저장한 관심 장소가 없어요. <Link className={linkClassName} href="/">장소 둘러보기</Link></p>;
  }

  return (
    <>
      {favorites.isError && <p className="mb-3 text-sm text-red-700" role="alert">
        관심 목록을 새로 불러오지 못했어요. <button className={linkClassName} type="button" onClick={() => void favorites.refetch()}>다시 시도</button>
      </p>}
      <ul className="grid gap-4">
        {favorites.data.map(place => <FavoritePlaceItem key={place.id} memberId={memberId} place={place} />)}
      </ul>
    </>
  );
}

type FavoriteChange = { type: 'remove' } | { type: 'notification'; isNotified: boolean };

function FavoritePlaceItem({ memberId, place }: { memberId: number; place: FavoritePlace }) {
  const queryClient = useQueryClient();
  const queryKey = favoritePlacesQuery(memberId).queryKey;
  const name = place.name ?? (place.publishStatus === 'HIDDEN' ? '비공개된 장소' : '이름 미확인');
  const mutation = useMutation({
    mutationFn: async (change: FavoriteChange) => {
      if (change.type === 'remove') {
        await removeFavoritePlace(place.id);
        return null;
      }
      return changeFavoriteNotification({ placeId: place.id, isNotified: change.isNotified });
    },
    onSuccess: async result => {
      await queryClient.cancelQueries({ queryKey });
      queryClient.setQueryData<FavoritePlace[]>(queryKey, previous => {
        if (!result) return previous?.filter(item => item.id !== place.id);
        return previous?.map(item => item.id === result.placeId && item.publishStatus === 'PUBLISHED'
          ? { ...item, isNotified: result.isNotified } : item);
      });
      await queryClient.invalidateQueries({ queryKey });
    },
    onError: error => {
      if (error instanceof ApiError && error.details.kind === 'http' && error.details.code === 'FAVORITE_NOT_FOUND') {
        return queryClient.invalidateQueries({ queryKey });
      }
    },
  });

  return (
    <li className="grid gap-x-4 gap-y-2 rounded-2xl border border-[#e9edf3] bg-white p-4 sm:grid-cols-[140px_minmax(0,1fr)]">
      {place.publishStatus === 'PUBLISHED' && (
        <div className="min-w-0 overflow-hidden rounded-lg">
          <PlacePhoto place={place} />
        </div>
      )}
      <div className={place.publishStatus === 'HIDDEN' ? 'sm:col-span-2' : undefined}>
        <h2 className="text-base font-bold">
          {place.publishStatus === 'PUBLISHED'
            ? <Link className={linkClassName} href={`/places/detail/?id=${place.id}`}>{name}</Link>
            : name}
        </h2>
        <p className="mt-1 text-sm text-[#718096]">
          {place.publishStatus === 'PUBLISHED' ? place.address ?? '주소 미확인' : '현재 볼 수 없는 장소'}
        </p>
        <div className="mt-3 flex flex-wrap items-center gap-3">
          {place.publishStatus === 'PUBLISHED' && (
            <label className="inline-flex items-center gap-2 text-sm text-slate-700">
              <input
                type="checkbox"
                role="switch"
                className="size-4 accent-[#5147ff] disabled:cursor-wait disabled:opacity-60"
                aria-label={`${name} 변경 알림 수신`}
                checked={place.isNotified}
                disabled={mutation.isPending}
                onChange={event => mutation.mutate({ type: 'notification', isNotified: event.currentTarget.checked })}
              />
              변경 알림 {place.isNotified ? '켜짐' : '꺼짐'}
            </label>
          )}
          <button
            type="button"
            className={buttonClassName}
            aria-label={`${name} 관심 해제`}
            disabled={mutation.isPending}
            onClick={() => mutation.mutate({ type: 'remove' })}
          >
            관심 해제
          </button>
          {place.publishStatus === 'PUBLISHED' && (
            <PlacePhotoCredit place={place} compact className="min-w-0 flex-[1_1_10rem] self-end text-right text-[9px]/[1.4]" />
          )}
        </div>
        {mutation.isError && <p className="mt-2 text-sm text-red-700" role="alert">
          {mutation.error instanceof ApiError ? mutation.error.message : '관심 장소를 변경하지 못했어요.'}
        </p>}
      </div>
    </li>
  );
}
