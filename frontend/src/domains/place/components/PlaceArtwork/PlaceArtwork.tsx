import type { SamplePlace } from '../../types/samplePlace.type';
import './PlaceArtwork.css';

/** 실제 시설 사진과 혼동하지 않도록 샘플 표기를 넣은 공통 대체 이미지입니다. */
export default function PlaceArtwork({ artwork }: { artwork: SamplePlace['artwork'] }) {
  return (
    <div className={`outpick-artwork outpick-${artwork}`} aria-hidden="true">
      <span className="outpick-artworkSun" /><span className="outpick-artworkHill" /><span className="outpick-artworkHillBack" />
      <span className="outpick-artworkLabel">OUTPICK · SAMPLE</span>
    </div>
  );
}
