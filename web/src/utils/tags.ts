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
