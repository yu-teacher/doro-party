import { expect, test, type Page } from '@playwright/test';
import { auditMobile, formatViolations } from './mobileAudit';
import { mockApi } from './fixtures';

/** 허브가 제공하는 서비스 목록(/services.json)을 가짜로 대체한다. */
async function mockServices(page: Page, body: unknown, status = 200) {
  await page.route('**/services.json', (route) =>
    route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) }),
  );
}

const manifest = {
  version: 1,
  services: [
    { id: 'blog', name: 'DORO.log', desc: '개발자 오픈 기술 블로그', href: '/blog/' },
    { id: 'party', name: '도로 파티', desc: '친구들과 지도에 핀 모으기', href: '/party/' },
    { id: 'games', name: '도로 게임 천국', desc: '웹 미니게임', href: '/games/' },
    { id: 'interview', name: '도로 면접', desc: '면접 질문 은행', href: '/interview/' },
  ],
};

test.describe('파티: 서비스 목록 버튼', () => {
  test.beforeEach(async ({ page }) => mockApi(page, { loggedIn: false }));

  test('누르면 도로 홈과 서비스 링크가 뜨고 현재 서비스가 표시되며 화면 안에 들어온다', async ({ page }) => {
    await mockServices(page, manifest);
    await page.goto('/party/');
    await page.getByRole('button', { name: '도로 서비스 목록' }).click();

    const nav = page.getByRole('navigation', { name: '도로 서비스 목록' });
    await expect(nav.getByRole('link', { name: /도로 홈/ })).toHaveAttribute('href', '/');
    await expect(nav.getByRole('link', { name: /도로 면접/ })).toHaveAttribute('href', '/interview/');
    await expect(nav.locator('[aria-current="page"]')).toContainText('도로 파티');

    await page.waitForLoadState('networkidle');
    const box = await nav.boundingBox();
    const viewport = page.viewportSize();
    expect(box && viewport && box.x >= 0 && box.x + box.width <= viewport.width, '목록 창이 화면 밖으로 나갔다').toBe(true);
    const violations = await auditMobile(page);
    expect(violations, `모바일에서 깨지는 곳이 있습니다:\n${formatViolations(violations)}`).toEqual([]);
  });

  test('목록을 못 읽으면 도로 홈 링크와 안내만 보인다', async ({ page }) => {
    await mockServices(page, { error: 'down' }, 500);
    await page.goto('/party/');
    await page.getByRole('button', { name: '도로 서비스 목록' }).click();
    const nav = page.getByRole('navigation', { name: '도로 서비스 목록' });
    await expect(nav.getByRole('link', { name: /도로 홈/ })).toBeVisible();
    await expect(nav.getByText('불러오지 못했어요')).toBeVisible();
  });

  test('Esc 로 닫는다', async ({ page }) => {
    await mockServices(page, manifest);
    await page.goto('/party/');
    const button = page.getByRole('button', { name: '도로 서비스 목록' });
    await button.click();
    await expect(button).toHaveAttribute('aria-expanded', 'true');
    await page.keyboard.press('Escape');
    await expect(button).toHaveAttribute('aria-expanded', 'false');
  });
});
