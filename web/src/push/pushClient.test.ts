import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as pushApi from '../api/pushApi';
import {
  detachThisDevice, detectPushSupport, disableThisDevice, enableThisDevice, syncThisDevice, toRouterPath, urlBase64ToBytes,
} from './pushClient';

vi.mock('../api/pushApi');
const api = vi.mocked(pushApi);

const PUBLIC_KEY = 'BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4';

interface FakeSubscription {
  endpoint: string;
  options: { applicationServerKey: ArrayBuffer | null };
  toJSON: () => { endpoint?: string; keys?: { p256dh?: string; auth?: string } };
  unsubscribe: ReturnType<typeof vi.fn>;
}

function subscription(key: string | null, json?: ReturnType<FakeSubscription['toJSON']>): FakeSubscription {
  return {
    endpoint: 'https://fcm.googleapis.com/fcm/send/abc',
    options: { applicationServerKey: key === null ? null : urlBase64ToBytes(key).buffer as ArrayBuffer },
    toJSON: () => json ?? { endpoint: 'https://fcm.googleapis.com/fcm/send/abc', keys: { p256dh: 'P', auth: 'A' } },
    unsubscribe: vi.fn().mockResolvedValue(true),
  };
}

let existing: FakeSubscription | null;
const subscribeMock = vi.fn();
let permission: NotificationPermission;
const requestPermission = vi.fn();

const originals = new Map<string, PropertyDescriptor | undefined>();
function define(target: object, name: string, value: unknown) {
  originals.set(`${target === navigator ? 'nav' : 'win'}.${name}`, Object.getOwnPropertyDescriptor(target, name));
  Object.defineProperty(target, name, { configurable: true, writable: true, value });
}

beforeEach(() => {
  vi.resetAllMocks();
  existing = null;
  permission = 'default';
  subscribeMock.mockImplementation(async () => subscription(PUBLIC_KEY));
  requestPermission.mockImplementation(async () => { permission = 'granted'; return permission; });
  define(navigator, 'userAgent', 'Mozilla/5.0 (X11; Linux x86_64) Chrome/130');
  define(navigator, 'maxTouchPoints', 0);
  define(navigator, 'standalone', undefined);
  define(navigator, 'serviceWorker', { ready: Promise.resolve({ pushManager: { getSubscription: async () => existing, subscribe: subscribeMock } }) });
  define(window, 'PushManager', class {});
  define(window, 'matchMedia', () => ({ matches: false }));
  const NotificationStub = function Notification() { /* 브라우저 API 대역 */ };
  Object.defineProperty(NotificationStub, 'permission', { get: () => permission });
  Object.defineProperty(NotificationStub, 'requestPermission', { value: requestPermission });
  define(window, 'Notification', NotificationStub);
  api.saveSubscription.mockResolvedValue(undefined);
  api.deleteSubscription.mockResolvedValue(undefined);
});

afterEach(() => {
  for (const [key, descriptor] of originals) {
    const target = key.startsWith('nav.') ? navigator : window;
    const name = key.slice(4);
    if (descriptor) {
      Object.defineProperty(target, name, descriptor);
    } else {
      Reflect.deleteProperty(target, name);
    }
  }
  originals.clear();
});

