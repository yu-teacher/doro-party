import { describe, expect, it } from 'vitest';
import { clusterPoints } from './cluster';
import type { PixelPoint } from './cluster';

const point = (id: string, x: number, y: number): PixelPoint => ({ id, x, y });
const sizes = (points: PixelPoint[], threshold = 40) => clusterPoints(points, threshold).map((cluster) => cluster.ids.length).sort((a, b) => b - a);

describe('clusterPoints', () => {
  it('멀리 떨어진 점은 따로, 가까운 점은 하나로 묶는다', () => {
    expect(sizes([point('a', 0, 0), point('b', 300, 300), point('c', 600, 0)])).toEqual([1, 1, 1]);
    expect(sizes([point('a', 100, 100), point('b', 110, 105), point('c', 95, 98)])).toEqual([3]);
  });

  it('같은 위치의 점(같은 장소를 여러 명이 찍은 경우)은 하나로 묶인다', () => {
    const result = clusterPoints([point('a', 50, 50), point('b', 50, 50), point('c', 50, 50)], 40);

    expect(result).toHaveLength(1);
    expect(result[0].ids).toEqual(['a', 'b', 'c']);
    expect(result[0].x).toBe(50);
  });

  it('묶음의 중심은 속한 점들의 평균이다', () => {
    const [cluster] = clusterPoints([point('a', 100, 100), point('b', 120, 100)], 40);

    expect(cluster.x).toBe(110);
    expect(cluster.y).toBe(100);
  });

  it('임계 거리 안이면 묶고 밖이면 나눈다(경계 포함)', () => {
    expect(sizes([point('a', 0, 0), point('b', 40, 0)], 40)).toEqual([2]);
    expect(sizes([point('a', 0, 0), point('b', 40.5, 0)], 40)).toEqual([1, 1]);
  });

  it('모든 점이 정확히 한 묶음에 들어간다', () => {
    const points: PixelPoint[] = [];
    for (let i = 0; i < 500; i += 1) {
      points.push(point(`p${i}`, (i * 7919) % 1000, (i * 104729) % 1000));
    }

    const result = clusterPoints(points, 40);
    const ids = result.flatMap((cluster) => cluster.ids);

    expect(ids).toHaveLength(points.length);
    expect(new Set(ids).size).toBe(points.length);
  });

  it('확대하면(점 사이 픽셀 거리가 늘면) 묶음이 풀린다', () => {
    const near = [point('a', 100, 100), point('b', 120, 100), point('c', 140, 110)];
    const zoomedIn = near.map((p) => point(p.id, p.x * 5, p.y * 5));

    expect(sizes(near)).toEqual([3]);
    expect(sizes(zoomedIn).length).toBeGreaterThan(1);
  });

  it('keepSingle 에 든 점(선택된 핀)은 근처에 다른 점이 있어도 혼자 남는다', () => {
    const result = clusterPoints([point('a', 100, 100), point('selected', 101, 100), point('b', 102, 100)], 40, new Set(['selected']));

    const single = result.find((cluster) => cluster.ids.includes('selected'));
    expect(single?.ids).toEqual(['selected']);
    expect(result.find((cluster) => cluster.ids.includes('a'))?.ids.sort()).toEqual(['a', 'b']);
  });

  it('음수 좌표(화면 밖)도 같은 규칙으로 묶는다', () => {
    expect(sizes([point('a', -500, -500), point('b', -490, -505)])).toEqual([2]);
  });

  it('점이 없으면 빈 결과', () => {
    expect(clusterPoints([], 40)).toEqual([]);
  });
});
