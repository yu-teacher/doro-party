import { describe, expect, it } from 'vitest';
import { addTag, collectTags, normalizeTag } from './tags';

describe('normalizeTag', () => {
  it('앞뒤 공백과 앞의 #을 떼고 소문자로 맞춘다', () => {
    expect(normalizeTag('  #Cafe ')).toBe('cafe');
    expect(normalizeTag('맛집')).toBe('맛집');
    expect(normalizeTag('a_b-1')).toBe('a_b-1');
  });

  it('형식에 맞지 않으면 null', () => {
    for (const bad of ['', '#', '공백 있음', 'a'.repeat(31), '<b>', 'a,b']) {
      expect(normalizeTag(bad)).toBeNull();
    }
  });
});

describe('addTag', () => {
  it('중복은 하나로 합친다', () => {
    expect(addTag(['cafe'], '#CAFE')).toEqual({ ok: true, tags: ['cafe'] });
    expect(addTag(['cafe'], '술집')).toEqual({ ok: true, tags: ['cafe', '술집'] });
  });

  it('형식이 틀리면 invalid, 개수를 넘으면 limit', () => {
    expect(addTag([], 'a b')).toEqual({ ok: false, reason: 'invalid' });
    const full = Array.from({ length: 10 }, (_, i) => `t${i}`);
    expect(addTag(full, 'extra')).toEqual({ ok: false, reason: 'limit' });
    expect(addTag(full, 't1')).toEqual({ ok: true, tags: full });
  });
});

describe('collectTags', () => {
  it('많이 쓰인 순, 같으면 가나다순', () => {
    const pins = [{ tags: ['카페', '술집'] }, { tags: ['술집'] }, { tags: ['데이트'] }];
    expect(collectTags(pins)).toEqual(['술집', '데이트', '카페']);
  });
});
