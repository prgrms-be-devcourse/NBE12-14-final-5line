import { ExternalLink, Phone, ShieldCheck } from 'lucide-react';
export default function MarineCommonNotice() {
  return <section className="my-5 rounded-2xl border border-[#e5e3ef] bg-[#F3F0FF]/40 p-5 sm:p-6" aria-label="해양 안전 및 수산자원 보호 안내">
    <h2 className="flex items-center gap-2 text-lg font-bold"><ShieldCheck size={20} className="text-[#5942FF]"/>해양 안전 및 이용 안내</h2>
    <details open className="group mt-3"><summary className="cursor-pointer text-sm font-semibold text-[#5942FF]">방문 전 안전 및 이용 수칙</summary><p className="mt-3 text-sm leading-7 text-slate-600">방문 전 기상, 파도, 조류와 현장 출입 통제를 확인하세요. 장소 등록은 입수나 수산물 채취 허용을 의미하지 않습니다. 지역별 제한과 방문 날짜에 적용되는 규정을 확인해야 합니다.</p>
    </details><a className="mt-4 flex items-center justify-between gap-3 rounded-xl border border-[#e5e3ef] bg-white p-4 text-sm font-semibold text-[#5942FF]" href="https://www.mof.go.kr/doc/ko/selectDoc.do?bbsSeq=22&docSeq=66688&menuSeq=851" target="_blank" rel="noopener noreferrer">해양수산부 금어기·금지체장 공식 자료<ExternalLink size={16}/></a>
    <p className="mt-2 text-xs leading-6 text-slate-500">기존 확보 자료의 연결입니다. 최신 적용 연도와 지역별 고시는 원문에서 확인하세요. 금어기가 아니어도 채취가 허용되는 것은 아닙니다.</p>
    <a href="tel:119" className="mt-4 flex items-center justify-between rounded-xl bg-red-50 p-4 text-sm font-semibold text-red-700"><span className="flex items-center gap-2"><Phone size={18}/>해양사고 긴급 신고</span><span>119</span></a>
  </section>;
}
