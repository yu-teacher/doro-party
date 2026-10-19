import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { act } from 'react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useAuthStore } from '../store/authStore';
import { useCommentStore } from '../store/commentStore';
import { usePushStore } from '../store/pushStore';
import PushSync from './PushSync';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

// 운영에서는 앱이 /party/ 아래에 마운트된다(테스트 환경의 기본 기준 경로는 /)
vi.mock('../config', async (importOriginal) => ({ ...(await importOriginal<typeof import('../config')>()), ROUTER_BASENAME: '/party' }));

let container: HTMLDivElement;
let root: Root;
let where = '';
const refresh = vi.fn().mockResolvedValue(undefined);
const reset = vi.fn();
const loadUnread = vi.fn().mockResolvedValue(undefined);
let worker: EventTarget;
const originalDescriptor = Object.getOwnPropertyDescriptor(navigator, 'serviceWorker');

function Where() {
  const location = useLocation();
  where = `${location.pathname}${location.search}`;
  return null;
}

beforeEach(() => {
  vi.clearAllMocks();
  usePushStore.setState({ refresh, reset });
  useCommentStore.setState({ loadUnread });
  useAuthStore.setState({ isAuthenticated: false });
  worker = new EventTarget();
  Object.defineProperty(navigator, 'serviceWorker', { configurable: true, value: worker });
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
});
afterEach(() => {
  act(() => root.unmount());
  container.remove();
  if (originalDescriptor) {
    Object.defineProperty(navigator, 'serviceWorker', originalDescriptor);
  } else {
    Reflect.deleteProperty(navigator, 'serviceWorker');
  }
});

const mount = () => act(async () => root.render(<MemoryRouter initialEntries={['/']}><PushSync /><Where /></MemoryRouter>));
const post = (data: unknown) => act(async () => { worker.dispatchEvent(new MessageEvent('message', { data })); });

describe('로그인과 알림 연결', () => {
  it('로그인하면 이 기기의 알림 상태를 확인하고, 로그아웃하면 비운다', async () => {
    await mount();
    expect(reset).toHaveBeenCalledTimes(1);
    expect(refresh).not.toHaveBeenCalled();

    await act(async () => useAuthStore.setState({ isAuthenticated: true }));
    expect(refresh).toHaveBeenCalledTimes(1);

    await act(async () => useAuthStore.setState({ isAuthenticated: false }));
    expect(reset).toHaveBeenCalledTimes(2);
  });

  it('새 알림이 오면 새 댓글 표시를 바로 갱신한다', async () => {
    await mount();
    await post({ type: 'party-push-received' });
    expect(loadUnread).toHaveBeenCalledTimes(1);
  });

  it('알림을 눌렀다는 메시지가 오면 새로고침 없이 앱 안에서 그 화면으로 이동한다', async () => {
    await mount();
    await post({ type: 'party-open', url: `${window.location.origin}/party/?map=m1&pin=p1` });
    expect(where).toBe('/?map=m1&pin=p1');
    await post({ type: 'party-open', url: `${window.location.origin}/party/friends` });
    expect(where).toBe('/friends');
  });

  it('앱 범위 밖 주소, 알 수 없는 메시지, 주소 없는 메시지는 무시한다', async () => {
    await mount();
    await post({ type: 'party-open', url: 'https://evil.test/party/friends' });
    await post({ type: 'party-open', url: `${window.location.origin}/other` });
    await post({ type: 'party-open' });
    await post({ type: 'something-else', url: `${window.location.origin}/party/friends` });
    await post('not an object');
    expect(where).toBe('/');
    expect(loadUnread).not.toHaveBeenCalled();
  });

  it('서비스 워커가 없는 환경에서도 오류 없이 그린다', async () => {
    Reflect.deleteProperty(navigator, 'serviceWorker');
    await mount();
    expect(container).toBeDefined();
  });
});
