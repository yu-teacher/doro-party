import type { Pin } from '../api/types';

/** 한 번에 겹칠 수 있는 지도 수(서버 기본 상한과 같다. 서버가 설정으로 바꿨다면 서버의 안내 문구가 보인다). */
export const MAX_OVERLAY_MAPS = 20;

/**
 * 겹쳐보기를 처음 열 때 어떤 지도를 고를지: 지난번에 고른 지도 중 아직 볼 수 있는 것이 있으면 그것, 없으면 볼 수 있는 지도 전부(상한까지).
 * 볼 수 있는 지도의 순서(내 지도가 앞)를 유지한다.
 */
export function defaultOverlayMapIds(available: ReadonlyArray<string>, remembered: ReadonlyArray<string>): string[] {
  const rememberedSet = new Set(remembered);
  const kept = available.filter((id) => rememberedSet.has(id));
  return (kept.length > 0 ? kept : [...available]).slice(0, MAX_OVERLAY_MAPS);
}

export interface Author {
  userId: string;
  nickname: string;
  color: string;
  count: number;
}

/** 겹친 핀을 꽂은 사람들(핀이 많은 순, 같으면 닉네임순). 범례와 작성자별 켜기·끄기에 쓴다. */
export function authorsOf(pins: ReadonlyArray<Pick<Pin, 'createdBy' | 'authorNickname' | 'authorColor'>>): Author[] {
  const byId = new Map<string, Author>();
  for (const pin of pins) {
    const found = byId.get(pin.createdBy);
    if (found) {
      found.count += 1;
    } else {
      byId.set(pin.createdBy, { userId: pin.createdBy, nickname: pin.authorNickname, color: pin.authorColor, count: 1 });
    }
  }
  return [...byId.values()].sort((a, b) => b.count - a.count || a.nickname.localeCompare(b.nickname, 'ko'));
}

/** 숨긴 작성자의 핀을 뺀다. */
export function withoutHiddenAuthors<T extends Pick<Pin, 'createdBy'>>(pins: ReadonlyArray<T>, hidden: ReadonlyArray<string>): T[] {
  if (hidden.length === 0) {
    return [...pins];
  }
  const hiddenSet = new Set(hidden);
  return pins.filter((pin) => !hiddenSet.has(pin.createdBy));
}

export interface SelectionChange {
  selected: Set<string>;
  /** 상한 때문에 일부를 고르지 못했는지 */
  truncated: boolean;
}

/** 이 지도들을 모두 고르거나(하나라도 안 골랐으면) 모두 해제한다(전부 골랐다면). 고르는 도중 상한에 닿으면 거기까지만 고른다. */
export function toggleAll(selected: ReadonlySet<string>, ids: ReadonlyArray<string>, max: number = MAX_OVERLAY_MAPS): SelectionChange {
  const next = new Set(selected);
  if (ids.length > 0 && ids.every((id) => selected.has(id))) {
    ids.forEach((id) => next.delete(id));
    return { selected: next, truncated: false };
  }
  let truncated = false;
  for (const id of ids) {
    if (next.has(id)) {
      continue;
    }
    if (next.size >= max) {
      truncated = true;
      break;
    }
    next.add(id);
  }
  return { selected: next, truncated };
}

/** 선택을 이 지도들로 바꾼다(상한까지). */
export function selectOnly(ids: ReadonlyArray<string>, max: number = MAX_OVERLAY_MAPS): SelectionChange {
  return { selected: new Set(ids.slice(0, max)), truncated: ids.length > max };
}
