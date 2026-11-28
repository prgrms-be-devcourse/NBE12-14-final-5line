import type { Metadata } from 'next';
import Link from 'next/link';

export const metadata: Metadata = {
  title: '개인정보처리방침 검토본 | OUTPICK',
  description: 'OUTPICK 개인정보처리방침 검토본',
  robots: { index: false, follow: false },
};

const sections = [
  { id: 'purpose', number: '01', title: '처리 목적과 항목' },
  { id: 'retention', number: '02', title: '보유 및 이용 기간' },
  { id: 'sharing', number: '03', title: '제3자 제공과 처리 위탁' },
  { id: 'deletion', number: '04', title: '개인정보 파기' },
  { id: 'rights', number: '05', title: '이용자의 권리' },
  { id: 'security', number: '06', title: '보호 조치' },
  { id: 'contact', number: '07', title: '문의처' },
  { id: 'changes', number: '08', title: '방침 변경' },
];

const sectionClass = 'scroll-mt-28 border-b border-[#e5e9ef] py-9 last:border-b-0 md:py-11';
const titleClass = 'mb-5 flex items-baseline gap-3 text-[20px] font-extrabold tracking-[-.04em] text-[#1b2841] md:text-[22px]';
const bodyClass = 'text-[14px]/[1.85] text-[#4b596d] md:text-[15px]/[1.85]';

