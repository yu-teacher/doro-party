import { describe, expect, it } from 'vitest';
import type { Pin } from '../api/types';
import { distanceMeters, formatDistance, nearbyPins } from './nearby';

const HERE = { lat: 37.5563, lng: 126.9236 }; // 홍대입구 부근

function pin(partial: Partial<Pin> & { id: string }): Pin {
  return {
    mapId: 'm1',
    createdBy: 'u1',
    authorNickname: '나',
    authorColor: '#14B8A6',
    lat: HERE.lat,
    lng: HERE.lng,
    name: partial.id,
    sharedMemo: null,
    status: 'WISH',
    rating: null,
    revisitIntent: null,
    tags: [],
    visitCount: 0,
    lastVisitedOn: null,
    photoCount: 0, commentCount: 0,
    createdAt: '2026-10-07T00:00:00Z',
    updatedAt: '2026-10-07T00:00:00Z',
    ...partial,
  };
}

/** 위도 1도 ≈ 111.2km 이므로 북쪽으로 meters 만큼 떨어진 점 */
const northOf = (meters: number) => ({ lat: HERE.lat + meters / 111_195, lng: HERE.lng });

describe('distanceMeters', () => {
  it('같은 지점은 0', () => {
    expect(distanceMeters(HERE, HERE)).toBe(0);
  });

  it('북쪽으로 1km 떨어진 점은 약 1000m (오차 1% 이내)', () => {
    expect(distanceMeters(HERE, northOf(1000))).toBeCloseTo(1000, -1);
  });

  it('서울시청 ~ 홍대입구는 약 4.92km (WGS84 타원체 정밀 계산값 4924m 과 1% 이내)', () => {
    const cityHall = { lat: 37.5663, lng: 126.9779 };
    expect(Math.abs(distanceMeters(cityHall, HERE) - 4924) / 4924).toBeLessThan(0.01);
  });

  it('방향을 바꿔도 같다', () => {
    const a = { lat: 35.1796, lng: 129.0756 };
    expect(distanceMeters(a, HERE)).toBeCloseTo(distanceMeters(HERE, a), 6);
  });
});

describe('formatDistance', () => {
  it.each([
    [0, '10m'],
    [4, '10m'],
    [349, '350m'],
    [354, '350m'],
    [996, '1000m'],
    [1000, '1.0km'],
    [1249, '1.2km'],
    [1250, '1.3km'],
    [9949, '9.9km'],
    [12_400, '12km'],
  ])('%d m -> %s', (meters, text) => {
    expect(formatDistance(meters)).toBe(text);
  });
});

describe('nearbyPins', () => {
  const all = [
    pin({ id: 'far', ...northOf(2500), tags: ['카페'] }),
    pin({ id: 'near', ...northOf(100), status: 'VISITED', tags: ['카페'] }),
    pin({ id: 'mid', ...northOf(800), tags: ['술집'] }),
    pin({ id: 'edge', ...northOf(500) }),
  ];
  const base = { radiusMeters: null, status: 'ALL' as const, tag: null };

  it('가까운 순으로 정렬한다', () => {
    expect(nearbyPins(all, HERE, base).map((item) => item.pin.id)).toEqual(['near', 'edge', 'mid', 'far']);
  });

  it('거리를 함께 돌려준다', () => {
    const [first] = nearbyPins(all, HERE, base);
    expect(first.meters).toBeCloseTo(100, -1);
  });

  it('반경 안의 핀만 남긴다(경계는 포함)', () => {
    const edgeDistance = distanceMeters(HERE, northOf(500));
    expect(nearbyPins(all, HERE, { ...base, radiusMeters: edgeDistance }).map((item) => item.pin.id)).toEqual(['near', 'edge']);
    expect(nearbyPins(all, HERE, { ...base, radiusMeters: 1000 }).map((item) => item.pin.id)).toEqual(['near', 'edge', 'mid']);
  });

  it('상태로 거른다', () => {
    expect(nearbyPins(all, HERE, { ...base, status: 'VISITED' }).map((item) => item.pin.id)).toEqual(['near']);
    expect(nearbyPins(all, HERE, { ...base, status: 'WISH' }).map((item) => item.pin.id)).toEqual(['edge', 'mid', 'far']);
  });

  it('태그로 거른다', () => {
    expect(nearbyPins(all, HERE, { ...base, tag: '카페' }).map((item) => item.pin.id)).toEqual(['near', 'far']);
  });

  it('필터를 함께 쓰면 모두 만족하는 핀만 남는다', () => {
    expect(nearbyPins(all, HERE, { radiusMeters: 1000, status: 'WISH', tag: '술집' }).map((item) => item.pin.id)).toEqual(['mid']);
  });

  it('거리가 같으면 이름순이다', () => {
    const twins = [pin({ id: 'b', name: '나나', ...northOf(300) }), pin({ id: 'a', name: '가가', ...northOf(300) })];
    expect(nearbyPins(twins, HERE, base).map((item) => item.pin.name)).toEqual(['가가', '나나']);
  });

  it('핀이 없거나 모두 걸러지면 빈 목록', () => {
    expect(nearbyPins([], HERE, base)).toEqual([]);
    expect(nearbyPins(all, HERE, { ...base, radiusMeters: 10 })).toEqual([]);
  });
});
