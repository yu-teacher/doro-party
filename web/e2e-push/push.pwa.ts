import { expect, test } from '@playwright/test';
import type { CDPSession, Page, Route } from '@playwright/test';

const API = '/party/api/v1';
const NOW = '2026-10-19T09:00:00Z';
const ME = { id: 'u-me', username: 'doro', nickname: '도로롱', color: '#10b981' };
/** 서버의 VAPID 공개 키 모양(비압축 P-256, base64url 65바이트). 이 시험에서는 가짜 구독을 쓰므로 값 자체는 의미가 없다. */
const PUBLIC_KEY = 'BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4';
const ENDPOINT = 'https://fcm.googleapis.com/fcm/send/e2e-device';

const envelope = (data: unknown) => ({ success: true, data, timestamp: NOW });
const json = (route: Route, body: unknown, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });

interface Calls {
  saved: unknown[];
  deleted: unknown[];
  unreadFetches: number;
  signedOut: number;
}

/** 파티 서버 응답을 가짜로 대체한다(푸시 설정 포함). 브라우저가 서버로 보낸 호출을 기록해 돌려준다. */
async function mockServer(page: Page): Promise<Calls> {
  const calls: Calls = { saved: [], deleted: [], unreadFetches: 0, signedOut: 0 };
  await page.route((url) => url.pathname.startsWith(API), async (route) => {
    const request = route.request();
    if (!['xhr', 'fetch'].includes(request.resourceType())) return route.fallback();
    const path = new URL(request.url()).pathname.slice(API.length);
    const method = request.method();
    if (path === '/bff/session') return json(route, envelope({ authenticated: true, user: ME }));
    if (path === '/bff/logout') { calls.signedOut += 1; return json(route, envelope(null)); }
    if (path === '/maps') return json(route, envelope([]));
    if (path === '/comments/unread') { calls.unreadFetches += 1; return json(route, envelope([])); }
    if (path === '/push/config') return json(route, envelope({ enabled: true, publicKey: PUBLIC_KEY }));
    if (path === '/push/preferences') return json(route, envelope({ comments: true, friendRequests: true, shares: true }));
    if (path === '/push/subscription' && method === 'PUT') { calls.saved.push(request.postDataJSON()); return json(route, envelope(null)); }
    if (path === '/push/subscription' && method === 'DELETE') { calls.deleted.push(request.postDataJSON()); return json(route, envelope(null)); }
    return json(route, { success: false, code: 'NOT_MOCKED', message: `${method} ${path}` }, 404);
  });
  return calls;
}

/**
 * Playwright 의 Chromium 에는 구글 푸시 서비스 연결이 없어 실제 구독을 만들 수 없다. 그래서 구독 생성(PushManager.subscribe)만
 * 가짜로 바꾸고, 그 외(서비스 워커 등록, 권한, 알림 표시, 서비스 워커의 push 처리)는 실제 브라우저가 처리한다.
 */
async function fakePushSubscription(page: Page) {
  await page.addInitScript(({ endpoint, key }) => {
    const toBytes = (value: string) => Uint8Array.from(atob(value.replace(/-/g, '+').replace(/_/g, '/')), (c) => c.charCodeAt(0));
    let current: unknown = null;
    const make = () => ({
      endpoint,
      options: { applicationServerKey: toBytes(key).buffer },
      toJSON: () => ({ endpoint, keys: { p256dh: key, auth: 'AAAAAAAAAAAAAAAAAAAAAA' } }),
      unsubscribe: async () => { current = null; return true; },
    });
    PushManager.prototype.getSubscription = async () => current as PushSubscription | null;
    PushManager.prototype.subscribe = async () => { current = make(); return current as PushSubscription; };
  }, { endpoint: ENDPOINT, key: PUBLIC_KEY });
}

async function openSettings(page: Page) {
  await page.getByRole('button', { name: '알림 설정' }).click();
  await expect(page.getByRole('dialog', { name: '알림' })).toBeVisible();
}

/** 서비스 워커가 활성화돼 이 화면을 통제할 때까지 기다린다(두 번째 불러오기부터 통제한다). */
async function waitForControlledPage(page: Page) {
  await page.goto('/party/');
  await page.evaluate(() => navigator.serviceWorker.ready);
  await page.reload();
  await page.waitForFunction(() => navigator.serviceWorker.controller !== null);
}

