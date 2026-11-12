import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';
import { getQueryClient } from '@/shared/lib/query/queryClient';

/** 화면을 먼저 해제해 사용 중인 조회를 취소하고, 캐시·타이머를 제거해 테스트를 격리합니다. */
afterEach(() => {
  cleanup();
  getQueryClient().clear();
});
