import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { UnreadPin } from '../api/types';
import UnreadCommentsSheet from './UnreadCommentsSheet';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

let container: HTMLDivElement;
let root: Root;
beforeEach(() => {
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
});
afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

async function render(unread: UnreadPin[], onPick: (item: UnreadPin) => void = () => undefined) {
  await act(async () => {
    root.render(<UnreadCommentsSheet unread={unread} mapNames={new Map([['m1', '맛집']])} onPick={onPick} onClose={() => undefined} />);
  });
}

describe('새 댓글 시트', () => {
  it('새 댓글이 없으면 어떤 핀에 댓글이 오는지 안내한다', async () => {
    await render([]);
    expect(container.textContent).toContain('새 댓글이 없어요');
  });

  it('핀 이름, 지도 이름, 개수를 보여 주고 누르면 그 핀을 고른다', async () => {
    const onPick = vi.fn();
    const first: UnreadPin = { mapId: 'm1', pinId: 'p1', pinName: '국밥집', unread: 3 };
    await render([first, { mapId: 'gone', pinId: 'p2', pinName: '다른 곳', unread: 1 }], onPick);

    expect(container.textContent).toContain('국밥집');
    expect(container.textContent).toContain('맛집');
    expect(container.textContent).toContain('새 댓글 3');
    expect(container.textContent).toContain('지도');

    const button = [...container.querySelectorAll('button')].find((candidate) => candidate.textContent?.includes('국밥집'));
    await act(async () => button?.click());
    expect(onPick).toHaveBeenCalledWith(first);
  });
});
