import { describe, expect, it } from 'vitest';
import { fitWithin } from './image';

describe('fitWithin', () => {
  it('긴 변을 기준으로 비율을 지키며 줄인다', () => {
    expect(fitWithin(4000, 3000, 1600)).toEqual({ width: 1600, height: 1200 });
    expect(fitWithin(3000, 4000, 1600)).toEqual({ width: 1200, height: 1600 });
  });

  it('작은 사진은 키우지 않는다', () => {
    expect(fitWithin(800, 600, 1600)).toEqual({ width: 800, height: 600 });
    expect(fitWithin(1600, 1600, 1600)).toEqual({ width: 1600, height: 1600 });
  });

  it('극단적인 비율에서도 한 변이 0 이 되지 않는다', () => {
    expect(fitWithin(10000, 1, 1600)).toEqual({ width: 1600, height: 1 });
  });
});
