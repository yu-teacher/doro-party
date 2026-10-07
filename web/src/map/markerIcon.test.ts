import { describe, expect, it } from 'vitest';
import { markerIcon, PIN_COLORS } from './markerIcon';

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