test.describe('푸시 알림(운영 빌드, 실제 서비스 워커)', () => {
  test('빌드된 서비스 워커가 푸시 처리 코드를 불러온다', async ({ page, request }) => {
    const sw = await (await request.get('/party/sw.js')).text();
    expect(sw).toContain('push-sw.js');
    expect(sw).toMatch(/skipWaiting\(\)/);       // 업데이트가 대기하지 않게(회귀 방지)
    const pushSw = await request.get('/party/push-sw.js');
    expect(pushSw.status()).toBe(200);
    expect(pushSw.headers()['content-type']).toContain('javascript');
  });

  test('알림 켜기: 권한 허용 → 구독 → 서버 등록, 설정 시트가 켜짐 상태로 바뀐다', async ({ page }) => {
    const calls = await mockServer(page);
    await fakePushSubscription(page);
    await waitForControlledPage(page);

    await openSettings(page);
    await page.getByRole('button', { name: '이 기기에서 알림 받기' }).click();

    await expect(page.getByText('이 기기에서 알림을 받고 있어요.')).toBeVisible();
    expect(calls.saved).toEqual([{ endpoint: ENDPOINT, keys: { p256dh: PUBLIC_KEY, auth: 'AAAAAAAAAAAAAAAAAAAAAA' } }]);
    await expect(page.getByRole('checkbox')).toHaveCount(3);
    await page.screenshot({ path: 'test-results/push-settings-on.png' });
  });

  test('알림 끄기: 서버에서 지우고 꺼진 상태로 돌아간다', async ({ page }) => {
    const calls = await mockServer(page);
    await fakePushSubscription(page);
    await waitForControlledPage(page);
    await openSettings(page);
    await page.getByRole('button', { name: '이 기기에서 알림 받기' }).click();
    await expect(page.getByText('이 기기에서 알림을 받고 있어요.')).toBeVisible();

    await page.getByRole('button', { name: '이 기기 알림 끄기' }).click();

    await expect(page.getByRole('button', { name: '이 기기에서 알림 받기' })).toBeVisible();
    expect(calls.deleted).toEqual([{ endpoint: ENDPOINT }]);
  });

  test('로그아웃하면 서버에서 이 기기를 먼저 뗀다(같은 기기로 다른 사람이 로그인해도 이전 계정 알림이 오지 않게)', async ({ page }) => {
    const calls = await mockServer(page);
    await fakePushSubscription(page);
    await waitForControlledPage(page);
    await openSettings(page);
    await page.getByRole('button', { name: '이 기기에서 알림 받기' }).click();
    await expect(page.getByText('이 기기에서 알림을 받고 있어요.')).toBeVisible();
    await page.getByRole('button', { name: '닫기' }).click();

    await page.getByRole('button', { name: '로그아웃' }).click();

    await expect.poll(() => calls.signedOut).toBe(1);
    expect(calls.deleted).toEqual([{ endpoint: ENDPOINT }]);
  });

  test('서비스 워커가 push 를 받으면 알림을 띄우고, 열려 있는 앱은 새 댓글 표시를 바로 갱신한다', async ({ page, context }) => {
    const calls = await mockServer(page);
    await waitForControlledPage(page);
    const before = calls.unreadFetches;

    const devtools = await PushDevTools.attach(context, page);
    await devtools.deliver({ title: '골목 국밥집', body: '민준: 웨이팅 길어요', url: './?map=m1&pin=p1', tag: 'comment:p1' });

    await expect.poll(() => notifications(page)).toEqual([{ title: '골목 국밥집', body: '민준: 웨이팅 길어요', tag: 'comment:p1' }]);
    await expect.poll(() => calls.unreadFetches).toBeGreaterThan(before);
    await devtools.detach();
  });

  test('같은 tag 의 알림은 하나로 합쳐진다', async ({ page, context }) => {
    await mockServer(page);
    await waitForControlledPage(page);

    const devtools = await PushDevTools.attach(context, page);
    await devtools.deliver({ title: '핀', body: '첫 번째', url: './', tag: 'comment:p9' });
    await expect.poll(() => notifications(page)).toHaveLength(1);
    await devtools.deliver({ title: '핀', body: '두 번째', url: './', tag: 'comment:p9' });

    await expect.poll(async () => (await notifications(page)).map((n) => n.body)).toEqual(['두 번째']);
    await devtools.detach();
  });

  test('내용이 깨진 푸시도 알림은 띄운다', async ({ page, context }) => {
    await mockServer(page);
    await waitForControlledPage(page);

    const devtools = await PushDevTools.attach(context, page);
    await devtools.deliver('this is not json');

    await expect.poll(async () => (await notifications(page)).map((n) => n.title)).toEqual(['도로 파티']);
    await devtools.detach();
  });
});

async function notifications(page: Page): Promise<Array<{ title: string; body: string; tag: string }>> {
  return page.evaluate(async () => {
    const registration = await navigator.serviceWorker.ready;
    return (await registration.getNotifications()).map((n) => ({ title: n.title, body: n.body, tag: n.tag }));
  });
}

/**
 * DevTools 프로토콜로 서비스 워커에 푸시를 직접 전달한다(실제 푸시 서비스 없이 서비스 워커의 push 처리를 시험).
 * ServiceWorker 도메인을 한 번만 켜고, 그때 알려 주는 등록 정보(registrationId)를 기억해 둔다.
 */
class PushDevTools {
  private constructor(private readonly cdp: CDPSession, private readonly page: Page, private readonly registrationId: Promise<string>) {}

  static async attach(context: { newCDPSession: (page: Page) => Promise<CDPSession> }, page: Page): Promise<PushDevTools> {
    const cdp = await context.newCDPSession(page);
    const registrationId = new Promise<string>((resolve) => {
      cdp.on('ServiceWorker.workerRegistrationUpdated', (event) => {
        const found = event.registrations.find((r: { scopeURL: string }) => r.scopeURL.endsWith('/party/'));
        if (found) resolve(found.registrationId);
      });
    });
    await cdp.send('ServiceWorker.enable');
    return new PushDevTools(cdp, page, registrationId);
  }

  async deliver(payload: unknown): Promise<void> {
    await this.cdp.send('ServiceWorker.deliverPushMessage', {
      origin: new URL(this.page.url()).origin,
      registrationId: await this.registrationId,
      data: typeof payload === 'string' ? payload : JSON.stringify(payload),
    });
  }

  detach(): Promise<void> {
    return this.cdp.detach();
  }
}
