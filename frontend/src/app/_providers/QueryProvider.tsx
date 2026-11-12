'use client';

import { QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { getQueryClient } from '@/shared/lib/query/queryClient';

/** 모든 하위 화면에 공통 Query Client를 제공하는 클라이언트 경계입니다. 서버에서 API를 미리 조회하지 않습니다. */
export default function QueryProvider({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={getQueryClient()}>{children}</QueryClientProvider>;
}
