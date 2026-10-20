import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { act } from 'react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useOpenFromQuery } from './useOpenFromQuery';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

let container: HTMLDivElement;
let root: Root;
let currentUrl = '';
beforeEach(() => {
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
});
afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

function Probe({ ready, known, onOpen }: { ready: boolean; known: string[]; onOpen: (mapId: string, pinId: string | null, commentId: string | null) => void }) {
  const location = useLocation();
  currentUrl = `${location.pathname}${location.search}`;
  useOpenFromQuery({ ready, knownMapIds: known, onOpen });
  return null;
}

async function render(url: string, props: { ready: boolean; known: string[] }, onOpen = vi.fn()) {
  await act(async () => {
    root.render(<MemoryRouter initialEntries={[url]}><Probe {...props} onOpen={onOpen} /></MemoryRouter>);
  });
  return onOpen;
}

describe('알림에서 들어온 주소 처리', () => {
  it('지도와 핀을 열고 주소에서는 지운다', async () => {
    const onOpen = await render('/?map=m1&pin=p1', { ready: true, known: ['m1', 'm2'] });
    expect(onOpen).toHaveBeenCalledTimes(1);
    expect(onOpen).toHaveBeenCalledWith('m1', 'p1', null);
    expect(currentUrl).toBe('/');
  });

  it('지도만 있으면 핀 없이 연다(지도 공유 알림)', async () => {
    const onOpen = await render('/?map=m1', { ready: true, known: ['m1'] });
    expect(onOpen).toHaveBeenCalledWith('m1', null, null);
  });

  it('댓글까지 가리키는 알림(?comment=)이면 그 댓글도 함께 넘긴다', async () => {
    const onOpen = await render('/?map=m1&pin=p1&comment=c9', { ready: true, known: ['m1'] });
    expect(onOpen).toHaveBeenCalledWith('m1', 'p1', 'c9');
    expect(currentUrl).toBe('/');
  });

  it('준비되기 전에는 주소를 그대로 두고, 준비되면 한 번 처리한다', async () => {
    const onOpen = vi.fn();
    await render('/?map=m1&pin=p1', { ready: false, known: [] }, onOpen);
    expect(onOpen).not.toHaveBeenCalled();
    expect(currentUrl).toBe('/?map=m1&pin=p1');

    await act(async () => {
      root.render(<MemoryRouter initialEntries={['/?map=m1&pin=p1']}><Probe ready known={['m1']} onOpen={onOpen} /></MemoryRouter>);
    });
    expect(onOpen).toHaveBeenCalledTimes(1);
  });

  it('볼 수 없는 지도(공유가 풀렸거나 없어진 지도)의 알림은 열지 않고 주소만 지운다', async () => {
    const onOpen = await render('/?map=gone&pin=p1', { ready: true, known: ['m1'] });
    expect(onOpen).not.toHaveBeenCalled();
    expect(currentUrl).toBe('/');
  });

  it('알림과 무관한 주소는 건드리지 않는다', async () => {
    const onOpen = await render('/friends?x=1', { ready: true, known: ['m1'] });
    expect(onOpen).not.toHaveBeenCalled();
    expect(currentUrl).toBe('/friends?x=1');
  });

  it('콜백이 매 렌더링마다 바뀌어도 같은 알림을 두 번 열지 않는다', async () => {
    const calls: string[] = [];
    function Rerendering() {
      useOpenFromQuery({ ready: true, knownMapIds: ['m1'], onOpen: (mapId) => { calls.push(mapId); } });
      return null;
    }
    await act(async () => {
      root.render(<MemoryRouter initialEntries={['/?map=m1&pin=p1']}><Rerendering /></MemoryRouter>);
    });
    await act(async () => {
      root.render(<MemoryRouter initialEntries={['/?map=m1&pin=p1']}><Rerendering /></MemoryRouter>);
    });
    expect(calls).toEqual(['m1']);
  });
});
