import type { Pin } from '../api/types';
import type { StatusFilter } from '../store/mapStore';

export interface LatLng {
  lat: number;
  lng: number;
}

/** 반경 선택지(미터). null 은 거리 제한 없이 전체. */
export const RADIUS_OPTIONS: ReadonlyArray<{ meters: number | null; label: string }> = [
  { meters: 500, label: '500m' },
  { meters: 1000, label: '1km' },
  { meters: 3000, label: '3km' },
  { meters: null, label: '전체' },
];

export const DEFAULT_RADIUS_METERS = 1000;

const EARTH_RADIUS_METERS = 6_371_000;
const toRadians = (degrees: number) => (degrees * Math.PI) / 180;

/** 두 지점 사이의 대원 거리(미터). 하버사인 공식. */
export function distanceMeters(a: LatLng, b: LatLng): number {
  const dLat = toRadians(b.lat - a.lat);
  const dLng = toRadians(b.lng - a.lng);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(toRadians(a.lat)) * Math.cos(toRadians(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(h)));
}

/** 사람이 읽는 거리: 1km 미만은 10m 단위("350m"), 이상은 소수 한 자리("1.2km"), 10km 이상은 정수. */
export function formatDistance(meters: number): string {
  if (meters < 1000) {
    return `${Math.max(10, Math.round(meters / 10) * 10)}m`;
  }
  const km = meters / 1000;
  return km < 10 ? `${(Math.round(km * 10) / 10).toFixed(1)}km` : `${Math.round(km)}km`;
}

export interface NearbyPin {
  pin: Pin;
  meters: number;
}

export interface NearbyOptions {
  /** null 이면 거리 제한 없음 */
  radiusMeters: number | null;
  status: StatusFilter;
  tag: string | null;
}

/** 내 위치에서 가까운 순으로 핀을 정렬하고 반경·상태·태그로 거른다. 거리가 같으면 이름순(결과가 흔들리지 않게). */
export function nearbyPins(pins: ReadonlyArray<Pin>, origin: LatLng, options: NearbyOptions): NearbyPin[] {
  return pins
    .filter((pin) => (options.status === 'ALL' || pin.status === options.status) && (options.tag === null || pin.tags.includes(options.tag)))
    .map((pin) => ({ pin, meters: distanceMeters(origin, pin) }))
    .filter((item) => options.radiusMeters === null || item.meters <= options.radiusMeters)
    .sort((a, b) => a.meters - b.meters || a.pin.name.localeCompare(b.pin.name, 'ko'));
}
