'use client';

import Link from 'next/link';
import { useQueryClient } from '@tanstack/react-query';
import { useRouter, useSearchParams } from 'next/navigation';
import { useEffect, useState } from 'react';
import { confirmPasswordReset, login, requestPasswordReset, signup, validatePasswordReset } from '../api/memberApi';
import { setCurrentMember } from '../queries/currentMemberQuery';
import AccountFrame from './AccountFrame';
import FloatingField from './FloatingField';

const panelClassName = `account-panel rounded-[27px] border border-[#e5e3ed] bg-white p-5
  shadow-[0_24px_70px_-49px_#1d204a80,0_2px_8px_#1d204a0a] min-[401px]:p-[30px]`;
const formClassName = 'flex flex-col gap-[18px]';
const alertClassName = 'mb-4 rounded-xl px-3.5 py-3 text-[13px]/[1.5]';
const errorClassName = `${alertClassName} bg-[#fff1f2] text-[#b12d45]`;
const linksClassName = `mt-[22px] flex flex-wrap justify-center gap-4 text-[13px] text-[#6f7684]
  [&>a]:font-extrabold [&>a]:text-[#555ed5] [&>a:hover]:text-[#222a8a] [&>a:hover]:underline [&>a:hover]:underline-offset-4`;
const fieldNoteClassName = '-mt-[15px] -mb-1 text-[11px]/[1.5] text-[#747b8e]';
const focusClassName = 'focus-visible:outline-2 focus-visible:outline-offset-3 focus-visible:outline-[#645ee0]';

function errorText(error: unknown): string {
  if (error && typeof error === 'object' && 'message' in error && typeof error.message === 'string') return error.message;
  return '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.';
}

function Header({ step, title, description }: { step: string; title: string; description: string }) {
  return <><span className="mb-3 block text-[12px] font-[850] tracking-[.16em] text-[#6559d4] uppercase">{step}</span><h2 className="text-[30px]/[1.2] font-[850] tracking-[-.055em] text-[#17212e] min-[451px]:text-[clamp(32px,3vw,40px)]">{title}</h2><p className="mt-[13px] mb-[18px] text-[14px]/[1.7] text-[#697080] min-[451px]:mb-[26px]">{description}</p></>;
}

function Submit({ busy, success = false, children }: { busy: boolean; success?: boolean; children: React.ReactNode }) {
  return (
    <button
      className={`account-button relative flex min-h-[54px] w-full items-center justify-center gap-2.5 overflow-hidden rounded-2xl border-0
        text-[15px] font-extrabold text-white disabled:cursor-wait disabled:opacity-60 ${focusClassName}${busy ? ' is-loading' : ''}${success ? ' is-success' : ''}`}
      disabled={busy || success}
      type="submit"
      aria-busy={busy}
    >
      <span className="account-button-label">{success ? '완료됐어요' : busy ? '처리 중...' : children}</span>
      {busy && <span className="account-button-spinner" aria-hidden="true" />}
      {success && <span className="account-button-check" aria-hidden="true">✓</span>}
    </button>
  );
}