export default function PrivacyPage() {
  return (
    <main className="min-h-[calc(100vh-var(--service-header-height))] bg-[#f6f8fb] px-5 pb-24 pt-11 md:px-10 md:pt-16">
      <div className="mx-auto max-w-[1110px]">
        <nav aria-label="현재 위치" className="mb-8 flex items-center gap-2 text-xs font-semibold text-[#8290a3]">
          <Link href="/" className="hover:text-[#5147ff]">홈</Link><span aria-hidden="true">/</span><span className="text-[#42516a]">개인정보처리방침</span>
        </nav>
        <div className="mb-9 border-b border-[#dfe5ee] pb-9 md:mb-12 md:pb-11">
          <p className="mb-3 text-[11px] font-extrabold tracking-[.2em] text-[#5147ff]">OUTPICK · PRIVACY</p>
          <h1 className="text-[34px]/[1.15] font-black tracking-[-.06em] text-[#17243c] md:text-[49px]">개인정보처리방침</h1>
          <p className="mt-5 max-w-[720px] text-[15px]/[1.75] text-[#5e6a7d]">OUTPICK에서 어떤 정보를 왜 처리하고, 이용자가 자신의 정보를 어떻게 관리할 수 있는지 안내합니다.</p>
        </div>

        <div className="mb-9 rounded-2xl border border-[#e4d8a8] bg-[#fffaf0] px-5 py-4 text-[13px]/[1.7] text-[#6c571b]" role="note">
          <strong className="block font-extrabold">게시 전 검토본</strong>
          운영 주체, 개인정보 문의처, 보존 기간과 외부 서비스 계약 내용을 확인하는 중입니다. 이 페이지는 확정된 개인정보처리방침이 아닙니다.
        </div>

        <div className="grid gap-8 lg:grid-cols-[245px_minmax(0,1fr)] lg:gap-12">
          <aside className="self-start lg:sticky lg:top-8">
            <div className="rounded-[22px] border border-[#e3e8ef] bg-white p-5 shadow-[0_12px_32px_-28px_#24355566]">
              <p className="mb-4 text-[11px] font-extrabold tracking-[.14em] text-[#8a96a9]">목차</p>
              <ol className="grid grid-cols-2 gap-1 lg:grid-cols-1">
                {sections.map(section => <li key={section.id}><a className="flex items-center gap-2.5 rounded-lg px-2 py-2 text-[12px] font-semibold text-[#526176] transition-colors hover:bg-[#f1f0ff] hover:text-[#5147ff]" href={`#${section.id}`}><span className="text-[10px] font-bold text-[#a5aabd]">{section.number}</span>{section.title}</a></li>)}
              </ol>
            </div>
          </aside>

          <article className="rounded-[24px] border border-[#e3e8ef] bg-white px-5 shadow-[0_16px_46px_-35px_#24355566] md:px-10">
            <section id="purpose" className={sectionClass}>
              <h2 className={titleClass}><span className="text-sm font-bold text-[#6d63e6]">01</span>처리 목적과 항목</h2>
              <div className={`${bodyClass} space-y-4`}>
                <p>OUTPICK은 회원가입과 계정 관리에 이메일, 닉네임, 비밀번호를 사용합니다. 비밀번호는 원문 대신 해시값으로 저장합니다.</p>
                <p>회원이 이용하는 관심 장소, 알림 설정, 후기와 사진은 해당 기능을 제공하고 관리하기 위해 처리합니다. 후기 신고가 접수되면 신고 사유와 처리 상태를 관리합니다.</p>
                <p>로그인 상태 유지, 비밀번호 재설정, 반복 로그인 시도 제한에 필요한 인증 정보와 접속 관련 정보도 처리합니다. 실제 수집되는 접속 기록 항목은 게시 전에 확인합니다.</p>
              </div>
            </section>
            <section id="retention" className={sectionClass}>
              <h2 className={titleClass}><span className="text-sm font-bold text-[#6d63e6]">02</span>보유 및 이용 기간</h2>
              <p className={bodyClass}>회원 정보와 회원이 등록한 관심 장소, 후기, 사진, 알림 정보는 서비스 이용 중 처리합니다. 현재 회원 탈퇴 기능은 계정과 연결된 데이터를 삭제합니다. 운영 백업 및 접속 로그의 보존 대상과 기간, 별도 법령에 따른 보존 여부는 <strong className="font-bold text-[#8a5a16]">확인 후 기재할 예정</strong>입니다.</p>
            </section>
            <section id="sharing" className={sectionClass}>
              <h2 className={titleClass}><span className="text-sm font-bold text-[#6d63e6]">03</span>제3자 제공과 처리 위탁</h2>
              <p className={bodyClass}>비밀번호 재설정 메일 발송, 서비스 호스팅, 지도 제공 과정에 참여하는 외부 서비스의 계약 및 정보 처리 범위를 확인하고 있습니다. 실제 개인정보의 제3자 제공 또는 처리 위탁이 있다면 대상 업체, 업무와 처리 항목을 확정해 이곳에 공개하겠습니다. <strong className="font-bold text-[#8a5a16]">업체명과 처리 범위 확인 필요</strong></p>
            </section>
            <section id="deletion" className={sectionClass}>
              <h2 className={titleClass}><span className="text-sm font-bold text-[#6d63e6]">04</span>개인정보 파기</h2>
              <p className={bodyClass}>현재 회원 탈퇴 기능은 본인 확인 후 계정 정보와 연결된 관심 장소, 후기, 후기 사진, 개인 알림 및 인증용 토큰을 삭제하고 기존 로그인 상태를 무효화합니다. 백업과 로그에 남은 정보의 파기 절차 및 시점은 <strong className="font-bold text-[#8a5a16]">확인 후 기재할 예정</strong>입니다.</p>
            </section>
            <section id="rights" className={sectionClass}>
              <h2 className={titleClass}><span className="text-sm font-bold text-[#6d63e6]">05</span>이용자의 권리</h2>
              <p className={bodyClass}>이용자는 자신의 개인정보에 대한 열람, 정정·삭제, 처리정지 등을 요청할 수 있습니다. 계정 관리와 회원 탈퇴 기능을 이용하거나 아래 문의처로 요청할 수 있으며, 요청 처리에 필요한 범위에서 본인 여부를 확인합니다.</p>
            </section>
            <section id="security" className={sectionClass}>
              <h2 className={titleClass}><span className="text-sm font-bold text-[#6d63e6]">06</span>보호 조치</h2>
              <p className={bodyClass}>비밀번호는 해시값으로 저장하고, 회원 전용 기능에는 인증과 접근 권한 확인을 적용합니다. 인증 쿠키는 브라우저 스크립트가 읽지 못하도록 설정합니다. 비밀번호 재설정 토큰은 원문 대신 해시값을 저장합니다. 운영 환경의 접근 통제와 백업·로그 관리 방식은 실제 설정을 확인한 뒤 반영합니다.</p>
            </section>
            <section id="contact" className={sectionClass}>
              <h2 className={titleClass}><span className="text-sm font-bold text-[#6d63e6]">07</span>개인정보 문의처</h2>
              <p className={bodyClass}>운영 주체, 개인정보 보호 담당자 또는 담당 부서, 문의 이메일은 <strong className="font-bold text-[#8a5a16]">팀에서 확정한 뒤 기재할 예정</strong>입니다.</p>
            </section>
            <section id="changes" className={sectionClass}>
              <h2 className={titleClass}><span className="text-sm font-bold text-[#6d63e6]">08</span>방침 변경</h2>
              <p className={bodyClass}>처리 내용이 변경되면 실제 운영 방식에 맞게 이 방침을 수정해 공개합니다. 적용일은 <strong className="font-bold text-[#8a5a16]">확정 후 기재할 예정</strong>입니다.</p>
            </section>
          </article>
        </div>
        <div className="mt-10 flex justify-between border-t border-[#dfe5ee] pt-5 text-xs text-[#8491a3]"><span>OUTPICK · 밖으로, 더 멀리</span><Link href="/" className="font-bold text-[#5147ff] hover:underline">장소 탐색으로 돌아가기 ↗</Link></div>
      </div>
    </main>
  );
}
