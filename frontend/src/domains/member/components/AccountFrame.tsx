import Link from 'next/link';
import type { ReactNode } from 'react';
import './lucian.css';
import './account.css';

export default function AccountFrame({ children }: { children: ReactNode }) {
  return (
    <div className="account-page grid min-h-screen bg-[#f7f6f4] text-[#17212e] min-[851px]:grid-cols-[minmax(0,1.07fr)_minmax(390px,.93fr)]">
      <aside className="account-story relative flex min-h-[190px] flex-col justify-between gap-3 overflow-hidden
        px-6 pt-6 pb-[18px] text-white *:relative min-[451px]:min-h-[290px] min-[451px]:gap-6 min-[451px]:py-7
        min-[851px]:min-h-0 min-[851px]:gap-[normal] min-[851px]:px-[clamp(32px,5vw,88px)] min-[851px]:py-11">
        <Link className="text-[22px] font-black tracking-[-.06em] text-white no-underline min-[451px]:text-[24px]" href="/">OUTPICK <span className="ml-2.5 text-[11px] font-[650] tracking-[.08em] opacity-80">밖으로, 더 멀리</span></Link>
        <div className="my-auto hidden w-full max-w-[590px] min-[451px]:block">
          <span className="mb-2.5 block text-[11px] font-extrabold tracking-[.24em] text-[#c3c3ff] min-[851px]:mb-6">FIND YOUR NEXT OUTSIDE</span>
          <h1 className="max-w-[620px] text-[clamp(38px,8vw,52px)]/[1.05] font-black tracking-[-.075em] min-[851px]:text-[clamp(48px,5vw,82px)]">이번 주말,<br />어디로 나갈까요?</h1>
          <p className="mt-2.5 max-w-[440px] text-[13px]/[1.7] text-white/76 min-[851px]:mt-6 min-[851px]:text-[16px]">관심 있는 활동과 장소를 찾고, 다음 외출을 가볍게 준비해 보세요.</p>
          <div className="account-route-art hidden min-[851px]:block" aria-hidden="true">
            <div className="account-route-art-head"><span>WEEKEND ROUTE</span><span>OUTPICK · EXPLORE</span></div>
            <svg viewBox="0 0 400 142" fill="none" role="presentation">
              <path className="account-route-line" d="M23 102 C89 18 137 122 200 68 S303 14 380 47" />
              <circle className="account-route-start" cx="23" cy="102" r="9" />
              <circle className="account-route-mid" cx="200" cy="68" r="9" />
              <circle className="account-route-end" cx="380" cy="47" r="12" />
            </svg>
            <div className="account-route-art-foot"><span>마음이 향하는 곳을 찾아보세요</span><span>↗</span></div>
          </div>
        </div>
        <div className="hidden items-center justify-between gap-5 text-[11px] font-bold text-white/67 min-[851px]:flex"><span>로그인 없이도 장소를 둘러볼 수 있어요.</span><Link className="font-extrabold whitespace-nowrap text-white no-underline hover:underline hover:underline-offset-4" href="/">장소 탐색하기 <span aria-hidden="true">↗</span></Link></div>
      </aside>
      <main className="account-main flex min-h-[calc(100vh-290px)] min-w-0 flex-col items-center justify-center px-[18px] pt-7 pb-[60px]
        min-[451px]:pt-9 min-[851px]:min-h-0 min-[851px]:px-6 min-[851px]:pt-[60px]"><div className="ux w-full max-w-[430px]">{children}</div></main>
    </div>
  );
}
