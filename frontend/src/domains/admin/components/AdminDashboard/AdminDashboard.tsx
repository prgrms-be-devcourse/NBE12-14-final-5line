'use client';

import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { adminSummaryQuery } from '../../queries/adminSummaryQuery';
import './AdminDashboardArtwork.css';

const statClassName = 'grid gap-[5px] rounded-[15px] border p-3.5 min-[761px]:px-[22px] min-[761px]:py-5';
const statLabelClassName = 'text-[11px] font-bold text-[#66758d]';
const statValueClassName = 'text-[25px] tracking-[-.04em] min-[761px]:text-[31px]';
const statCaptionClassName = 'text-[11px] text-[#8b97a8]';
const taskClassName = `mt-2.5 flex min-h-[92px] items-center gap-2.5 rounded-[15px] border border-[#e4e9f2] bg-white p-3
  no-underline transition-[border-color,transform,box-shadow] duration-200 hover:-translate-y-0.5 hover:border-[#a9ade7]
  hover:shadow-[0_12px_24px_#1824500b] motion-reduce:transition-none min-[761px]:gap-[17px] min-[761px]:px-[19px] min-[761px]:py-3.5`;
const taskIconClassName = 'grid size-[45px] shrink-0 place-items-center rounded-xl text-[21px] font-extrabold';
const taskActionClassName = 'ml-auto flex items-center gap-2.5 text-[0px] font-[750] whitespace-nowrap text-[#5555a8] not-italic min-[761px]:text-[12px]';

export default function AdminDashboard() {
  const { data: summary, isPending, isError } = useQuery(adminSummaryQuery);

  return <div className="text-[#17223a]">
    <section className="relative flex min-h-[180px] justify-between overflow-hidden rounded-[19px] bg-[linear-gradient(115deg,#292e68,#5458ab)]
      px-[25px] py-[30px] text-white min-[761px]:min-h-[210px] min-[761px]:px-11 min-[761px]:py-[39px]"><div><p className="mb-6 text-[10px] font-[850] tracking-[.18em] text-[#c2c7ff]">MANAGE THE OUTSIDE</p><h1 className="mb-3 text-[clamp(28px,3vw,41px)] tracking-[-.055em]">오늘의 운영 현황</h1>
        <span className="text-[13px] text-[#d8dbfa]">검토가 필요한 내용을 확인하고 새 장소를 준비하세요.</span></div>
      <div className="admin-dashboardHeroArt" aria-hidden="true"><i /><i /><i /></div></section>
    {isError && <p role="alert" className="text-[#a73240]">현황을 불러오지 못했어요. 새로고침해 주세요.</p>}
    {isPending && <p role="status">운영 현황을 불러오고 있어요.</p>}
    {summary && <section className="mt-5 mb-7 grid grid-cols-3 gap-2 max-[391px]:grid-cols-2 min-[761px]:mb-10 min-[761px]:gap-3.5" aria-label="운영 현황">
      <div className={`${statClassName} border-[#dcdcf7] bg-[#f6f5ff]`}><span className={statLabelClassName}>처리 대기</span><strong className={statValueClassName}>{summary.pendingReports}</strong><small className={statCaptionClassName}>후기 신고</small></div>
      <div className={`${statClassName} border-[#e3e8f1] bg-white`}><span className={statLabelClassName}>등록 준비</span><strong className={statValueClassName}>{summary.draftPlaces}</strong><small className={statCaptionClassName}>비공개 장소 초안</small></div>
      <div className={`${statClassName} border-[#e3e8f1] bg-white max-[391px]:col-span-2`}><span className={statLabelClassName}>서비스 중</span><strong className={statValueClassName}>{summary.publishedPlaces}</strong><small className={statCaptionClassName}>공개 장소</small></div>
    </section>}
    <section className="max-w-[1000px]" aria-label="운영 작업">
      <div className="mb-3.5 flex items-end justify-between gap-4"><div><p className="mb-2 text-[10px] font-[850] tracking-[.15em] text-[#6161bd]">NEXT ACTION</p><h2 className="text-[22px] tracking-[-.04em]">바로 처리할 작업</h2></div>
        <span className="hidden text-[12px] text-[#8b97a8] min-[761px]:inline">필요한 메뉴로 이동하세요</span></div>
      <Link className={taskClassName} href="/admin/review-reports/"><span className={`${taskIconClassName} bg-[#fff0e8] text-[#d5693d]`} aria-hidden="true">!</span>
        <span className="grid min-w-0 gap-[5px]"><strong className="text-[15px]">후기 신고 검토</strong><small className="text-[12px]/[1.5] text-[#77849b]">신고 사유를 확인하고 후기 숨김 또는 반려를 결정합니다.</small></span>
        <em className={taskActionClassName}>{summary ? `${summary.pendingReports}건 대기` : '확인하기'} <b className="text-[18px]" aria-hidden="true">↗</b></em></Link>
      <Link className={taskClassName} href="/admin/places/"><span className={`${taskIconClassName} bg-[#eaf8f3] text-[#339c79]`} aria-hidden="true">＋</span>
        <span className="grid min-w-0 gap-[5px]"><strong className="text-[15px]">새 장소 등록</strong><small className="text-[12px]/[1.5] text-[#77849b]">기본 정보를 비공개 초안으로 저장하고 검토합니다.</small></span>
        <em className={taskActionClassName}>등록하기 <b className="text-[18px]" aria-hidden="true">↗</b></em></Link>
    </section>
  </div>;
}
