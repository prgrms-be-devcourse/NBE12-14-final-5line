'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Bookmark } from 'lucide-react';
import Link from 'next/link';
import { removeFavoritePlace, saveFavoritePlace } from '@/domains/favorite/api/favoriteApi';
import { favoritePlacesQuery, useFavoritePlaces } from '@/domains/favorite/hooks/useFavoritePlaces';
import { useCurrentMember } from '@/domains/member/hooks/useCurrentMember';
import { ApiError } from '@/shared/lib/http/apiError';

const buttonClassName = 'inline-flex items-center gap-1.5 rounded-lg border border-[#ddd9ff] bg-white px-3 py-[9px] text-xs/[1.4] font-bold text-[#5147ff] focus-visible:outline-3 focus-visible:outline-offset-3 focus-visible:outline-[#5147ff]';
const errorClassName = 'text-xs text-red-700';

export default function FavoritePlaceButton({ placeId }: { placeId: number }) {
  const member = useCurrentMember();

  if (member.isPending) return null;
  if (member.isError) return <p className={errorClassName} role="alert">로그인 상태를 확인하지 못했어요. <button type="button" className="underline" onClick={() => void member.refetch()}>다시 시도</button></p>;
  if (!member.data) return <Link className={buttonClassName} href="/login/"><Bookmark aria-hidden="true" size={14} />로그인하고 관심 장소 저장</Link>;
  if (member.data.role !== 'USER') return null;

  return <MemberFavoriteButton key={`${member.data.id}:${placeId}`} memberId={member.data.id} placeId={placeId} />;
}

function MemberFavoriteButton({ memberId, placeId }: { memberId: number; placeId: number }) {
  const queryClient = useQueryClient();
  const favorites = useFavoritePlaces(memberId);
  const saved = favorites.data?.some(place => place.id === placeId) ?? false;
  const mutation = useMutation({
    mutationFn: async (remove: boolean) => {
      if (remove) return (await removeFavoritePlace(placeId)).message;
      try {
        return (await saveFavoritePlace(placeId)).message;
      } catch (error) {
        if (error instanceof ApiError && error.details.kind === 'http'
          && error.details.status === 409 && error.details.code === 'FAVORITE_ALREADY_EXISTS') {
          return error.message;
        }
        throw error;
      }
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: favoritePlacesQuery(memberId).queryKey }),
  });

  if (favorites.isPending) return <p className="text-xs text-[#718096]" role="status">관심 상태를 불러오고 있어요.</p>;
  if (favorites.isError) return <p className={errorClassName} role="alert">관심 상태를 불러오지 못했어요. <button type="button" className="underline" onClick={() => void favorites.refetch()}>다시 시도</button></p>;

  return (
    <div className="flex flex-col items-start gap-1.5">
      <button
        type="button"
        className={`${buttonClassName} cursor-pointer disabled:cursor-wait disabled:opacity-60`}
        disabled={mutation.isPending}
        onClick={() => mutation.mutate(saved)}
      >
        <Bookmark aria-hidden="true" size={14} fill={saved ? 'currentColor' : 'none'} />
        {saved ? '관심 장소 해제' : '관심 장소 저장'}
      </button>
      {mutation.isSuccess && <p className="text-[11px] text-[#6156cc]" role="status">{mutation.data}</p>}
      {mutation.isError && <p className={errorClassName} role="alert">{mutation.error instanceof ApiError ? mutation.error.message : '관심 장소를 변경하지 못했어요.'}</p>}
    </div>
  );
}
