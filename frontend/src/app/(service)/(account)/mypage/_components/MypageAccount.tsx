'use client';

import { useQueryClient } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { logout, withdraw } from '@/domains/member/api/memberApi';
import { ApiError } from '@/shared/lib/http/apiError';
import { useCurrentMember } from '@/domains/member/hooks/useCurrentMember';
import { setCurrentMember } from '@/domains/member/queries/currentMemberQuery';

export default function MypageAccount() {
  const { data: member } = useCurrentMember();
  const queryClient = useQueryClient();
  const withdrawalDialog = useRef<HTMLDialogElement>(null);
  const [busy, setBusy] = useState(false);
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [logoutError, setLogoutError] = useState('');
  const [withdrawalError, setWithdrawalError] = useState('');
  const canWithdraw = password.length > 0 && confirmation === '탈퇴';

  async function doLogout() {
    setBusy(true);
    setLogoutError('');
    try {
      await logout();
      await setCurrentMember(queryClient, null);
      // 세션 종료 후 공개 화면을 새로 열어 보호 화면의 로그인 이동과 경쟁하지 않도록 합니다.
      window.location.replace('/');
    } catch {
      setLogoutError('로그아웃하지 못했습니다. 다시 시도해 주세요.');
    } finally {
      setBusy(false);
    }
  }

  async function doWithdraw(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || !canWithdraw) return;

    setBusy(true);
    setWithdrawalError('');
    try {
      await withdraw(password);
      withdrawalDialog.current?.close();
      await setCurrentMember(queryClient, null);
      window.location.replace('/?withdrawn=1');
    } catch (cause) {
      setWithdrawalError(cause instanceof ApiError && cause.details.kind === 'http' && cause.details.code === 'WITHDRAWAL_PASSWORD_MISMATCH'
        ? '현재 비밀번호가 올바르지 않습니다.'
        : '탈퇴를 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.');
    } finally {
      setBusy(false);
    }
  }

  function resetWithdrawalForm() {
    setPassword('');
    setConfirmation('');
    setWithdrawalError('');
  }

  if (!member) return null;

  return (
    <>
      <h1 className="mb-6 text-3xl font-extrabold tracking-tight">마이페이지</h1>
      <section aria-labelledby="account-heading" className="rounded-2xl border border-[#e4e9f1] bg-white p-5 sm:p-6">
        <h2 id="account-heading" className="text-xl font-bold">계정 정보</h2>
        <dl className="mt-4 grid gap-4 rounded-xl bg-slate-50 p-4 sm:grid-cols-2">
          <div>
            <dt className="text-sm text-slate-600">닉네임</dt>
            <dd className="mt-1 font-semibold break-words">{member.nickname}</dd>
          </div>
          <div>
            <dt className="text-sm text-slate-600">이메일</dt>
            <dd className="mt-1 font-semibold break-words">{member.email}</dd>
          </div>
        </dl>
        <button type="button" onClick={doLogout} disabled={busy}
          className="mt-4 min-h-11 rounded-lg border border-slate-300 px-4 text-sm font-semibold cursor-pointer hover:bg-slate-50 disabled:opacity-50">
          로그아웃
        </button>
        {logoutError && <p role="alert" className="mt-3 text-sm text-rose-700">{logoutError}</p>}
      </section>
      <section aria-labelledby="withdrawal-heading" className="mt-5 rounded-2xl border border-[#e4e9f1] bg-white p-5 sm:p-6">
        <h2 id="withdrawal-heading" className="text-xl font-bold">회원 탈퇴</h2>
        <p className="mt-2 text-sm text-slate-600">탈퇴 후에는 기존 계정으로 로그인할 수 없습니다.</p>
        <button type="button" disabled={busy} onClick={() => withdrawalDialog.current?.showModal()}
          className="mt-4 min-h-11 rounded-lg border border-rose-200 px-4 text-sm font-semibold text-rose-700 cursor-pointer hover:bg-rose-50 disabled:opacity-50">
          회원 탈퇴
        </button>
      </section>
      <dialog ref={withdrawalDialog} aria-labelledby="withdraw-title" closedby="any" onClose={resetWithdrawalForm}
        onCancel={event => { if (busy) event.preventDefault(); }}
        className="m-auto max-h-[calc(100%-2rem)] w-[calc(100%-2rem)] max-w-md overflow-y-auto rounded-2xl bg-white p-5 text-[#16243b] shadow-xl backdrop:bg-slate-950/50 sm:p-6">
        <h2 id="withdraw-title" className="text-xl font-bold">정말 탈퇴하시겠어요?</h2>
        <p className="mt-2 text-sm leading-relaxed text-slate-600">
          계정과 관심 장소, 알림 설정이 삭제됩니다. 남긴 제보 기록은 개인을 식별할 수 없도록 처리됩니다.
        </p>
        <form onSubmit={doWithdraw} className="mt-4 grid gap-4">
          <label className="grid gap-1 text-sm font-semibold">현재 비밀번호
            <input type="password" required autoComplete="current-password" value={password} disabled={busy}
              onChange={event => setPassword(event.target.value)} className="min-h-11 rounded-lg border border-slate-300 px-3 font-normal" />
          </label>
          <label className="grid gap-1 text-sm font-semibold">확인을 위해 ‘탈퇴’를 입력해 주세요
            <input required value={confirmation} disabled={busy} onChange={event => setConfirmation(event.target.value)}
              className="min-h-11 rounded-lg border border-slate-300 px-3 font-normal" />
          </label>
          {withdrawalError && <p role="alert" className="text-sm text-rose-700">{withdrawalError}</p>}
          <div className="grid grid-cols-2 gap-2">
            <button type="button" disabled={busy} onClick={() => withdrawalDialog.current?.close()}
              className="min-h-11 rounded-lg bg-slate-100 px-3 text-sm font-semibold hover:bg-slate-200 disabled:opacity-50">취소</button>
            <button type="submit" disabled={busy || !canWithdraw}
              className="min-h-11 rounded-lg bg-rose-700 px-3 text-sm font-semibold text-white hover:bg-rose-800 disabled:opacity-50">
              {busy ? '처리 중...' : '탈퇴 확정'}
            </button>
          </div>
        </form>
      </dialog>
    </>
  );
}
