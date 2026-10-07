import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { act, useState } from 'react';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import TagInput from './TagInput';

// React 의 act() 가 jsdom 에서 동작하도록 알린다
(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

function Harness() {
  const [tags, setTags] = useState<string[]>([]);
  return <TagInput tags={tags} onChange={setTags} />;
}

let container: HTMLDivElement;
let root: Root;

beforeEach(() => {
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
  act(() => root.render(<Harness />));
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

const input = () => container.querySelector('input') as HTMLInputElement;
const chips = () => Array.from(container.querySelectorAll('span')).map((span) => span.textContent?.replace(/^#/, '') ?? '');

/** 입력창의 값을 바꾸고 React 의 onChange 가 불리도록 input 이벤트를 보낸다. */
function type(value: string) {
  const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set;
  act(() => {
    setter?.call(input(), value);
    input().dispatchEvent(new Event('input', { bubbles: true }));
  });
}

function key(type: 'keydown' | 'keyup', init: KeyboardEventInit & { keyCode?: number }) {
  act(() => {
    input().dispatchEvent(new KeyboardEvent(type, { bubbles: true, cancelable: true, ...init }));
  });
}

describe('TagInput 의 Enter 처리', () => {
  it('영문처럼 조합이 없는 입력은 Enter 로 바로 태그가 된다', () => {
    type('cafe');
    key('keydown', { key: 'Enter' });
    key('keyup', { key: 'Enter' });

    expect(chips()).toEqual(['cafe']);
    expect(input().value).toBe('');
  });

  it('한글 조합 중 Enter(Chrome): 마지막 글자가 입력창에 다시 남지 않고 태그가 하나만 생긴다', () => {
    // 카 -> 카ㅍ -> 카페 를 치는 중에 Enter 로 확정
    type('카');
    type('카ㅍ');
    type('카페');
    key('keydown', { key: 'Enter', isComposing: true, keyCode: 229 });
    // 확정 중에는 입력창을 건드리지 않는다(여기서 비우면 확정되는 글자가 다시 들어간다)
    expect(input().value).toBe('카페');
    expect(chips()).toEqual([]);

    // 조합이 끝나면서 값이 확정되고, keyup 에서 태그로 만든다
    act(() => input().dispatchEvent(new CompositionEvent('compositionend', { bubbles: true, data: '카페' })));
    key('keyup', { key: 'Enter' });

    expect(chips()).toEqual(['카페']);
    expect(input().value).toBe('');
  });

  it('한글 조합 직후의 Enter(Safari: keydown 이 조합 종료 뒤에 오고 keyCode 만 229): 같은 결과', () => {
    type('술집');
    key('keydown', { key: 'Enter', isComposing: false, keyCode: 229 });
    expect(chips()).toEqual([]);
    key('keyup', { key: 'Enter' });

    expect(chips()).toEqual(['술집']);
    expect(input().value).toBe('');
  });

  it('조합이 끝난 뒤 다시 누른 Enter 는 빈 입력창이라 아무것도 더하지 않는다', () => {
    type('카페');
    key('keydown', { key: 'Enter', isComposing: true, keyCode: 229 });
    key('keyup', { key: 'Enter' });
    key('keydown', { key: 'Enter' });
    key('keyup', { key: 'Enter' });

    expect(chips()).toEqual(['카페']);
  });

  it('한글을 치다 쉼표로 확정해도 마지막 글자까지 태그가 된다', () => {
    type('카페,');
    expect(chips()).toEqual(['카페']);
    expect(input().value).toBe('');

    type('술집,데이');
    expect(chips()).toEqual(['카페', '술집']);
    expect(input().value).toBe('데이');
  });

  it('Backspace 는 입력창이 비었을 때만 마지막 태그를 지운다', () => {
    type('a,b,');
    key('keydown', { key: 'Backspace' });
    expect(chips()).toEqual(['a']);

    type('x');
    key('keydown', { key: 'Backspace' });
    expect(chips()).toEqual(['a']);
  });
});
