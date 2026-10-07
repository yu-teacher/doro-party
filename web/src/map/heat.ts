export interface HeatSpot {
  lat: number;
  lng: number;
  score: number;
  /** 그 장소에 핀을 꽂은 사람 수 */
  people: number;
}

export interface HeatStyle {
  /** #RRGGBB */
  color: string;
  fillOpacity: number;
  /** 원의 반지름(미터) */
  radius: number;
}

const COLD = { hue: 50, saturation: 95, lightness: 55 };
const HOT = { hue: 2, saturation: 85, lightness: 48 };
const MIN_RADIUS = 60;
const RADIUS_PER_PERSON = 25;
const MAX_RADIUS = 220;
const MIN_OPACITY = 0.25;
const OPACITY_RANGE = 0.3;

function hslToHex(h: number, s: number, l: number): string {
  const sat = s / 100;
  const light = l / 100;
  const k = (n: number) => (n + h / 30) % 12;
  const a = sat * Math.min(light, 1 - light);
  const channel = (n: number) => Math.round(255 * (light - a * Math.max(-1, Math.min(k(n) - 3, Math.min(9 - k(n), 1)))));
  return `#${[channel(0), channel(8), channel(4)].map((value) => value.toString(16).padStart(2, '0')).join('')}`;
}

/**
 * 히트맵 한 칸의 스타일. 점수가 높을수록 노랑에서 빨강으로, 더 진하고 더 크게 그린다.
 * 점수는 보이는 장소들 중 가장 낮은 값과 높은 값 사이에서 상대적으로 정한다(모두 같으면 가장 뜨거운 쪽).
 */
export function heatStyle(spot: Pick<HeatSpot, 'score' | 'people'>, minScore: number, maxScore: number): HeatStyle {
  const t = maxScore > minScore ? (spot.score - minScore) / (maxScore - minScore) : 1;
  const clamped = Math.max(0, Math.min(1, t));
  const mix = (from: number, to: number) => from + (to - from) * clamped;
  return {
    color: hslToHex(mix(COLD.hue, HOT.hue), mix(COLD.saturation, HOT.saturation), mix(COLD.lightness, HOT.lightness)),
    fillOpacity: MIN_OPACITY + OPACITY_RANGE * clamped,
    radius: Math.min(MAX_RADIUS, MIN_RADIUS + RADIUS_PER_PERSON * spot.people),
  };
}

/** 추천 장소들에서 히트맵 칸과 점수 범위를 만든다. */
export function toHeatSpots(places: ReadonlyArray<{ lat: number; lng: number; score: number; people: number }>): { spots: HeatSpot[]; min: number; max: number } {
  const spots = places.map(({ lat, lng, score, people }) => ({ lat, lng, score, people }));
  const scores = spots.map((spot) => spot.score);
  return { spots, min: scores.length > 0 ? Math.min(...scores) : 0, max: scores.length > 0 ? Math.max(...scores) : 0 };
}
