import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

/** public/push-sw.js 를 실제 파일 그대로 가짜 서비스 워커 환경에서 실행해 push·notificationclick 동작을 확인한다. */
const SOURCE = readFileSync(resolve(__dirname, '../../public/push-sw.js'), 'utf8');
const SCOPE = 'https://app.test/party/';

interface FakeClient {
  url: string;
  focused?: boolean;
  visibilityState?: string;
  focus: ReturnType<typeof vi.fn>;
  postMessage: ReturnType<typeof vi.fn>;
  navigate: ReturnType<typeof vi.fn>;
}

/**
 * 앱 창 대역. acknowledges 가 true 면 이동 메시지에 딸려 온 포트로 "처리했다" 고 답한다(최신 버전 화면),
 * false 면 답하지 않는다(이동 메시지를 처리하지 못하는 옛 버전 화면).
 */
function client(url: string, options: { acknowledges?: boolean; focused?: boolean; visibilityState?: string } = {}): FakeClient {
  const { acknowledges = true, focused = false, visibilityState = 'hidden' } = options;
  return {
    url,
    focused,
    visibilityState,
    focus: vi.fn().mockResolvedValue(undefined),
    postMessage: vi.fn((_message: unknown, transfer?: MessagePort[]) => { if (acknowledges) transfer?.[0]?.postMessage('handled'); }),
    navigate: vi.fn().mockResolvedValue({}),
  };
}

type Listener = (event: unknown) => void;

function load(windows: FakeClient[] = []) {
  const listeners: Record<string, Listener> = {};
  const showNotification = vi.fn().mockResolvedValue(undefined);
  const openWindow = vi.fn().mockResolvedValue(undefined);
  const matchAll = vi.fn().mockResolvedValue(windows);
  const fakeSelf = {
    addEventListener: (type: string, listener: Listener) => { listeners[type] = listener; },
    registration: { scope: SCOPE, showNotification },
    clients: { matchAll, openWindow },
    location: { origin: 'https://app.test' },
  };
  new Function('self', 'console', SOURCE)(fakeSelf, { warn: vi.fn(), error: vi.fn() });
  return { listeners, showNotification, openWindow, matchAll };
}

function pushEvent(data: unknown) {
  const waits: Promise<unknown>[] = [];
  const event = {
    data: data === undefined ? null : { json: () => { if (data instanceof Error) { throw data; } return data; } },
    waitUntil: (promise: Promise<unknown>) => { waits.push(promise); },
  };
  return { event, done: () => Promise.all(waits) };
}

function clickEvent(url: unknown) {
  const waits: Promise<unknown>[] = [];
  const close = vi.fn();
  const event = {
    notification: { close, data: url === undefined ? undefined : { url } },
    waitUntil: (promise: Promise<unknown>) => { waits.push(promise); },
  };
  return { event, close, done: () => Promise.all(waits) };
}

describe('push', () => {
  it('서버가 보낸 내용으로 알림을 띄운다(같은 tag 는 바꿔치기, 다시 울린다)', async () => {
    const sw = load();
    const { event, done } = pushEvent({ title: '골목 국밥집', body: '민준: 웨이팅 길어요', url: './?map=m1&pin=p1', tag: 'comment:p1' });

    sw.listeners.push(event);
    await done();

    expect(sw.showNotification).toHaveBeenCalledWith('골목 국밥집', {
      body: '민준: 웨이팅 길어요',
      tag: 'comment:p1',
      renotify: true,
      icon: `${SCOPE}icon-192.png`,
      data: { url: './?map=m1&pin=p1' },
    });
  });

  it('내용이 없거나 JSON 이 아니어도 알림은 띄운다(푸시를 받고 알림을 안 띄우면 브라우저가 구독을 제한한다)', async () => {
    const sw = load();
    for (const data of [undefined, new Error('not json')]) {
      const { event, done } = pushEvent(data);
      sw.listeners.push(event);
      await done();
    }
    expect(sw.showNotification).toHaveBeenCalledTimes(2);
    expect(sw.showNotification.mock.calls[0][0]).toBe('도로 파티');
    expect(sw.showNotification.mock.calls[0][1].body).toBe('새 알림이 있어요');
  });

  it('문자열이 아닌 값은 기본값으로 바꾼다', async () => {
    const sw = load();
    const { event, done } = pushEvent({ title: 123, body: { x: 1 }, url: null, tag: [] });
    sw.listeners.push(event);
    await done();
    expect(sw.showNotification).toHaveBeenCalledWith('도로 파티', expect.objectContaining({ body: '', tag: 'party', data: { url: './' } }));
  });

  it('열려 있는 앱 창에 새 알림이 왔음을 알린다(새 댓글 표시를 바로 갱신하게)', async () => {
    const open = client(`${SCOPE}`);
    const sw = load([open]);
    const { event, done } = pushEvent({ title: 't', body: 'b', url: './', tag: 'x' });
    sw.listeners.push(event);
    await done();
    expect(open.postMessage).toHaveBeenCalledWith({ type: 'party-push-received' });
  });
});

