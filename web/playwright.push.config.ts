import { defineConfig } from '@playwright/test';

/**
 * 푸시 알림 검증(실제 브라우저, 운영 빌드). `npm run test:push`.
 * 서비스 워커는 운영 빌드에만 있으므로 빌드해서 미리보기 서버로 띄우고, API 응답은 테스트가 가로챈다(서버·DB 불필요).
 * Playwright 내장 Chromium(새 헤드리스 모드, channel 'chromium')으로 돈다. 기본 헤드리스 셸은 알림 권한을 지원하지 않아 풀 Chromium 이 필요하고,
 * 시스템 Chrome(PW_CHANNEL=chrome)은 쓰지 않는다(테스트마다 Dock 아이콘이 깜빡인다).
 */
const PORT = Number(process.env.E2E_PUSH_PORT ?? 4177);
const launchOptions = process.env.CI ? { args: ['--no-sandbox'] } : {};

export default defineConfig({
  testDir: './e2e-push',
  testMatch: '**/*.pwa.ts',
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: `http://localhost:${PORT}`,
    trace: 'off',
    launchOptions,
    channel: 'chromium',
    permissions: ['notifications'],
    serviceWorkers: 'allow',
    viewport: { width: 390, height: 844 },
  },
  projects: [{ name: 'chromium' }],
  webServer: {
    command: `npm run build && npx vite preview --port ${PORT} --strictPort --host 127.0.0.1`,
    url: `http://localhost:${PORT}/party/`,
    reuseExistingServer: !process.env.CI,
    timeout: 180_000,
  },
});
