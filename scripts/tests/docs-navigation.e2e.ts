import { test, beforeEach, afterEach } from '@e2e-dev/web';
import { expect } from 'e2e';

beforeEach(async ({ browser }) => {
  await browser.addInitScript(() => {
    window['documentationErrors'] = [];
    window.addEventListener('error', event => window['documentationErrors'].push(event.message));
    window.addEventListener('unhandledrejection', event => window['documentationErrors'].push(String(event.reason)));
  });
});
afterEach(async ({ browser }) => {
  expect(await browser.evaluate(() => window['documentationErrors'] || [])).toEqual([]);
});

test('project site selects current version and keeps Korean document and heading', async ({ app, screen, browser }) => {
  await app.open('/searchable-jpa/snapshot/#/ko/basic-usage.md?id=usage');
  const select = screen.getByRole('combobox', '버전');
  await expect(select).toHaveValue('snapshot');
  await select.selectOption('1.10.0');
  await expect(browser).toHaveURL('/searchable-jpa/1.10.0/#/ko/basic-usage.md?id=usage');
  await expect(screen.getByRole('combobox', '버전')).toHaveValue('1.10.0');
});

test('missing old page falls back to the same language introduction', async ({ app, screen, browser }) => {
  await app.open('/searchable-jpa/snapshot/#/ko/advanced-features.md');
  await expect(screen.getByRole('combobox', '버전')).toBeEnabled();
  await screen.getByRole('combobox', '버전').selectOption('1.9.0');
  await expect(browser).toHaveURL('/searchable-jpa/1.9.0/#/ko/');
});

test('root deployment and explicit index support release to development navigation', async ({ app, screen, browser }) => {
  await app.open('/1.10.0/index.html#/en/basic-usage.md');
  await expect(screen.getByRole('combobox', 'Version')).toHaveValue('1.10.0');
  await screen.getByRole('combobox', 'Version').selectOption({ value: 'snapshot' });
  await expect(browser).toHaveURL('/snapshot/#/en/basic-usage.md');
});

test('manifest failure has accessible retry and recovers', async ({ app, screen, browser }) => {
  let failures = 1;
  await browser.route('**/versions.json', route => {
    if (failures-- > 0) return route.fulfill({ status: 503, body: 'unavailable' });
    return route.continue();
  });
  await app.open('/searchable-jpa/snapshot/#/en/basic-usage.md');
  await expect(screen.getByRole('combobox', 'Version')).toBeDisabled();
  await screen.getByRole('button', 'Retry').tap();
  await expect(screen.getByRole('combobox', 'Version')).toBeEnabled();
});

test('failed document request keeps the current version and permits retry', async ({ app, screen, browser }) => {
  await browser.route('**/1.10.0/en/basic-usage.md', route => route.fulfill({ status: 503, body: 'unavailable' }));
  await app.open('/searchable-jpa/snapshot/#/en/basic-usage.md');
  const select = screen.getByRole('combobox', 'Version');
  await expect(select).toBeEnabled();
  await select.selectOption('1.10.0');
  await expect(screen.getByRole('status')).toContainText('Unable to open');
  await expect(select).toHaveValue('snapshot');
  await expect(select).toBeEnabled();
  await expect(browser).toHaveURL('/searchable-jpa/snapshot/#/en/basic-usage.md');
});

test('language changes keep one selector and local previews omit it', async ({ app, screen, browser }) => {
  await app.open('/searchable-jpa/snapshot/#/en/basic-usage.md');
  await expect(screen.getByRole('combobox', 'Version')).toBeEnabled();
  await browser.goto('/searchable-jpa/snapshot/#/ko/basic-usage.md');
  await expect(screen.getByRole('combobox', '버전')).toHaveCount(1);
  await app.open('/preview/#/en/basic-usage.md');
  await expect(screen.getByRole('heading', 'Basic Usage')).toBeVisible();
  await expect(screen.getByRole('combobox', 'Version')).toHaveCount(0);
});

test('documentation figures load under the versioned project path', async ({ app, screen, browser }) => {
  await app.open('/searchable-jpa/snapshot/#/ko/two-phase-query-optimization.md');
  await expect(screen.getByRole('heading', '2단계 쿼리 최적화')).toBeVisible();
  const failed = await browser.evaluate(async () => {
    const images = Array.from(document.querySelectorAll('main img')) as HTMLImageElement[];
    await Promise.all(images.map(image => image.decode().catch(() => undefined)));
    return images.filter(image => !image.naturalWidth).map(image => image.src);
  });
  expect(failed).toEqual([]);
});

test('content links and search results stay within the selected locale', async ({ app, screen, browser }) => {
  await app.open('/searchable-jpa/snapshot/#/ko/two-phase-query-optimization.md');
  await expect(screen.getByRole('heading', '2단계 쿼리 최적화')).toBeVisible();
  const invalidLinks = await browser.evaluate(() => Array.from(document.querySelectorAll('a'))
    .map(link => link.href).filter(href => /\/(en|ko)\/\1\//.test(href)));
  expect(invalidLinks).toEqual([]);
  await screen.getByRole('link', '관계형 데이터', { exact: true }).tap();
  await expect(browser).toHaveURL('/searchable-jpa/snapshot/#/ko/relationship-and-two-phase-query');
  await screen.getByRole('searchbox').fill('auto-fetch-collections');
  await expect(screen.getByRole('link').filter({ hasText: 'auto-fetch-collections' }).first()).toBeVisible();
  const links = await browser.evaluate(() => Array.from(document.querySelectorAll('.search .matching-post a'))
    .map(link => (link as HTMLAnchorElement).href));
  expect(links.length).toBeGreaterThan(0);
  expect(links.every(href => href.includes('/snapshot/#/ko/'))).toBe(true);
});
