'use client';

import { useState } from 'react';
import type { PlaceSummary } from '../../types/placeApi.type';
import PlaceArtwork from '../PlaceArtwork/PlaceArtwork';

export function getPlacePhoto(place: PlaceSummary) {
  try {
    const image = new URL(place.representativeImage ?? '');
    const source = new URL(place.imageSourceUrl ?? '');
    if (image.protocol !== 'https:' || image.hostname !== 'tong.visitkorea.or.kr' || image.username || image.password
      || image.port && image.port !== '443' || image.search || image.hash
      || !/^\/cms\/resource\/\d+\/[a-z\d_-]+\.(jpg|jpeg|png|webp|gif)$/i.test(image.pathname)
      || source.protocol !== 'https:' || source.username || source.password || !place.imageAttribution?.trim()
      || !['Type1', 'Type3'].includes(place.imageLicense ?? '')) return null;
    return { url: image.href, attribution: place.imageAttribution, source: source.href, license: place.imageLicense! };
  } catch { return null; }
}

function RemotePlacePhoto({ url, name }: { url: string; name: string }) {
  const [failed, setFailed] = useState(false);
  if (failed) return <PlaceArtwork artwork="forest" />;
  return <div className="aspect-[1.65] w-full overflow-hidden bg-[#f1f3f6]">
    {/* 원본을 변환·자르지 않고 표시한다. 변경금지 사진에도 동일하게 적용한다. */}
    {/* eslint-disable-next-line @next/next/no-img-element */}
    <img className="block size-full object-contain" src={url} alt={`${name} 시설 사진`} loading="lazy" decoding="async" referrerPolicy="no-referrer" onError={() => setFailed(true)} />
  </div>;
}

export default function PlacePhoto({ place }: { place: PlaceSummary }) {
  const photo = getPlacePhoto(place);
  return photo ? <RemotePlacePhoto key={photo.url} url={photo.url} name={place.name ?? '장소'} /> : <PlaceArtwork artwork="forest" />;
}

/** 카드 선택 버튼 밖에 배치해 링크를 중첩하지 않는다. */
export function PlacePhotoCredit({ place, className = 'mx-3 my-2 text-[11px]/[1.6]', compact = false }: {
  place: PlaceSummary;
  className?: string;
  compact?: boolean;
}) {
  const photo = getPlacePhoto(place);
  if (!photo) return null;
  const type = photo.license === 'Type3' ? '3' : '1';
  return <p className={`wrap-anywhere text-[#5b687d] [&>a]:text-[#4552a0] [&>a]:underline [&>a]:underline-offset-2 ${className}`}>
    {!compact && `${photo.attribution} · `}
    <a href={photo.source} target="_blank" rel="noopener noreferrer" title={compact ? photo.attribution : undefined}>
      {compact ? '© 한국관광공사' : '사진 출처'}
    </a> ·{' '}
    <a href={`https://www.kogl.or.kr/info/licenseType${type}.do`} target="_blank" rel="noopener noreferrer">공공누리 제{type}유형</a>
  </p>;
}
