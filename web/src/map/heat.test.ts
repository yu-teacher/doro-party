import { describe, expect, it } from 'vitest';
import { heatStyle, toHeatSpots } from './heat';

describe('heatStyle', () => {
  it('점수가 낮으면 노랑, 높으면 빨강이고 #RRGGBB 형식이다', () => {
    const cold = heatStyle({ score: 10, people: 1 }, 10, 50);
    const hot = heatStyle({ score: 50, people: 1 }, 10, 50);

    expect(cold.color).toMatch(/^#[0-9a-f]{6}$/);
    expect(hot.color).toMatch(/^#[0-9a-f]{6}$/);
    const red = (hex: string) => parseInt(hex.slice(1, 3), 16);
    const green = (hex: string) => parseInt(hex.slice(3, 5), 16);
    expect(green(cold.color)).toBeGreaterThan(green(hot.color));   // 노랑은 초록 성분이 크고
    expect(red(hot.color)).toBeGreaterThan(green(hot.color));      // 빨강은 빨강 성분이 지배한다
  });

  it('점수가 높을수록 더 진하고, 사람이 많을수록 더 크다(상한 있음)', () => {
    const low = heatStyle({ score: 10, people: 1 }, 10, 50);
    const high = heatStyle({ score: 50, people: 1 }, 10, 50);

    expect(high.fillOpacity).toBeGreaterThan(low.fillOpacity);
    expect(heatStyle({ score: 10, people: 4 }, 10, 50).radius).toBeGreaterThan(low.radius);
    expect(heatStyle({ score: 10, people: 1000 }, 10, 50).radius).toBe(220);
  });

  it('모두 같은 점수면 가장 뜨거운 쪽으로 그리고, 범위 밖 값도 안전하다', () => {
    expect(heatStyle({ score: 30, people: 1 }, 30, 30)).toEqual(heatStyle({ score: 50, people: 1 }, 10, 50));
    expect(heatStyle({ score: -999, people: 1 }, 10, 50).fillOpacity).toBeCloseTo(0.25);
    expect(heatStyle({ score: 999, people: 1 }, 10, 50).fillOpacity).toBeCloseTo(0.55);
  });
});

describe('toHeatSpots', () => {
  it('장소들에서 칸과 점수 범위를 만든다', () => {
    const { spots, min, max } = toHeatSpots([
      { lat: 1, lng: 2, score: 14, people: 1 },
      { lat: 3, lng: 4, score: 50, people: 3 },
    ]);

    expect(spots).toHaveLength(2);
    expect([min, max]).toEqual([14, 50]);
    expect(toHeatSpots([])).toEqual({ spots: [], min: 0, max: 0 });
  });
});
