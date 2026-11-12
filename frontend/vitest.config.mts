import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vitest/config';

// Next.js와 동일한 @ 별칭을 사용하고, 화면 테스트가 아닌 Node 기반 테스트를 실행합니다.
export default defineConfig({
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'node',
    setupFiles: ['./tests/setup.ts'],
  },
});
