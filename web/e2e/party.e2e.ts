import { expect, test, type Page } from '@playwright/test';
import { auditMobile, formatViolations, type AuditOptions } from './mobileAudit';
import { mockApi } from './fixtures';

/** 화면이 자리 잡을 때까지 기다린 뒤 모바일 문제를 모두 모아 한 번에 보여 준다. */
async function expectMobileFriendly(page: Page, options: AuditOptions = {}) {
  await page.waitForLoadState('networkidle');
  await page.evaluate(() => document.fonts.ready);
  const violations = await auditMobile(page, options);
  expect(violations, `모바일에서 깨지는 곳이 있습니다:\n${formatViolations(violations)}`).toEqual([]);
}

test.describe('파티: 비로그인', () => {
  test.beforeEach(async ({ page }) => mockApi(page, { loggedIn: false }));

  test('지도 첫 화면', async ({ page }) => {
    await page.goto('/party/');
    await expect(page.getByText('내 지도를 만들어 보세요')).toBeVisible();
    await expectMobileFriendly(page);
  });

  test('친구 화면(로그인 안내)', async ({ page }) => {
    await page.goto('/party/friends');
    await expect(page.getByRole('link', { name: /로그인/ }).first()).toBeVisible();
    await expectMobileFriendly(page);
  });
});

test.describe('파티: 로그인', () => {
  test.beforeEach(async ({ page }) => mockApi(page, { loggedIn: true }));

  test('지도 첫 화면', async ({ page }) => {
    await page.goto('/party/');
    await page.waitForLoadState('networkidle');
    await expectMobileFriendly(page);
  });

  test('친구', async ({ page }) => {
    await page.goto('/party/friends');
    await expect(page.getByText(/친구닉네임/).first()).toBeVisible();
    await expectMobileFriendly(page);
  });

  test('친구의 지도', async ({ page }) => {
    await page.goto('/party/friends/maps');
    await expect(page.getByText(/친구닉네임/).first()).toBeVisible();
    await expectMobileFriendly(page);
  });

  test('친구 초대 링크', async ({ page }) => {
    await page.goto('/party/invite/ABCD1234');
    await expect(page.getByText(/초대한사람/).first()).toBeVisible();
    await expectMobileFriendly(page);
  });

  test('모임 목록', async ({ page }) => {
    await page.goto('/party/groups');
    await expect(page.getByText(/모임 이름이 아주 긴 경우/).first()).toBeVisible();
    await expectMobileFriendly(page);
  });

  test('모임 상세', async ({ page }) => {
    await page.goto('/party/groups/g1');
    await expect(page.getByText(/멤버닉네임/).first()).toBeVisible();
    await expectMobileFriendly(page);
  });

  test('모임 초대 링크', async ({ page }) => {
    await page.goto('/party/join/GROUP1234');
    await expect(page.getByText(/모임장/).first()).toBeVisible();
    await expectMobileFriendly(page);
  });
});
