import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { beforeEach, describe, expect, it, vi } from 'vitest';

/** public/push-sw.js 를 실제 파일 그대로 가짜 서비스 워커 환경에서 실행해 push·notificationclick 동작을 확인한다. */
const SOURCE = readFileSync(resolve(__dirname, '../../public/push-sw.js'), 'utf8');
const SCOPE = 'https://app.test/party/';

interface FakeClient {
  url: string;
  focus: ReturnType<typeof vi.fn>;
  postMessage: ReturnType<typeof vi.fn>;
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

const client = (url: string): FakeClient => ({ url, focus: vi.fn().mockResolvedValue(undefined), postMessage: vi.fn() });

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
  let sw: ReturnType<typeof load>;
  beforeEach(() => { sw = load(); });

  it('앱이 열려 있으면 그 창을 앞으로 가져오고 새로고침 없이 앱 안에서 이동시킨다', async () => {
    const open = client(`${SCOPE}friends`);
    sw = load([client('https://other.test/'), open]);
    const { event, close, done } = clickEvent('./?map=m1&pin=p1');

    sw.listeners.notificationclick(event);
    await done();

    expect(close).toHaveBeenCalled();
    expect(open.focus).toHaveBeenCalled();
    expect(open.postMessage).toHaveBeenCalledWith({ type: 'party-open', url: `${SCOPE}?map=m1&pin=p1` });
    expect(sw.openWindow).not.toHaveBeenCalled();
  });

  it('앱이 열려 있지 않으면 새 창으로 연다', async () => {
    sw = load([client('https://other.test/')]);
    const { event, done } = clickEvent('./friends');
    sw.listeners.notificationclick(event);
    await done();
    expect(sw.openWindow).toHaveBeenCalledWith(`${SCOPE}friends`);
  });

  it('앱 범위 밖 주소(다른 출처, 다른 경로)가 담겨 있으면 앱 첫 화면으로 보낸다', async () => {
    for (const url of ['https://evil.test/phish', '/other/path', '//evil.test/x', 'javascript:alert(1)']) {
      sw = load();
      const { event, done } = clickEvent(url);
      sw.listeners.notificationclick(event);
      await done();
      expect(sw.openWindow).toHaveBeenCalledWith(SCOPE);
    }
  });

  it('주소가 없으면 앱 첫 화면을 연다', async () => {
    const { event, done } = clickEvent(undefined);
    sw.listeners.notificationclick(event);
    await done();
    expect(sw.openWindow).toHaveBeenCalledWith(SCOPE);
  });
});