export function LoginScreen() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const loginParams = useSearchParams();
  const destination = loginParams.get('next') === '/mypage/' ? '/mypage/'
    : loginParams.get('next') === '/admin/' ? '/admin/' : '/';
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [success, setSuccess] = useState(false);
  const [error, setError] = useState('');
  const notice = loginParams.has('reset') ? '비밀번호가 변경되었습니다. 새 비밀번호로 로그인해 주세요.'
    : loginParams.has('signup') ? '회원가입이 완료되었습니다. 로그인해 주세요.' : '';
  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError('');
    try {
      const member = await login({ email, password });
      await setCurrentMember(queryClient, member);
      setSuccess(true);
      const target = destination === '/mypage/' ? destination : member.role === 'ADMIN' ? '/admin/' : destination;
      window.setTimeout(() => router.push(target), 420);
    }
    catch (cause) { setError(errorText(cause)); }
    finally { setBusy(false); }
  }
  return <AccountFrame><Header step="WELCOME BACK" title="다시 만나 반가워요" description="로그인하면 계정 정보를 확인하고 장소 탐색을 이어갈 수 있어요." />
    <form className={`${panelClassName} ${formClassName}${error ? ' has-error' : ''}`} onSubmit={submit}>
      {notice && <p className={`${alertClassName} bg-[#e9f8f1] text-[#177a57]`} role="status">{notice}</p>}
      <FloatingField label="이메일" type="email" value={email} onChange={setEmail} autoComplete="email" disabled={busy || success} autoFocus />
      <FloatingField label="비밀번호" type="password" value={password} onChange={setPassword} autoComplete="current-password" disabled={busy || success} />
      {error && <p className={errorClassName} role="alert">{error}</p>}
      <Submit busy={busy} success={success}>로그인</Submit>
      <Link className={`account-guest-button -mt-2 flex min-h-[52px] items-center justify-center gap-3 rounded-2xl border border-[#dcd9ef]
        bg-white text-[14px] font-extrabold text-[#49448f] no-underline ${focusClassName}`} href="/"><span>비회원으로 시작하기</span><span className="text-[18px]/none" aria-hidden="true">↗</span></Link>
      <p className="-mt-[13px] text-center text-[11px]/[1.5] text-[#7a8090]">로그인 없이 장소 검색과 지도를 이용할 수 있어요.</p>
      <Link className={`self-end text-[13px] text-[#555ed5] underline underline-offset-4 ${focusClassName}`} href="/password-reset/">비밀번호를 잊으셨나요?</Link>
    </form><div className={linksClassName}><span>아직 계정이 없나요?</span><Link className={focusClassName} href="/signup/">회원가입</Link></div>
  </AccountFrame>;
}

export function SignupScreen() {
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [nickname, setNickname] = useState('');
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [busy, setBusy] = useState(false);
  const [success, setSuccess] = useState(false);
  const [error, setError] = useState('');
  const [confirmationError, setConfirmationError] = useState('');
  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (password !== confirmation) { setConfirmationError('비밀번호가 일치하지 않습니다.'); return; }
    setBusy(true); setError(''); setConfirmationError('');
    try { await signup({ email, password, nickname }); setSuccess(true); window.setTimeout(() => router.push('/login/?signup=done'), 420); }
    catch (cause) { setError(errorText(cause)); }
    finally { setBusy(false); }
  }
  return <AccountFrame><Header step="JOIN OUTPICK" title="다음 외출을 함께해요" description="이메일로 계정을 만들고 장소 탐색을 시작해 보세요." />
    <form className={`${panelClassName} ${formClassName}${error ? ' has-error' : ''}`} onSubmit={submit}>
      <FloatingField label="이메일" type="email" value={email} onChange={setEmail} autoComplete="email" disabled={busy || success} autoFocus />
      <FloatingField label="닉네임" value={nickname} onChange={setNickname} autoComplete="nickname" disabled={busy || success} />
      <FloatingField label="비밀번호" type="password" value={password} onChange={value => { setPassword(value); setConfirmationError(''); }} autoComplete="new-password" minLength={8} maxLength={64} disabled={busy || success} />
      <p className={fieldNoteClassName}>비밀번호는 8~64자로 입력해 주세요.</p>
      <FloatingField label="비밀번호 확인" type="password" value={confirmation} onChange={value => { setConfirmation(value); setConfirmationError(''); }} autoComplete="new-password" minLength={8} maxLength={64} error={confirmationError} disabled={busy || success} />
      {error && <p className={errorClassName} role="alert">{error}</p>}
      <Submit busy={busy} success={success}>회원가입</Submit>
      <Link className={`account-guest-button -mt-2 flex min-h-[52px] items-center justify-center gap-3 rounded-2xl border border-[#dcd9ef]
        bg-white text-[14px] font-extrabold text-[#49448f] no-underline ${focusClassName}`} href="/"><span>비회원으로 시작하기</span><span className="text-[18px]/none" aria-hidden="true">↗</span></Link>
      <p className="-mt-[13px] text-center text-[11px]/[1.5] text-[#7a8090]">계정을 만들지 않아도 먼저 장소를 둘러볼 수 있어요.</p>
    </form><div className={linksClassName}><span>이미 계정이 있나요?</span><Link className={focusClassName} href="/login/">로그인</Link></div>
  </AccountFrame>;
}

