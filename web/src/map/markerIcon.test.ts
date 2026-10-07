import { describe, expect, it } from 'vitest';
import { markerIcon, pinColor, PIN_COLORS, safeColor } from './markerIcon';

describe('markerIcon', () => {
  it('색을 SVG 에 반영한 data URL 을 만든다', () => {
    const icon = markerIcon(PIN_COLORS.VISITED, false);
    expect(icon.url.startsWith('data:image/svg+xml')).toBe(true);
    expect(decodeURIComponent(icon.url)).toContain(PIN_COLORS.VISITED);
  });

  it('선택된 핀은 더 크다', () => {
    expect(markerIcon('#fff', true).width).toBeGreaterThan(markerIcon('#fff', false).width);
    expect(markerIcon('#fff', true).height).toBeGreaterThan(markerIcon('#fff', false).height);
  });
});

describe('옅은 표시와 안전한 색', () => {
  it('가고 싶은 곳은 옅게(투명도·흰 중심) 그린다', () => {
    const solid = decodeURIComponent(markerIcon('#E4572E', false, false).url);
    const faded = decodeURIComponent(markerIcon('#E4572E', false, true).url);

    expect(solid).toContain('fill-opacity="1"');
    expect(faded).toContain('fill-opacity="0.5"');
    expect(faded).toContain('#F8FAFC');
  });

  it('#RRGGBB 가 아닌 색은 스타일에 들어가지 않고 기본 색이 된다', () => {
    expect(safeColor('#12ab9F')).toBe('#12ab9F');
    expect(safeColor('red; background:url(x)')).toBe('#94A3B8');
    expect(decodeURIComponent(markerIcon('"><script>', false).url)).not.toContain('script');
  });

  it('칠하는 방식에 따라 상태 색 또는 작성자 색을 쓴다', () => {
    const pin = { status: 'VISITED' as const, authorColor: '#E4572E' };

    expect(pinColor(pin, 'status')).toBe(PIN_COLORS.VISITED);
    expect(pinColor(pin, 'author')).toBe('#E4572E');
    expect(pinColor({ status: 'WISH', authorColor: 'nope' }, 'author')).toBe('#94A3B8');
  });
});
