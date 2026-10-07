import { describe, expect, it } from 'vitest';
import { addTag, applyTagInput, collectTags, normalizeTag } from './tags';

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

describe('applyTagInput', () => {
  it('쉼표가 없으면 입력창을 그대로 둔다', () => {
    expect(applyTagInput(['a'], '카페')).toEqual({ tags: ['a'], draft: '카페', error: null });
  });

  it('쉼표 앞까지를 태그로 확정하고 뒤는 입력창에 남긴다(조합 중인 글자가 쉼표와 함께 확정되는 경우)', () => {
    expect(applyTagInput([], '카페,술')).toEqual({ tags: ['카페'], draft: '술', error: null });
    expect(applyTagInput(['카페'], '술집,#데이트,')).toEqual({ tags: ['카페', '술집', '데이트'], draft: '', error: null });
  });

  it('빈 조각과 중복은 건너뛴다', () => {
    expect(applyTagInput(['a'], ',a, ,b,')).toEqual({ tags: ['a', 'b'], draft: '', error: null });
  });

  it('확정할 수 없는 조각은 입력창으로 되돌리고 사유를 알린다', () => {
    expect(applyTagInput([], '좋은,나쁜 태그,다음')).toEqual({ tags: ['좋은'], draft: '나쁜 태그,다음', error: 'invalid' });
    const full = Array.from({ length: 10 }, (_, i) => `t${i}`);
    expect(applyTagInput(full, 'extra,')).toEqual({ tags: full, draft: 'extra,', error: 'limit' });
  });
});
