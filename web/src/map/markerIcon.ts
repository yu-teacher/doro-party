import type { PinStatus } from '../api/types';

export const PIN_COLORS: Record<PinStatus, string> = {
  WISH: '#F59E0B',
  VISITED: '#14B8A6',
};

/** 작성 중인 핀(아직 저장 전)의 색 */
export const DRAFT_COLOR = '#E2E8F0';

export interface MarkerIconSpec {
  url: string;
  width: number;
  height: number;
}

const BASE = { width: 30, height: 40 };
const SELECTED = { width: 38, height: 50 };

/** 핀 모양 SVG 를 data URL 로 만든다. 선택된 핀은 더 크게 그려 눈에 띄게 한다. */
export function markerIcon(color: string, selected: boolean): MarkerIconSpec {
  const size = selected ? SELECTED : BASE;
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" width="${size.width}" height="${size.height}" viewBox="0 0 30 40">` +
    `<path d="M15 1C7.8 1 2 6.8 2 14c0 9.6 13 25 13 25s13-15.4 13-25C28 6.8 22.2 1 15 1z" fill="${color}" stroke="#0F172A" stroke-width="2"/>` +
    `<circle cx="15" cy="14" r="5" fill="#0F172A"/></svg>`;
  return { url: `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`, ...size };
}