export function ResetRequestScreen() {
  const [email, setEmail] = useState('');
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState('');
  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError('');
    try { await requestPasswordReset(email); setSent(true); }
    catch (cause) { setError(errorText(cause)); }
    finally { setBusy(false); }
  }
  return <AccountFrame><Header step="ACCOUNT RECOVERY" title="비밀번호를 다시 설정하세요" description="가입할 때 사용한 이메일을 입력해 주세요." />
    <div className={panelClassName}>
      {sent ? <div className="px-1.5 py-[22px] text-center" role="status"><span className="account-envelope" aria-hidden="true">✉</span><h3 className="mt-5 mb-2 text-[21px]">메일을 확인해 주세요</h3><p className="text-[14px]/[1.7] text-[#697080]">해당 이메일로 재설정할 수 있는 계정이 있다면 안내 메일이 발송됩니다.</p></div>
        : <form className={formClassName} onSubmit={submit}><FloatingField label="이메일" type="email" value={email} onChange={setEmail} autoComplete="email" />
          {error && <p className={errorClassName} role="alert">{error}</p>}<Submit busy={busy}>재설정 링크 요청</Submit></form>}
    </div><div className={linksClassName}><Link className={focusClassName} href="/login/">로그인으로 돌아가기</Link></div>
  </AccountFrame>;
}

export function ResetConfirmScreen() {
  const router = useRouter();
  const token = useSearchParams().get('token') ?? '';
  const [valid, setValid] = useState<boolean | null>(null);
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [busy, setBusy] = useState(false);
  const [success, setSuccess] = useState(false);
  const [error, setError] = useState('');
  const [confirmationError, setConfirmationError] = useState('');
  useEffect(() => {
    let active = true;
    if (!token) return;
    validatePasswordReset(token).then(() => { if (active) setValid(true); }).catch(() => { if (active) setValid(false); });
    return () => { active = false; };
  }, [token]);
  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (password !== confirmation) { setConfirmationError('새 비밀번호가 일치하지 않습니다.'); return; }
    setBusy(true); setError('');
    try { await confirmPasswordReset({ token, newPassword: password, newPasswordConfirmation: confirmation }); setSuccess(true); window.setTimeout(() => router.push('/login/?reset=done'), 420); }
    catch (cause) { setError(errorText(cause)); }
    finally { setBusy(false); }
  }
  return <AccountFrame><Header step="NEW PASSWORD" title="새 비밀번호 설정" description="새 비밀번호를 입력하고 다시 한번 확인해 주세요." />
    <div className={panelClassName}>{!token || valid === false ? <p className={errorClassName} role="alert">재설정 링크가 유효하지 않습니다. 새 링크를 요청해 주세요.</p>
      : valid === null ? <p role="status">재설정 링크를 확인하고 있습니다...</p>
        : <form className={formClassName} onSubmit={submit}>
          <FloatingField label="새 비밀번호" type="password" value={password} onChange={value => { setPassword(value); setConfirmationError(''); }} autoComplete="new-password" minLength={8} maxLength={64} disabled={busy || success} />
          <p className={fieldNoteClassName}>비밀번호는 8~64자로 입력해 주세요.</p>
          <FloatingField label="새 비밀번호 확인" type="password" value={confirmation} onChange={value => { setConfirmation(value); setConfirmationError(''); }} autoComplete="new-password" minLength={8} maxLength={64} error={confirmationError} disabled={busy || success} />
          {error && <p className={errorClassName} role="alert">{error}</p>}<Submit busy={busy} success={success}>비밀번호 변경</Submit>
        </form>}</div><div className={linksClassName}><Link className={focusClassName} href="/password-reset/">새 링크 요청</Link><Link className={focusClassName} href="/login/">로그인</Link></div>
  </AccountFrame>;
}
