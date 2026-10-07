import { LIMITS } from '../api/types';

const TAG_PATTERN = new RegExp(`^[\\p{L}\\p{N}_-]{1,${LIMITS.tag}}$`, 'u');

/** 서버(TagNormalizer)와 같은 규칙: 앞뒤 공백·앞의 #을 떼고 소문자로. 규칙에 맞지 않으면 null. */
export function normalizeTag(raw: string): string | null {
  let tag = raw.trim();
  if (tag.startsWith('#')) {
    tag = tag.slice(1).trim();
  }
  tag = tag.toLowerCase();
  return TAG_PATTERN.test(tag) ? tag : null;
}

export type AddTagResult =
  | { ok: true; tags: string[] }
  | { ok: false; reason: 'invalid' | 'limit' };

/** 태그 하나를 목록에 더한다. 이미 있으면 그대로(중복 없이), 형식이 틀리거나 개수를 넘으면 사유를 돌려준다. */
export function addTag(tags: string[], raw: string): AddTagResult {
  const tag = normalizeTag(raw);
  if (tag === null) {
    return { ok: false, reason: 'invalid' };
  }
  if (tags.includes(tag)) {
    return { ok: true, tags };
  }
  if (tags.length >= LIMITS.tagsPerPin) {
    return { ok: false, reason: 'limit' };
  }
  return { ok: true, tags: [...tags, tag] };
}

/** 핀 목록에서 쓰인 태그를 많이 쓰인 순(같으면 가나다순)으로. */
export function collectTags(pins: ReadonlyArray<{ tags: string[] }>): string[] {
  const counts = new Map<string, number>();
  for (const pin of pins) {
    for (const tag of pin.tags) {
      counts.set(tag, (counts.get(tag) ?? 0) + 1);
    }
  }
  return [...counts.entries()]
    .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0], 'ko'))
    .map(([tag]) => tag);
}

export interface TagInputResult {
  tags: string[];
  /** 입력창에 남겨 둘 글자(아직 확정되지 않은 부분) */
  draft: string;
  error: 'invalid' | 'limit' | null;
}

/**
 * 입력창의 값에 쉼표가 들어오면 그 앞까지를 태그로 확정한다. 한글 입력기는 조합 중인 글자를 쉼표와 함께 확정하므로
 * 키 이벤트가 아니라 입력된 값을 보고 나눈다. 확정할 수 없는 조각(형식 오류·개수 초과)은 입력창에 되돌려 놓는다.
 */
export function applyTagInput(tags: string[], value: string): TagInputResult {
  if (!value.includes(',')) {
    return { tags, draft: value, error: null };
  }
  const segments = value.split(',');
  const rest = segments[segments.length - 1];
  let current = tags;
  for (let i = 0; i < segments.length - 1; i += 1) {
    if (segments[i].trim() === '') {
      continue;
    }
    const added = addTag(current, segments[i]);
    if (!added.ok) {
      return { tags: current, draft: segments.slice(i).join(','), error: added.reason };
    }
    current = added.tags;
  }
  return { tags: current, draft: rest, error: null };
}
