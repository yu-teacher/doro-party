import type { Visit } from '../api/types';

/** 방문 기록을 최근 방문 순으로(같은 날이면 나중에 남긴 것이 위). 서버 목록과 같은 순서를 유지한다. */
export function sortVisits(visits: ReadonlyArray<Visit>): Visit[] {
  return [...visits].sort((a, b) => b.visitedOn.localeCompare(a.visitedOn) || b.createdAt.localeCompare(a.createdAt));
}
