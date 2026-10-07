import { describe, expect, it } from 'vitest';
import { authorsOf, defaultOverlayMapIds, MAX_OVERLAY_MAPS, selectOnly, toggleAll, withoutHiddenAuthors } from './overlaySelection';

describe('defaultOverlayMapIds', () => {
  it('지난번에 고른 지도 중 아직 볼 수 있는 것만 다시 고른다', () => {
    expect(defaultOverlayMapIds(['a', 'b', 'c'], ['c', 'x', 'a'])).toEqual(['a', 'c']);
  });

  it('지난 선택이 없거나 모두 볼 수 없게 됐으면 볼 수 있는 지도 전부', () => {
    expect(defaultOverlayMapIds(['a', 'b'], [])).toEqual(['a', 'b']);
    expect(defaultOverlayMapIds(['a', 'b'], ['gone'])).toEqual(['a', 'b']);
  });

  it('상한을 넘으면 앞에서부터 상한까지', () => {
    const many = Array.from({ length: MAX_OVERLAY_MAPS + 5 }, (_, i) => `m${i}`);
    expect(defaultOverlayMapIds(many, [])).toHaveLength(MAX_OVERLAY_MAPS);
    expect(defaultOverlayMapIds(many, [])[0]).toBe('m0');
  });
});

describe('authorsOf', () => {
  const pin = (createdBy: string, nickname: string) => ({ createdBy, authorNickname: nickname, authorColor: '#aabbcc' });

  it('작성자별로 핀 개수를 세어 많은 순으로 정렬한다', () => {
    const authors = authorsOf([pin('u2', '다람'), pin('u1', '가나'), pin('u2', '다람'), pin('u2', '다람'), pin('u3', '나비')]);

    expect(authors.map((a) => [a.userId, a.count])).toEqual([['u2', 3], ['u1', 1], ['u3', 1]]);
    expect(authors[1].nickname).toBe('가나');
  });

  it('핀이 없으면 빈 목록', () => {
    expect(authorsOf([])).toEqual([]);
  });
});

describe('withoutHiddenAuthors', () => {
  it('숨긴 작성자의 핀만 뺀다', () => {
    const pins = [{ createdBy: 'a', id: 1 }, { createdBy: 'b', id: 2 }, { createdBy: 'a', id: 3 }];

    expect(withoutHiddenAuthors(pins, ['a']).map((p) => p.id)).toEqual([2]);
    expect(withoutHiddenAuthors(pins, []).map((p) => p.id)).toEqual([1, 2, 3]);
  });
});

describe('toggleAll', () => {
  it('하나라도 안 골랐으면 모두 고르고, 전부 골랐다면 모두 해제한다', () => {
    const first = toggleAll(new Set(['a']), ['a', 'b', 'c']);
    expect([...first.selected].sort()).toEqual(['a', 'b', 'c']);
    expect(first.truncated).toBe(false);

    const second = toggleAll(first.selected, ['a', 'b', 'c']);
    expect([...second.selected]).toEqual([]);
  });

  it('다른 구역에서 고른 지도는 건드리지 않는다', () => {
    const result = toggleAll(new Set(['x', 'y']), ['a', 'b']);
    expect([...result.selected].sort()).toEqual(['a', 'b', 'x', 'y']);
    expect([...toggleAll(result.selected, ['a', 'b']).selected].sort()).toEqual(['x', 'y']);
  });

  it('상한에 닿으면 거기까지만 고르고 알린다', () => {
    const result = toggleAll(new Set(['x']), ['a', 'b', 'c', 'd'], 3);

    expect(result.selected.size).toBe(3);
    expect(result.truncated).toBe(true);
    expect(result.selected.has('x')).toBe(true);
  });

  it('고를 지도가 없으면(빈 구역) 아무 일도 없다', () => {
    const result = toggleAll(new Set(['x']), []);
    expect([...result.selected]).toEqual(['x']);
    expect(result.truncated).toBe(false);
  });
});

describe('selectOnly', () => {
  it('선택을 이 지도들로 바꾸고 상한을 넘으면 알린다', () => {
    expect([...selectOnly(['a', 'b']).selected]).toEqual(['a', 'b']);
    const over = selectOnly(['a', 'b', 'c'], 2);
    expect(over.selected.size).toBe(2);
    expect(over.truncated).toBe(true);
  });
});