describe('지원 여부', () => {
  it('서비스 워커·PushManager·Notification 이 있으면 켤 수 있다', () => {
    expect(detectPushSupport()).toBe('supported');
  });

  it.each([['serviceWorker'], ['PushManager'], ['Notification']])('%s 가 없으면 지원하지 않는다', (missing) => {
    const target = missing === 'serviceWorker' ? navigator : window;
    Reflect.deleteProperty(target, missing);
    expect(detectPushSupport()).toBe('unsupported');
  });

  it('아이폰 사파리 탭에서는 홈 화면에 추가해야 한다(PushManager 가 있어도)', () => {
    define(navigator, 'userAgent', 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 Version/17.4 Mobile/15E148 Safari/604.1');
    expect(detectPushSupport()).toBe('needs-install');
  });

  it('아이폰에서 홈 화면에 추가한 앱(standalone)이면 켤 수 있다', () => {
    define(navigator, 'userAgent', 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148');
    define(navigator, 'standalone', true);
    expect(detectPushSupport()).toBe('supported');
  });

  it('아이패드(데스크톱 사파리로 보이지만 터치가 된다)도 설치 전에는 설치가 필요하다', () => {
    define(navigator, 'userAgent', 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Version/17.4 Safari/605.1.15');
    define(navigator, 'maxTouchPoints', 5);
    expect(detectPushSupport()).toBe('needs-install');
    define(window, 'matchMedia', () => ({ matches: true }));
    expect(detectPushSupport()).toBe('supported');
  });
});

describe('urlBase64ToBytes', () => {
  it('URL 안전 Base64 를 바이트로 바꾼다(패딩 없음, - 와 _ 포함)', () => {
    expect([...urlBase64ToBytes('AQID')]).toEqual([1, 2, 3]);
    expect([...urlBase64ToBytes('-_8')]).toEqual([251, 255]);
    expect(urlBase64ToBytes(PUBLIC_KEY)).toHaveLength(65);
    expect(urlBase64ToBytes(PUBLIC_KEY)[0]).toBe(4);
  });
});

describe('이 기기의 알림 켜기', () => {
  it('권한을 거부하면 안내 오류를 던지고 아무것도 등록하지 않는다', async () => {
    requestPermission.mockResolvedValue('denied');
    await expect(enableThisDevice(PUBLIC_KEY)).rejects.toThrow('알림 권한이 허용되지 않았어요');
    expect(subscribeMock).not.toHaveBeenCalled();
    expect(api.saveSubscription).not.toHaveBeenCalled();
  });

  it('권한을 허용하면 서버 공개 키로 구독을 만들고(userVisibleOnly) 서버에 등록한다', async () => {
    await enableThisDevice(PUBLIC_KEY);

    expect(subscribeMock).toHaveBeenCalledWith({ userVisibleOnly: true, applicationServerKey: urlBase64ToBytes(PUBLIC_KEY) });
    expect(api.saveSubscription).toHaveBeenCalledWith({ endpoint: 'https://fcm.googleapis.com/fcm/send/abc', keys: { p256dh: 'P', auth: 'A' } });
  });

  it('이미 같은 키의 구독이 있으면 새로 만들지 않고 서버에만 등록한다', async () => {
    existing = subscription(PUBLIC_KEY);
    await enableThisDevice(PUBLIC_KEY);
    expect(subscribeMock).not.toHaveBeenCalled();
    expect(api.saveSubscription).toHaveBeenCalledTimes(1);
  });

  it('서버의 푸시 키가 바뀌었으면 옛 구독을 해제하고 새로 만든다', async () => {
    const otherKey = 'BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8';
    const old = subscription(otherKey);
    existing = old;
    await enableThisDevice(PUBLIC_KEY);
    expect(old.unsubscribe).toHaveBeenCalled();
    expect(subscribeMock).toHaveBeenCalledTimes(1);
  });

  it('브라우저가 구독 정보(주소·키)를 주지 않으면 오류를 던진다', async () => {
    subscribeMock.mockResolvedValue(subscription(PUBLIC_KEY, { endpoint: 'https://x', keys: { p256dh: 'P' } }));
    await expect(enableThisDevice(PUBLIC_KEY)).rejects.toThrow('구독 정보를 읽지 못했어요');
    expect(api.saveSubscription).not.toHaveBeenCalled();
  });
});

describe('이 기기의 알림 끄기·떼기·맞추기', () => {
  it('끄기: 서버에서 지운 뒤 브라우저 구독을 해제한다. 서버 삭제가 실패하면 브라우저 구독은 그대로 둔다', async () => {
    const current = subscription(PUBLIC_KEY);
    existing = current;
    await disableThisDevice();
    expect(api.deleteSubscription).toHaveBeenCalledWith(current.endpoint);
    expect(current.unsubscribe).toHaveBeenCalled();

    const kept = subscription(PUBLIC_KEY);
    existing = kept;
    api.deleteSubscription.mockRejectedValue(new Error('서버 오류'));
    await expect(disableThisDevice()).rejects.toThrow('서버 오류');
    expect(kept.unsubscribe).not.toHaveBeenCalled();
  });

  it('끄기: 구독이 없으면 아무것도 하지 않는다', async () => {
    await disableThisDevice();
    expect(api.deleteSubscription).not.toHaveBeenCalled();
  });

  it('로그아웃 전에 떼기: 서버에서만 지우고 브라우저 구독은 남긴다', async () => {
    permission = 'granted';
    const current = subscription(PUBLIC_KEY);
    existing = current;
    await detachThisDevice();
    expect(api.deleteSubscription).toHaveBeenCalledWith(current.endpoint);
    expect(current.unsubscribe).not.toHaveBeenCalled();
  });

  it('떼기: 권한이 없거나 지원하지 않으면 아무것도 하지 않는다', async () => {
    existing = subscription(PUBLIC_KEY);
    await detachThisDevice();   // 권한 default
    Reflect.deleteProperty(window, 'PushManager');
    permission = 'granted';
    await detachThisDevice();
    expect(api.deleteSubscription).not.toHaveBeenCalled();
  });

  it('맞추기: 권한이 허용되지 않았거나 구독이 없으면 켜지지 않은 상태다(권한을 새로 묻지 않는다)', async () => {
    expect(await syncThisDevice(PUBLIC_KEY)).toBe(false);
    permission = 'granted';
    expect(await syncThisDevice(PUBLIC_KEY)).toBe(false);
    expect(requestPermission).not.toHaveBeenCalled();
    expect(api.saveSubscription).not.toHaveBeenCalled();
  });

  it('맞추기: 같은 키의 구독이 있으면 서버에 다시 등록하고 켜진 상태다', async () => {
    permission = 'granted';
    existing = subscription(PUBLIC_KEY);
    expect(await syncThisDevice(PUBLIC_KEY)).toBe(true);
    expect(api.saveSubscription).toHaveBeenCalledTimes(1);
    expect(subscribeMock).not.toHaveBeenCalled();
  });

  it('맞추기: 키가 다르면 새로 구독한다', async () => {
    permission = 'granted';
    existing = subscription('BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8');
    expect(await syncThisDevice(PUBLIC_KEY)).toBe(true);
    expect(subscribeMock).toHaveBeenCalledTimes(1);
  });
});

describe('toRouterPath', () => {
  const origin = 'https://app.test';

  it('앱 범위 안의 주소를 라우터 경로(검색어 포함)로 바꾼다', () => {
    expect(toRouterPath('https://app.test/party/?map=m1&pin=p1', origin, '/party')).toBe('/?map=m1&pin=p1');
    expect(toRouterPath('https://app.test/party/friends', origin, '/party')).toBe('/friends');
    expect(toRouterPath('https://app.test/party', origin, '/party')).toBe('/');
  });

  it('다른 출처, 범위 밖 경로, 접두사만 같은 경로, 잘못된 주소는 null', () => {
    expect(toRouterPath('https://evil.test/party/x', origin, '/party')).toBeNull();
    expect(toRouterPath('https://app.test/other', origin, '/party')).toBeNull();
    expect(toRouterPath('https://app.test/partyx/y', origin, '/party')).toBeNull();
    expect(toRouterPath('http://[bad', origin, '/party')).toBeNull();
  });
});
