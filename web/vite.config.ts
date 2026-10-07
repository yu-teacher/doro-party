import { defineConfig } from 'vitest/config';
import type { Plugin } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import { VitePWA } from 'vite-plugin-pwa';
import path from 'path';

/** 게이트웨이에서 /party/ 하위 경로로 마운트한다(블로그가 /api/v1/ 을 쓰므로 같은 경로를 쓸 수 없다). */
const BASE = '/party/';
const API_TARGET = process.env.VITE_DEV_API_TARGET ?? 'http://localhost:8086';
const THEME_COLOR = '#0f172a';

const BASE_WITHOUT_SLASH = BASE.replace(/\/+$/, '');

/**
 * 라우터의 기준 경로가 /party 라서 앱 안에서 홈으로 가면 주소가 "/party"(끝에 / 없음)가 된다.
 * 운영에서는 게이트웨이가 /party 를 /party/ 로 보내 주지만(deploy/gateway-party.conf), 개발·미리보기 서버에는 그 규칙이 없어서
 * 이 주소로 새로고침하면 404 안내가 뜬다. 같은 규칙을 맞춰 준다.
 */
function redirectBaseWithoutSlash(): Plugin {
  const redirect = (url: string | undefined): string | null => {
    if (url === undefined) {
      return null;
    }
    const [path, query] = url.split('?', 2);
    return path === BASE_WITHOUT_SLASH ? `${BASE}${query === undefined ? '' : `?${query}`}` : null;
  };
  const middleware = (req: { url?: string }, res: { statusCode: number; setHeader: (name: string, value: string) => void; end: () => void }, next: () => void) => {
    const target = redirect(req.url);
    if (target === null) {
      next();
      return;
    }
    res.statusCode = 301;
    res.setHeader('Location', target);
    res.end();
  };
  return {
    name: 'party-redirect-base-without-slash',
    configureServer: (server) => {
      server.middlewares.use(middleware);
    },
    configurePreviewServer: (server) => {
      server.middlewares.use(middleware);
    },
  };
}

export default defineConfig({
  base: BASE,
  plugins: [
    redirectBaseWithoutSlash(),
    react(),
    tailwindcss(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.svg', 'apple-touch-icon.png'],
      manifest: {
        name: '도로 파티',
        short_name: '도로 파티',
        description: '친구들과 지도를 겹쳐 보고 오늘 갈 곳을 정해요',
        lang: 'ko',
        start_url: BASE,
        scope: BASE,
        display: 'standalone',
        orientation: 'portrait',
        background_color: THEME_COLOR,
        theme_color: THEME_COLOR,
        icons: [
          { src: `${BASE}icon-192.png`, sizes: '192x192', type: 'image/png', purpose: 'any maskable' },
          { src: `${BASE}icon-512.png`, sizes: '512x512', type: 'image/png', purpose: 'any maskable' },
        ],
      },
      workbox: {
        // 앱 셸(정적 파일)만 캐시한다. 지도·핀 같은 개인 데이터가 담긴 API 와 업로드 사진은 절대 캐시하지 않는다.
        navigateFallback: `${BASE}index.html`,
        navigateFallbackDenylist: [/^\/party\/api\//],
        runtimeCaching: [],
      },
    }),
  ],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
  server: {
    port: 3005,
    proxy: {
      // 게이트웨이처럼 /party 를 떼고 백엔드로 전달한다
      '/party/api': {
        target: API_TARGET,
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/party/, ''),
      },
    },
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.{ts,tsx}'],
  },
});
