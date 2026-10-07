import type { Pin, PinStatus } from '../api/types';

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

/** 마커를 무슨 색으로 칠할지: 핀의 상태(가고 싶은 곳/다녀온 곳)로 / 핀을 꽂은 사람(겹쳐보기)으로 */
export type ColorMode = 'status' | 'author';

/** 서버가 준 색이 #RRGGBB 가 아니면(스타일에 넣는 값이므로) 기본 색으로 바꾼다. */
const SAFE_COLOR = /^#[0-9A-Fa-f]{6}$/;
const FALLBACK_COLOR = '#94A3B8';

export function safeColor(color: string): string {
  return SAFE_COLOR.test(color) ? color : FALLBACK_COLOR;
}

export function pinColor(pin: Pick<Pin, 'status' | 'authorColor'>, mode: ColorMode): string {
  return mode === 'author' ? safeColor(pin.authorColor) : PIN_COLORS[pin.status];
}

/**
 * 핀 모양 SVG 를 data URL 로 만든다. 선택된 핀은 더 크게 그려 눈에 띄게 한다.
 * faded 는 옅게 그려(투명도·흰 중심) 가고 싶은 곳을 다녀온 곳과 구분할 때 쓴다.
 */
export function markerIcon(color: string, selected: boolean, faded = false): MarkerIconSpec {
  const size = selected ? SELECTED : BASE;
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" width="${size.width}" height="${size.height}" viewBox="0 0 30 40">` +
    `<path d="M15 1C7.8 1 2 6.8 2 14c0 9.6 13 25 13 25s13-15.4 13-25C28 6.8 22.2 1 15 1z" fill="${safeColor(color)}" fill-opacity="${faded ? 0.5 : 1}" stroke="#0F172A" stroke-width="2"/>` +
    `<circle cx="15" cy="14" r="5" fill="${faded ? '#F8FAFC' : '#0F172A'}"/></svg>`;
  return { url: `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`, ...size };
}
