import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vitest/config';

// 기본 테스트는 Node 환경을 사용하고, 컴포넌트 파일에서만 jsdom을 지정합니다.
export default defineConfig({
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'node',
    setupFiles: ['./tests/setup.ts'],
    environmentOptions: { 
      jsdom: { 
        url: 'https://api.outpick.test' 
      },
    },
    env: {
      NEXT_PUBLIC_API_BASE_URL: 'https://api.outpick.test',
    },
  },
});
