import { expect, test } from '@playwright/test';
import type { Page } from '@playwright/test';
import { mockApi } from './fixtures';

/**
 * 알림을 눌렀을 때 해당 핀으로 이동하는지(딥링크 ?map=&pin=). 알림을 누르는 동작 자체는 브라우저 밖(운영체제)이라 흉내 내기 어려우므로,
 * 앱이 닫혀 있다가 알림 주소로 열리는 경우(주소로 진입)와, 이미 열려 있는 앱이 서비스 워커의 이동 메시지를 받는 경우를 나눠서 확인한다.
 */
const NOW = '2026-10-20T09:00:00Z';
const pin = (id: string, mapId: string, name: string) => ({
  id, mapId, createdBy: 'u-me', authorNickname: '도로롱', authorColor: '#10b981', lat: 37.5563, lng: 126.9236, name, sharedMemo: null,
  status: 'WISH', rating: null, revisitIntent: null, tags: [], visitCount: 0, lastVisitedOn: null, photoCount: 0, commentCount: 1, createdAt: NOW, updatedAt: NOW,
});
const PINS: Record<string, ReturnType<typeof pin>[]> = {
  m1: [pin('p1', 'm1', '골목 국밥집'), pin('p2', 'm1', '다른 핀')],
  m2: [pin('p3', 'm2', '친구 지도의 핀')],
};
const envelope = (data: unknown) => ({ success: true, data, timestamp: NOW });

async function setup(page: Page) {
  await mockApi(page, { loggedIn: true });
  // 핀 목록과 상세에 딸린 목록만 이 시험용으로 덮어쓴다(나중에 등록한 가로채기가 먼저 적용된다)
  await page.route((url) => /^\/party\/api\/v1\/maps\/[^/]+\/pins(\/[^/]+\/(visits|photos|comments))?$/.test(url.pathname), async (route) => {
    const request = route.request();
    if (!['xhr', 'fetch'].includes(request.resourceType())) return route.fallback();
    const match = /\/maps\/([^/]+)\/pins(\/[^/]+\/(visits|photos|comments))?$/.exec(new URL(request.url()).pathname);
    const body = match?.[2] ? [] : (PINS[match?.[1] ?? ''] ?? []);
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope(body)) });
  });
}

const dialog = (page: Page, name: string) => page.getByRole('dialog', { name });

test.describe('알림에서 핀으로 이동', () => {
  test.beforeEach(async ({ page }) => setup(page));

  test('앱이 닫혀 있다가 알림 주소로 열리면 그 핀의 상세가 열리고, 주소에서는 지워진다', async ({ page }) => {
    await page.goto('/party/?map=m1&pin=p1');
    await expect(dialog(page, '골목 국밥집')).toBeVisible();
    await expect(page).toHaveURL(/\/party\/?$/);
  });

  test('다른 지도(공유받은 지도)의 핀도 열린다', async ({ page }) => {
    await page.goto('/party/?map=m2&pin=p3');
    await expect(dialog(page, '친구 지도의 핀')).toBeVisible();
  });

  test('이미 열려 있는 앱이 서비스 워커의 이동 메시지를 받으면 그 핀을 연다', async ({ page }) => {
    await page.goto('/party/');
    await page.waitForLoadState('networkidle');
    await page.evaluate((origin) => {
      navigator.serviceWorker.dispatchEvent(new MessageEvent('message', { data: { type: 'party-open', url: `${origin}/party/?map=m1&pin=p1` } }));
    }, new URL(page.url()).origin);
    await expect(dialog(page, '골목 국밥집')).toBeVisible();
  });

  test('다른 화면(친구 탭)에 있어도 이동 메시지를 받으면 지도로 돌아와 핀을 연다', async ({ page }) => {
    await page.goto('/party/friends');
    await page.waitForLoadState('networkidle');
    await page.evaluate((origin) => {
      navigator.serviceWorker.dispatchEvent(new MessageEvent('message', { data: { type: 'party-open', url: `${origin}/party/?map=m1&pin=p2` } }));
    }, new URL(page.url()).origin);
    await expect(dialog(page, '다른 핀')).toBeVisible();
  });

  test('핀을 연 상태에서 다른 핀의 알림을 받으면 그 핀으로 바뀐다', async ({ page }) => {
    await page.goto('/party/?map=m1&pin=p1');
    await expect(dialog(page, '골목 국밥집')).toBeVisible();
    await page.evaluate((origin) => {
      navigator.serviceWorker.dispatchEvent(new MessageEvent('message', { data: { type: 'party-open', url: `${origin}/party/?map=m1&pin=p2` } }));
    }, new URL(page.url()).origin);
    await expect(dialog(page, '다른 핀')).toBeVisible();
  });
});

test.describe('알림에서 들어왔는데 지도 목록을 처음에 불러오지 못한 경우', () => {
  test('불러오기에 실패했다가 다시 시도하면 알림의 핀이 열린다(실패했다고 알림을 버리지 않는다)', async ({ page }) => {
    await setup(page);
    let healthy = false;
    await page.route((url) => url.pathname === '/party/api/v1/maps', async (route) => {
      if (['xhr', 'fetch'].includes(route.request().resourceType()) && !healthy) {
        return route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ success: false, message: '일시적인 오류예요.' }) });
      }
      return route.fallback();
    });

    await page.goto('/party/?map=m1&pin=p1');
    await expect(page.getByRole('button', { name: '다시 시도' })).toBeVisible();
    await expect(dialog(page, '골목 국밥집')).toHaveCount(0);

    healthy = true;
    await page.getByRole('button', { name: '다시 시도' }).click();

    await expect(dialog(page, '골목 국밥집')).toBeVisible();
  });
});
