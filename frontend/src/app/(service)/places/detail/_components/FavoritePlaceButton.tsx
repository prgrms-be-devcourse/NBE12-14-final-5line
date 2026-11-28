'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import ActionToast from '@/shared/components/ActionToast';
import { Bookmark } from 'lucide-react';
import Link from 'next/link';
import { removeFavoritePlace, saveFavoritePlace } from '@/domains/favorite/api/favoriteApi';
import { favoritePlacesQuery, useFavoritePlaces } from '@/domains/favorite/hooks/useFavoritePlaces';
import { useCurrentMember } from '@/domains/member/hooks/useCurrentMember';
import { ApiError } from '@/shared/lib/http/apiError';

const buttonClassName = 'inline-flex items-center gap-1.5 rounded-lg border border-[#ddd9ff] bg-white px-3 py-[9px] text-xs/[1.4] font-bold text-[#5147ff] focus-visible:outline-3 focus-visible:outline-offset-3 focus-visible:outline-[#5147ff]';
const errorClassName = 'text-xs text-red-700';
const iconClassName = 'inline-flex size-9 shrink-0 items-center justify-center rounded-full border border-white/80 bg-white/95 text-[#5942FF] shadow-sm hover:bg-[#F3F0FF] focus-visible:outline-3 focus-visible:outline-offset-3 focus-visible:outline-[#5942FF]';
type FavoriteButtonProps = { placeId: number; iconOnly?: boolean; placeName?: string | null };

export default function FavoritePlaceButton({ placeId, iconOnly = false, placeName }: FavoriteButtonProps) {
  const member = useCurrentMember();
  const style = iconOnly ? iconClassName : buttonClassName;
  const name = placeName ?? '장소';

  if (member.isPending) return null;
  if (member.isError && iconOnly) return <button type="button" className={style} aria-label={`${name} 로그인 상태 다시 확인`} title="로그인 상태를 확인하지 못했어요. 다시 시도" onClick={()=>void member.refetch()}><Bookmark size={18} aria-hidden="true"/></button>;
  if (member.isError) return <p className={errorClassName} role="alert">로그인 상태를 확인하지 못했어요. <button type="button" className="underline" onClick={() => void member.refetch()}>다시 시도</button></p>;
  if (!member.data) return <Link className={style} href="/login/" aria-label={iconOnly ? `${name} 로그인하고 관심 장소 저장` : undefined} title={iconOnly ? '로그인하고 관심 장소 저장' : undefined}><Bookmark aria-hidden="true" size={iconOnly ? 18 : 14} />{!iconOnly && '로그인하고 관심 장소 저장'}</Link>;
  if (member.data.role !== 'USER') return null;

  return <MemberFavoriteButton key={`${member.data.id}:${placeId}`} memberId={member.data.id} placeId={placeId} iconOnly={iconOnly} placeName={name} />;
}

function MemberFavoriteButton({ memberId, placeId, iconOnly, placeName }: FavoriteButtonProps & { memberId: number }) {
  const queryClient = useQueryClient();
  const [feedback, setFeedback] = useState<string|null>(null);
  useEffect(()=>{if(!feedback)return;const timer=setTimeout(()=>setFeedback(null),3000);return ()=>clearTimeout(timer);},[feedback]);
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
    onSuccess: message => { setFeedback(message); return queryClient.invalidateQueries({ queryKey: favoritePlacesQuery(memberId).queryKey }); },
  });

  if (favorites.isPending && iconOnly) return <button type="button" className={iconClassName} disabled aria-label={`${placeName} 관심 상태 불러오는 중`}><Bookmark size={18} aria-hidden="true"/></button>;
  if (favorites.isPending) return <p className="text-xs text-[#718096]" role="status">관심 상태를 불러오고 있어요.</p>;
  if (favorites.isError && iconOnly) return <button type="button" className={iconClassName} aria-label={`${placeName} 관심 상태 다시 확인`} title="관심 상태를 확인하지 못했어요. 다시 시도" onClick={()=>void favorites.refetch()}><Bookmark size={18} aria-hidden="true"/></button>;
  if (favorites.isError) return <p className={errorClassName} role="alert">관심 상태를 불러오지 못했어요. <button type="button" className="underline" onClick={() => void favorites.refetch()}>다시 시도</button></p>;

  return (
    <div className={iconOnly ? 'relative' : 'flex flex-col items-start gap-1.5'}>
      <button
        type="button"
        aria-label={iconOnly ? `${placeName} ${saved ? '관심 장소 해제' : '관심 장소 저장'}` : undefined}
        aria-pressed={saved}
        title={iconOnly ? saved ? '관심 장소 해제' : '관심 장소 저장' : undefined}
        className={`${iconOnly ? iconClassName : buttonClassName} cursor-pointer disabled:cursor-wait disabled:opacity-60`}
        disabled={mutation.isPending}
        onClick={() => mutation.mutate(saved)}
      >
        <Bookmark aria-hidden="true" size={iconOnly ? 18 : 14} fill={saved ? 'currentColor' : 'none'} />
        {!iconOnly && (saved ? '관심 장소 해제' : '관심 장소 저장')}
      </button>
      {feedback && <ActionToast message={feedback} onDismiss={()=>setFeedback(null)}/>}
      {mutation.isError && <p className={iconOnly ? 'absolute right-0 top-11 z-20 w-28 rounded-lg border border-red-100 bg-white p-2 text-xs text-red-700 shadow-sm' : errorClassName} role="alert">{mutation.error instanceof ApiError ? mutation.error.message : '관심 장소를 변경하지 못했어요.'}</p>}
    </div>
  );
}