describe('notificationclick', () => {
  // 확인 응답(MessagePort)은 실제 이벤트 루프로 도착하므로 setTimeout 만 가짜로 쓴다(응답 대기 시간 800ms 를 건너뛰려고)
  beforeEach(() => { vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] }); });
  afterEach(() => { vi.useRealTimers(); });

  /** 클릭을 처리하고, 확인 응답을 기다리는 시간(800ms)이 지나가게 한 뒤 끝까지 기다린다. */
  async function click(sw: ReturnType<typeof load>, url: unknown) {
    const { event, close, done } = clickEvent(url);
    sw.listeners.notificationclick(event);
    // 창이 응답할 틈을 먼저 주고(실제 메시지 전달), 그래도 답이 없으면 대기 시간이 지난 것으로 본다
    for (let turn = 0; turn < 3; turn += 1) {
      await new Promise<void>((resolve) => setImmediate(resolve));
    }
    await vi.advanceTimersByTimeAsync(1000);
    await done();
    return { close };
  }

  it('앱이 열려 있으면 그 창을 앞으로 가져오고 새로고침 없이 앱 안에서 이동시킨다(창이 처리했다고 답하면 끝)', async () => {
    const open = client(`${SCOPE}friends`);
    const sw = load([client('https://other.test/'), open]);

    const { close } = await click(sw, './?map=m1&pin=p1');

    expect(close).toHaveBeenCalled();
    expect(open.focus).toHaveBeenCalled();
    expect(open.postMessage).toHaveBeenCalledWith({ type: 'party-open', url: `${SCOPE}?map=m1&pin=p1` }, [expect.anything()]);
    expect(open.navigate).not.toHaveBeenCalled();
    expect(sw.openWindow).not.toHaveBeenCalled();
  });

  it('창이 이동 메시지에 답하지 않으면(옛 버전 화면) 그 창을 주소로 직접 이동시킨다', async () => {
    const stale = client(`${SCOPE}`, { acknowledges: false });
    const sw = load([stale]);

    await click(sw, './?map=m1&pin=p1');

    expect(stale.navigate).toHaveBeenCalledWith(`${SCOPE}?map=m1&pin=p1`);
    expect(sw.openWindow).not.toHaveBeenCalled();
  });

  it('직접 이동도 안 되면(null 을 돌려주거나 오류) 새 창으로 연다', async () => {
    const cannotNavigate = client(SCOPE, { acknowledges: false });
    cannotNavigate.navigate.mockResolvedValue(null);
    let sw = load([cannotNavigate]);
    await click(sw, './friends');
    expect(sw.openWindow).toHaveBeenCalledWith(`${SCOPE}friends`);

    const throws = client(SCOPE, { acknowledges: false });
    throws.navigate.mockRejectedValue(new Error('not allowed'));
    sw = load([throws]);
    await click(sw, './friends');
    expect(sw.openWindow).toHaveBeenCalledWith(`${SCOPE}friends`);
  });

  it('앞으로 가져오기(focus)가 실패해도 이동은 계속한다', async () => {
    const open = client(SCOPE);
    open.focus.mockRejectedValue(new Error('Not allowed to focus a window'));
    const sw = load([open]);

    await click(sw, './?map=m1&pin=p1');

    expect(open.postMessage).toHaveBeenCalled();
    expect(sw.openWindow).not.toHaveBeenCalled();
  });

  it('앱 창이 여럿이면 지금 보고 있는 창(포커스 > 보이는 창)을 쓴다. 다른 출처의 창은 쓰지 않는다', async () => {
    const background = client(`${SCOPE}a`);
    const visible = client(`${SCOPE}b`, { visibilityState: 'visible' });
    const focused = client(`${SCOPE}c`, { focused: true, visibilityState: 'visible' });
    const foreign = client('https://other.test/party/', { focused: true });

    let sw = load([background, foreign, visible, focused]);
    await click(sw, './friends');
    expect(focused.postMessage).toHaveBeenCalled();
    expect(visible.postMessage).not.toHaveBeenCalled();
    expect(foreign.postMessage).not.toHaveBeenCalled();

    sw = load([background, foreign, visible]);
    await click(sw, './friends');
    expect(visible.postMessage).toHaveBeenCalled();
    expect(background.postMessage).not.toHaveBeenCalled();
  });

  it('앱이 열려 있지 않으면 새 창으로 연다', async () => {
    const sw = load([client('https://other.test/')]);
    await click(sw, './friends');
    expect(sw.openWindow).toHaveBeenCalledWith(`${SCOPE}friends`);
  });

  it('앱 범위 밖 주소(다른 출처, 다른 경로)가 담겨 있으면 앱 첫 화면으로 보낸다', async () => {
    for (const url of ['https://evil.test/phish', '/other/path', '//evil.test/x', 'javascript:alert(1)']) {
      const sw = load();
      await click(sw, url);
      expect(sw.openWindow).toHaveBeenCalledWith(SCOPE);
    }
  });

  it('주소가 없으면 앱 첫 화면을 연다', async () => {
    const sw = load();
    await click(sw, undefined);
    expect(sw.openWindow).toHaveBeenCalledWith(SCOPE);
  });
});
