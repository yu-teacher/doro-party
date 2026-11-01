import { expect, test } from '@playwright/test';
import type { Page } from '@playwright/test';
import { mockApi } from './fixtures';

const NOW = '2026-10-20T09:00:00Z';
type TestPin = ReturnType<typeof pin>;
const pin = (i: number, over: Partial<{ lat: number; lng: number; tags: string[]; status: 'WISH' | 'VISITED'; name: string }> = {}) => ({
  id: `p${i}`, mapId: 'm1', createdBy: 'u-me', authorNickname: '도로롱', authorColor: '#10b981',
  lat: 37.5 + i * 0.01, lng: 126.9, name: `핀 ${i}`, sharedMemo: null, status: 'WISH' as 'WISH' | 'VISITED', rating: null, revisitIntent: null,
  tags: [] as string[], visitCount: 0, lastVisitedOn: null, photoCount: 0, commentCount: 0, createdAt: NOW, updatedAt: NOW, ...over,
});

async function setup(page: Page, pins: TestPin[]) {
  await mockApi(page, { loggedIn: true });
  await page.route((url) => /^\/party\/api\/v1\/maps\/m1\/pins$/.test(url.pathname), (route) =>
    ['xhr', 'fetch'].includes(route.request().resourceType())
      ? route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data: pins, timestamp: NOW }) })
      : route.fallback());
}

/** 지도에 그려진 핀 수: 마커 하나는 1, 묶음은 그 안의 핀 수 */
const shownPins = async (page: Page) => {
  const singles = await page.locator('.fake-marker').count();
  const clusters = await page.locator('.fake-cluster').evaluateAll((els) => els.map((el) => Number(el.textContent)));
  return singles + clusters.reduce((a, b) => a + b, 0);
};
const fits = (page: Page) => page.evaluate(() => (window as unknown as { __fits?: number }).__fits ?? 0);
const chip = (page: Page, name: string) => page.getByRole('button', { name: `#${name}` });

test.describe('태그 필터', () => {
  test('서로 먼 핀: 태그를 누르면 그 태그의 핀만, 다시 누르면 전부, 다른 태그로 옮기면 그 태그의 핀만', async ({ page }) => {
    await setup(page, [pin(1, { tags: ['cafe'] }), pin(2, { tags: ['cafe', 'bar'] }), pin(3, { tags: ['bar'] }), pin(4)]);
    await page.goto('/party/');
    await expect.poll(() => shownPins(page)).toBe(4);
    await chip(page, 'cafe').click();
    await expect.poll(() => shownPins(page)).toBe(2);
    await chip(page, 'cafe').click();
    await expect.poll(() => shownPins(page)).toBe(4);
    await chip(page, 'cafe').click();
    await chip(page, 'bar').click();
    await expect.poll(() => shownPins(page)).toBe(2);
    await chip(page, 'bar').click();
    await expect.poll(() => shownPins(page)).toBe(4);
  });

  test('핀이 많아 묶음이 생겨도(41개) 누르고 해제하고 옮길 때마다 보이는 핀 수가 맞는다', async ({ page }) => {
    const pins = Array.from({ length: 41 }, (_, i) =>
      pin(i, { lat: 37.5 + Math.floor(i / 7) * 0.0003, lng: 126.9 + (i % 7) * 0.0003, tags: [i % 2 === 0 ? 'cafe' : 'bar', ...(i % 5 === 0 ? ['night'] : [])] }));
    await setup(page, pins);
    await page.goto('/party/');
    await expect.poll(() => shownPins(page)).toBe(41);
    const has = (tag: string) => pins.filter((p) => p.tags.includes(tag)).length;
    for (const [name, expected] of [['cafe', has('cafe')], ['cafe', 41], ['cafe', has('cafe')], ['bar', has('bar')], ['night', has('night')], ['night', 41]] as const) {
      await chip(page, name).click();
      await expect.poll(() => shownPins(page)).toBe(expected);
    }
  });

  test('필터를 바꾸면 지도가 보이는 핀에 맞춰진다(맞는 핀이 화면 밖에 있어 빈 것처럼 보이지 않게)', async ({ page }) => {
    await setup(page, [pin(1, { tags: ['cafe'] }), pin(2, { tags: ['cafe'] }), pin(3, { tags: ['bar'] })]);
    await page.goto('/party/');
    await expect.poll(() => shownPins(page)).toBe(3);
    const before = await fits(page);
    await chip(page, 'bar').click();
    await expect.poll(() => fits(page)).toBeGreaterThan(before);
    const afterTag = await fits(page);
    await chip(page, 'bar').click(); // 해제하면 전체에 다시 맞춘다
    await expect.poll(() => fits(page)).toBeGreaterThan(afterTag);
  });

  test('상태와 태그가 겹쳐 결과가 없으면 안내와 "필터 해제"가 나오고, 누르면 전부 돌아온다', async ({ page }) => {
    await setup(page, [pin(1, { tags: ['cafe'], status: 'WISH' }), pin(2, { tags: ['bar'], status: 'VISITED' })]);
    await page.goto('/party/');
    await expect.poll(() => shownPins(page)).toBe(2);
    await expect(page.getByRole('button', { name: /필터 해제/ })).toHaveCount(0); // 필터가 없을 때는 안 보인다
    await chip(page, 'cafe').click();
    await page.getByRole('button', { name: '다녀왔어요' }).click();
    await expect.poll(() => shownPins(page)).toBe(0);
    await expect(page.getByText('조건에 맞는 핀이 없어요')).toBeVisible();
    await page.getByRole('button', { name: '필터 해제', exact: true }).click();
    await expect.poll(() => shownPins(page)).toBe(2);
    await expect(page.getByText('조건에 맞는 핀이 없어요')).toHaveCount(0);
  });

  test('필터 해제 칩은 상태와 태그를 한 번에 풀고 보이는 개수를 알려 준다', async ({ page }) => {
    await setup(page, [pin(1, { tags: ['cafe'], status: 'WISH' }), pin(2, { tags: ['cafe'], status: 'VISITED' }), pin(3, { tags: ['bar'], status: 'WISH' })]);
    await page.goto('/party/');
    await chip(page, 'cafe').click();
    await page.getByRole('button', { name: '가고 싶어요' }).click();
    await expect(page.getByRole('button', { name: '필터 해제 · 1/3개' })).toBeVisible();
    await page.getByRole('button', { name: /필터 해제/ }).click();
    await expect.poll(() => shownPins(page)).toBe(3);
    await expect(page.getByRole('button', { name: '#cafe' })).toHaveAttribute('aria-pressed', 'false');
  });
});
