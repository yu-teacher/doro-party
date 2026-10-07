import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import { VitePWA } from 'vite-plugin-pwa';
import path from 'path';

/** 게이트웨이에서 /party/ 하위 경로로 마운트한다(블로그가 /api/v1/ 을 쓰므로 같은 경로를 쓸 수 없다). */
const BASE = '/party/';
const API_TARGET = process.env.VITE_DEV_API_TARGET ?? 'http://localhost:8086';
const THEME_COLOR = '#0f172a';

export default defineConfig({
  base: BASE,
  plugins: [
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
