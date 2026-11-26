'use client';

import { useRouter } from 'next/navigation';
import { useEffect, useState, type ReactNode } from 'react';
import { useCurrentMember } from '../hooks/useCurrentMember';

const LOADING_DELAY_MS = 1000;

type ProtectedPageProps = {
  children: ReactNode;
  requiredRole?: 'USER' | 'ADMIN';
  loadingMessage?: string;
};

/** 현재 회원 조회 결과로 페이지 접근을 확인하고, 접근할 수 없으면 현재 방문 기록을 교체합니다. */
export default function ProtectedPage({
  children,
  requiredRole,
  loadingMessage = '계정 정보를 불러오고 있어요.',
}: ProtectedPageProps) {
  const router = useRouter();
  const { data: member, isPending, isError } = useCurrentMember();
  const [showLoading, setShowLoading] = useState(false);
  const hasWrongRole = member != null && requiredRole !== undefined && member.role !== requiredRole;
  const destination = isError ? null : member === null ? '/login/' : hasWrongRole ? '/mypage/' : null;

  useEffect(() => {
    if (!isPending) return;

    const timer = window.setTimeout(() => setShowLoading(true), LOADING_DELAY_MS);
    return () => window.clearTimeout(timer);
  }, [isPending]);

  useEffect(() => {
    if (destination) router.replace(destination);
  }, [destination, router]);

  if (!isPending && showLoading) setShowLoading(false);
  if (destination) return null;
  if (member && !isError) return children;
  if (!isError && !showLoading) return null;

  return (
    <div className="mx-auto max-w-7xl px-4 py-9 text-slate-700 md:px-6">
      {isError
        ? <p role="alert">계정 정보를 확인하지 못했어요. 잠시 후 다시 시도해 주세요.</p>
        : <p role="status">{loadingMessage}</p>}
    </div>
  );
}
