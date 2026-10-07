import { describe, expect, it } from 'vitest';
import type { ScoreBreakdown } from '../api/types';
import { breakdownParts } from './RecommendationsSheet';

const base: ScoreBreakdown = {
  people: 3, peoplePoints: 30, wishCount: 0, wishPoints: 0, visitedCount: 0, visitedPoints: 0, againCount: 0, againPoints: 0,
  onceCount: 0, oncePoints: 0, ratedCount: 0, ratingAverage: null, ratingPoints: 0,
};

describe('breakdownParts', () => {
  it('0점 항목은 빼고, 있는 항목은 몇 명이 몇 점인지 보여 준다', () => {
    expect(breakdownParts(base)).toEqual(['3명이 찍었어요 +30']);

    const parts = breakdownParts({ ...base, wishCount: 2, wishPoints: 8, visitedCount: 1, visitedPoints: 2, againCount: 1, againPoints: 6, ratedCount: 1, ratingAverage: 5, ratingPoints: 4 });
    expect(parts).toEqual(['3명이 찍었어요 +30', '가고 싶어요 2명 +8', '다녀왔어요 1명 +2', '또 가고 싶어요 1명 +6', '평점 평균 5 +4']);
  });

  it('점수를 깎는 항목은 음수로 보여 준다', () => {
    const parts = breakdownParts({ ...base, onceCount: 1, oncePoints: -4, ratedCount: 1, ratingAverage: 1, ratingPoints: -4 });

    expect(parts).toContain('한 번이면 충분 1명 -4');
    expect(parts).toContain('평점 평균 1 -4');
  });

  it('평점이 중립이라 점수에 영향이 없으면 평점 항목을 보여 주지 않는다', () => {
    expect(breakdownParts({ ...base, ratedCount: 2, ratingAverage: 3, ratingPoints: 0 })).toEqual(['3명이 찍었어요 +30']);
  });
});
