// Run with Node.js and Playwright installed: node scripts/docs/test-docs-portal.cjs
// Exercises a sandbox-local source preview; it does not claim to replace a Jekyll production build.
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const root = path.resolve(__dirname, '../..');
const failures = [];
let passed = 0;
const server = http.createServer((request, response) => {
    let relative = decodeURIComponent(new URL(request.url, 'http://localhost').pathname).replace(/^\/+/, '');
    let file = path.resolve(root, relative || '.');
    if (!file.startsWith(root + path.sep)) { response.writeHead(403); response.end(); return; }
    try {
        if (fs.statSync(file).isDirectory()) file = path.join(file, 'index.html');
        let body = fs.readFileSync(file);
        const extension = path.extname(file);
        if (extension === '.html') body = Buffer.from(body.toString('utf8').replace(/^---\r?\n[\s\S]*?\r?\n---\r?\n/, ''));
        const types = { '.html': 'text/html; charset=utf-8', '.js': 'application/javascript', '.json': 'application/json', '.css': 'text/css' };
        response.writeHead(200, { 'Content-Type': types[extension] || 'application/octet-stream' });
        response.end(body);
    } catch { response.writeHead(404); response.end(); }
});
async function test(name, action) {
    try { await action(); passed++; console.log('PASS ' + name); }
    catch (error) { failures.push(name + ': ' + error.message); console.log('FAIL ' + name + ': ' + error.message); }
}
(async () => {
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    const base = 'http://127.0.0.1:' + server.address().port + '/documentation/';
    const browser = await chromium.launch({ headless: true,
        ...(process.env.DOCS_CHROMIUM ? { executablePath: process.env.DOCS_CHROMIUM } : {}),
        args: ['--no-sandbox'] });
    try {
        const page = await browser.newPage();
        await page.route(/fonts\.(googleapis|gstatic)\.com/, route => route.abort());
        await test('locale links reach the actual translated hub', async () => {
            await page.goto(base); await page.locator('#langBtn').click();
            assert.equal(await page.locator('#langMenu a').count(), 3);
            await page.locator('#langMenu a[data-lang="ru"]').click();
            await page.waitForURL('**/index-ru.html');
            assert.equal(await page.locator('html').getAttribute('lang'), 'ru');
            await page.locator('#langBtn').click(); await page.locator('#langMenu a[data-lang="uk"]').click();
            await page.waitForURL('**/index-uk.html');
            assert.equal(await page.locator('html').getAttribute('lang'), 'uk');
        });
        await test('legacy language query URLs reach the translation', async () => {
            await page.goto(base + '?lang=ru');
            await page.waitForURL('**/index-ru.html');
            assert.equal(await page.locator('html').getAttribute('lang'), 'ru');
        });
        await test('locale switch preserves a recipe topic and metadata', async () => {
            await page.goto(base + 'storage/batch-renaming-ru.html');
            await page.locator('#langBtn').click(); await page.locator('#langMenu a[data-lang="uk"]').click();
            await page.waitForURL('**/batch-renaming-uk.html');
            assert.equal(await page.locator('link[hreflang="uk"]').count(), 1);
            assert.match(await page.locator('link[hreflang="en"]').getAttribute('href'), /batch-renaming\.html$/);
        });
        await test('first search reruns automatically when its index arrives', async () => {
            await page.goto(base);
            await page.route('**/search-index.json', async route => {
                await new Promise(resolve => setTimeout(resolve, 500)); await route.continue();
            });
            await page.locator('[data-search-trigger]').first().click();
            await page.locator('#docSearchInput').fill('SMB');
            await page.locator('.doc-search-item').first().waitFor();
            assert.ok(await page.locator('.doc-search-item').count() > 0);
            await page.unroute('**/search-index.json');
        });
        await test('search traps focus and restores the opener', async () => {
            await page.keyboard.press('Shift+Tab');
            assert.equal(await page.evaluate(() => !!document.activeElement.closest('#docSearchModal')), true);
            await page.keyboard.press('Escape');
            assert.equal(await page.evaluate(() => document.activeElement.hasAttribute('data-search-trigger')), true);
            assert.equal(await page.locator('.doc-container').evaluate(element => element.inert), false);
        });
        await test('search indexes body-only recipe text', async () => {
            await page.goto(base); await page.locator('[data-search-trigger]').first().click();
            await page.locator('#docSearchInput').fill('Got it, start');
            await page.locator('.doc-search-item').first().waitFor();
            assert.ok((await page.locator('.doc-search-item').allTextContents()).some(text => text.includes('First Launch')));
        });
        await test('Ukrainian search stays Ukrainian', async () => {
            await page.goto(base + 'index-uk.html'); await page.locator('[data-search-trigger]').first().click();
            await page.locator('#docSearchInput').fill('SMB');
            await page.locator('.doc-search-item').first().waitFor();
            assert.match(await page.locator('.doc-search-item').first().getAttribute('href'), /-uk\.html$/);
            assert.match(await page.locator('#docSearchInput').getAttribute('placeholder'), /Знайти/);
            assert.ok(!(await page.locator('.doc-search-item-meta').first().innerText()).match(/S\d{4}|Published/));
        });
        await test('failed index requests show an error and can retry', async () => {
            await page.goto(base);
            let blocked = true;
            await page.route('**/search-index.json', route => blocked ? route.fulfill({ status: 503, body: '{}' }) : route.continue());
            await page.locator('[data-search-trigger]').first().click();
            await page.locator('#docSearchInput').fill('SMB');
            await page.getByRole('button', { name: 'Retry', exact: true }).waitFor();
            blocked = false;
            await page.getByRole('button', { name: 'Retry', exact: true }).click();
            await page.locator('.doc-search-item').first().waitFor();
            await page.unroute('**/search-index.json');
        });
        await test('mobile widths and themes do not overflow', async () => {
            for (const width of [320, 360, 390, 768]) {
                await page.setViewportSize({ width, height: 844 });
                for (const theme of ['light', 'dark']) {
                    await page.goto(base);
                    await page.evaluate(theme => document.documentElement.dataset.theme = theme, theme);
                    const sizes = await page.evaluate(() => ({ viewport: innerWidth, content: document.documentElement.scrollWidth }));
                    assert.ok(sizes.content <= sizes.viewport + 1, JSON.stringify({ width, theme, ...sizes }));
                }
            }
        });
        await test('mobile drawer closes on Escape and returns focus', async () => {
            await page.setViewportSize({ width: 390, height: 844 }); await page.goto(base);
            await page.locator('#mobileMenuBtn').click();
            assert.equal(await page.locator('#mobileMenuBtn').getAttribute('aria-expanded'), 'true');
            await page.keyboard.press('Shift+Tab');
            assert.equal(await page.evaluate(() => !!document.activeElement.closest('.doc-sidebar')), true);
            await page.keyboard.press('Escape');
            assert.equal(await page.locator('.doc-sidebar').evaluate(element => element.classList.contains('active')), false);
            assert.equal(await page.evaluate(() => document.activeElement.id), 'mobileMenuBtn');
        });
        await test('curated hubs retain the category directory', async () => {
            for (const suffix of ['', '-ru', '-uk']) {
                await page.goto(base + 'index' + suffix + '.html');
                assert.equal(await page.locator('#start-with-a-task a').count(), 5);
                assert.equal(await page.locator('#categories').count(), 1);
            }
        });
    } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
    console.log('Summary: ' + passed + ' passed, ' + failures.length + ' failed');
    if (failures.length) { failures.forEach(failure => console.error(failure)); process.exit(1); }
    process.exit(0);
})().catch(error => { console.error(error); server.close(); process.exit(1); });
