import { describe, expect, it } from 'vitest';
import type { Visit } from '../api/types';
import { sortVisits } from './visits';

function visit(id: string, visitedOn: string, createdAt: string): Visit {
  return { id, pinId: 'p', userId: 'u', visitedOn, note: null, createdAt };
}

describe('sortVisits', () => {
  it('최근 방문일 순이고, 같은 날이면 나중에 남긴 기록이 위', () => {
    const sorted = sortVisits([
      visit('old', '2026-09-01', '2026-09-01T10:00:00Z'),
      visit('new', '2026-10-07', '2026-10-07T01:00:00Z'),
      visit('same-day-later', '2026-10-07', '2026-10-07T09:00:00Z'),
    ]);
    expect(sorted.map((v) => v.id)).toEqual(['same-day-later', 'new', 'old']);
  });

  it('원본 배열을 바꾸지 않는다', () => {
    const input = [visit('a', '2026-01-01', '1'), visit('b', '2026-02-01', '2')];
    sortVisits(input);
    expect(input.map((v) => v.id)).toEqual(['a', 'b']);
  });
});
