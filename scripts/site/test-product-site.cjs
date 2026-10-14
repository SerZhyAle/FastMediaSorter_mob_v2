// Source-preview browser regressions; a production Jekyll build remains a separate check.
// Run: SITE_CHROMIUM=/path/to/chromium node scripts/site/test-product-site.cjs
// Optional SITE_FEATURES_FIXTURE_DIR uses saved rendered FEATURES*.html for integration coverage.
// Optional SITE_AXE_SCRIPT runs a locally supplied axe-core accessibility audit.
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const root = path.resolve(__dirname, '../..');
const screenshotDir = process.env.SITE_SCREENSHOT_DIR;
if (screenshotDir) fs.mkdirSync(screenshotDir, { recursive: true });
const locales = ['', '-ru', '-uk', '-zh-hans', '-hi', '-es', '-fr', '-ar', '-bn', '-pt', '-ur', '-de', '-it'];
const pages = locales.map(l => 'index' + l + '.html').concat(['nolegal.html', 'nolegal-ru.html', 'nolegal-uk.html']);
const fixture = '<main class="main-content"><h1>Complete Feature List</h1><h2 id="shell">1. Device shell</h2>' +
    '<ul><li><strong>Launcher &amp; desktop</strong> <code>[Standard / noLegal]</code>: Home screen taskbar gadgets widgets.</li>' +
    '<li><strong>Weather</strong> <code>[Standard / VR]</code>: Clock widgets.</li></ul>' +
    '<h2 id="media">2. Media player</h2><ul><li><strong>Video &amp; music</strong> <code>[Standard / VR]</code>: ' +
    'Photo frame slideshow, audio radio, openxr headset, subtitle cinema television, cast playback, NAS SMB SFTP offline downloads backup camera ' +
    'documents EPUB OCR translation markdown notes duplicates calculator math watch Wear companion.</li></ul></main>';
let failures = [], passed = 0;
const server = http.createServer((request, response) => {
    const relative = decodeURIComponent(new URL(request.url, 'http://localhost').pathname).replace(/^\/+/, '');
    if (/^docs\/FEATURES(?:-ru|-uk)?\.html$/.test(relative)) {
        const saved = process.env.SITE_FEATURES_FIXTURE_DIR && path.join(process.env.SITE_FEATURES_FIXTURE_DIR, path.basename(relative));
        response.setHeader('Content-Type', 'text/html; charset=utf-8');
        response.end(saved ? fs.readFileSync(saved) : fixture); return;
    }
    let file = path.resolve(root, relative || 'index.html');
    if (!file.startsWith(root + path.sep)) { response.writeHead(403); response.end(); return; }
    try {
        if (fs.statSync(file).isDirectory()) file = path.join(file, 'index.html');
        const types = { '.html': 'text/html; charset=utf-8', '.js': 'application/javascript', '.css': 'text/css', '.json': 'application/json' };
        response.setHeader('Content-Type', types[path.extname(file)] || 'application/octet-stream');
        response.end(fs.readFileSync(file));
    } catch { response.writeHead(404); response.end(); }
});
async function test(name, action) {
    if (process.env.SITE_TEST_FILTER && !new RegExp(process.env.SITE_TEST_FILTER).test(name)) return;
    try { await action(); passed++; console.log('PASS ' + name); }
    catch (error) { failures.push(name + ': ' + error.message); console.error('FAIL ' + name + ': ' + error.message); }
}
(async () => {
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    const base = 'http://127.0.0.1:' + server.address().port + '/';
    const browser = await chromium.launch({ headless: true,
        ...(process.env.SITE_CHROMIUM ? { executablePath: process.env.SITE_CHROMIUM } : {}), args: ['--no-sandbox'] });
    try {
        const page = await browser.newPage();
        page.setDefaultTimeout(10000);
        await page.route(/^https:\/\//, route => route.abort());
        await test('all 16 product pages load with images and without script errors', async () => {
            const errors = [];
            page.on('pageerror', error => errors.push(error.message));
            for (const file of pages) {
                await page.goto(base + file);
                if (file.startsWith('index')) await page.waitForFunction(() => document.querySelectorAll('.v1-cat-btn').length, {}, { timeout: 10000 });
                await page.evaluate(() => Promise.all(Array.from(document.images, image => { image.loading = "eager"; return image.decode().catch(() => {}); })));
                assert.deepEqual(await page.evaluate(() => Array.from(document.images).filter(i => !i.naturalWidth).map(i => i.src)), [], file);
                assert.equal(await page.locator('main').count(), 1, file);
                assert.equal(await page.locator('h1').count(), 1, file);
            }
            assert.deepEqual(errors, []);
        });
        await test('all locales, dashboard modes and themes fit 320 to 1280 pixels', async () => {
            for (const file of pages) {
                await page.goto(base + file);
                if (file.startsWith('index')) await page.waitForFunction(() => document.querySelectorAll('.v1-cat-btn').length, {}, { timeout: 10000 });
                for (const width of [320, 390, 768, 1280]) {
                    await page.setViewportSize({ width, height: 844 });
                    for (const theme of ['light', 'dark']) {
                        await page.evaluate(t => document.documentElement.dataset.theme = t, theme);
                        for (const variant of file.startsWith('index') ? [1, 2, 3, 4] : [0]) {
                            if (variant) await page.locator('#var-btn-v' + variant).evaluate(button => button.click());
                            const size = await page.evaluate(() => ({ w: innerWidth, sw: document.documentElement.scrollWidth }));
                            assert.ok(size.sw <= size.w + 1, JSON.stringify({ file, width, theme, variant, ...size }));
                        }
                    }
                }
            }
        });
        await test('scenarios precede installation and optional screenshots in every locale', async () => {
            for (const locale of locales) {
                await page.goto(base + 'index' + locale + '.html');
                const layout = await page.evaluate(() => {
                    const scenarios = document.getElementById('scenarios');
                    const get = document.getElementById('get');
                    const gallery = document.getElementById('home-screen');
                    return { first: document.querySelector('main > section').id,
                        beforeGet: !!(scenarios.compareDocumentPosition(get) & Node.DOCUMENT_POSITION_FOLLOWING),
                        beforeGallery: !!(scenarios.compareDocumentPosition(gallery) & Node.DOCUMENT_POSITION_FOLLOWING),
                        cards: document.querySelectorAll('.screenshot-card').length,
                        open: document.querySelectorAll('.screenshot-card[open]').length };
                });
                assert.deepEqual(layout, { first: 'scenarios', beforeGet: true, beforeGallery: true, cards: 3, open: 0 }, locale);
                assert.equal(await page.locator('.screenshot-preview').first().isVisible(), false, locale);
            }
        });
        await test('desktop previews support hover, keyboard, dismissal and viewport limits', async () => {
            await page.setViewportSize({ width: 1280, height: 900 });
            await page.goto(base + 'index-ru.html');
            const card = page.locator('.screenshot-card').first();
            const summary = card.locator('summary');
            await summary.hover();
            await page.waitForFunction(() => document.querySelector('.screenshot-card').open);
            const preview = card.locator('.screenshot-preview');
            await preview.locator('img').evaluate(img => img.decode());
            await page.waitForTimeout(100);
            const rect = await preview.boundingBox();
            assert.ok(rect.x >= 0 && rect.y >= 0 && rect.x + rect.width <= 1281 && rect.y + rect.height <= 901);
            await preview.hover(); await page.waitForTimeout(250);
            assert.equal(await card.evaluate(e => e.open), true, 'preview remains hoverable');
            await page.mouse.move(5, 5); await page.waitForTimeout(250);
            assert.equal(await card.evaluate(e => e.open), false, 'leaving closes the preview');
            await summary.focus(); await page.keyboard.press('Enter');
            assert.equal(await card.evaluate(e => e.open), true);
            await page.keyboard.press('Escape');
            assert.equal(await card.evaluate(e => e.open), false);
            assert.equal(await summary.evaluate(e => e === document.activeElement), true);
            await page.keyboard.press('Space');
            assert.equal(await card.evaluate(e => e.open), true);
            await page.keyboard.press('Space');
            assert.equal(await card.evaluate(e => e.open), false);
            await summary.click();
            assert.equal(await card.evaluate(e => e.open), true);
            await page.mouse.click(5, 5);
            assert.equal(await card.evaluate(e => e.open), false);
            await summary.focus(); await page.keyboard.press('Enter');
            if (screenshotDir) await page.screenshot({ path: path.join(screenshotDir, 'site-preview-desktop.png') });
            await page.keyboard.press('Escape');
        });
        await test('touch previews open on tap, fit the viewport and close on a second tap', async () => {
            const context = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true });
            const touch = await context.newPage(); await touch.route(/^https:\/\//, route => route.abort());
            try {
                await touch.goto(base + 'index-ru.html');
                const card = touch.locator('.screenshot-card').first();
                const summary = card.locator('summary');
                assert.equal(await card.evaluate(e => e.open), false);
                await summary.tap(); assert.equal(await card.evaluate(e => e.open), true);
                await card.locator('img').evaluate(img => img.decode());
                assert.ok(await touch.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1));
                assert.ok((await card.locator('img').boundingBox()).height <= 844 * 0.61);
                if (screenshotDir) await touch.screenshot({ path: path.join(screenshotDir, 'site-preview-mobile.png') });
                await summary.tap(); assert.equal(await card.evaluate(e => e.open), false);
            } finally { await context.close(); }
        });
        await test('screenshots remain optional and usable without JavaScript', async () => {
            const context = await browser.newContext({ javaScriptEnabled: false, viewport: { width: 390, height: 844 } });
            const plain = await context.newPage(); await plain.route(/^https:\/\//, route => route.abort());
            try {
                await plain.goto(base + 'index-ru.html');
                const card = plain.locator('.screenshot-card').first();
                assert.equal(await card.locator('img').isVisible(), false);
                await card.locator('summary').click();
                assert.equal(await card.locator('img').isVisible(), true);
                await card.locator('summary').click();
                assert.equal(await card.locator('img').isVisible(), false);
            } finally { await context.close(); }
        });
        await test('every translated scenario uses its stable filter, not its title', async () => {
            await page.setViewportSize({ width: 1280, height: 900 });
            for (const locale of locales) {
                await page.goto(base + 'index' + locale + '.html');
                await page.waitForFunction(() => document.querySelectorAll('.v1-cat-btn').length, {}, { timeout: 10000 });
                const cards = page.locator('.scenario-card');
                assert.equal(await cards.count(), 24);
                for (let i = 0; i < 24; i++) {
                    const id = await cards.nth(i).getAttribute('data-scenario');
                    await cards.nth(i).locator('.scenario-select').evaluate(button => button.click());
                    assert.equal(await page.locator('.scenario-pill-btn.active').getAttribute('data-id'), id);
                    assert.equal(await page.locator('#var-btn-v4').getAttribute('aria-pressed'), 'true');
                }
            }
        });
        await test('keyboard scenario activation and media-map modal manage focus', async () => {
            await page.goto(base); await page.waitForFunction(() => document.querySelectorAll('.v1-cat-btn').length, {}, { timeout: 10000 });
            await page.locator('.scenario-select').nth(7).focus(); await page.keyboard.press('Enter');
            assert.equal(await page.locator('.scenario-pill-btn.active').getAttribute('data-id'), 'vrcinema');
            await page.locator('#var-btn-v2').click();
            const opener = page.locator('.dashboard-v2-item').first();
            assert.equal(await opener.evaluate(button => button.tagName), 'BUTTON');
            await opener.focus(); await page.keyboard.press('Enter');
            assert.equal(await page.locator('#detailDrawer').evaluate(e => e.inert), false);
            assert.equal(await page.evaluate(() => document.activeElement.id), 'detailDrawerClose');
            assert.equal(await page.locator('main').evaluate(e => e.inert), true);
            await page.keyboard.press('Shift+Tab');
            assert.ok(await page.evaluate(() => !!document.activeElement.closest('#detailDrawer')));
            await page.keyboard.press('Escape');
            assert.equal(await page.locator('#detailDrawer').evaluate(e => e.inert), true);
            assert.equal(await opener.evaluate(e => e === document.activeElement), true);
            assert.equal(await page.locator('main').evaluate(e => e.inert), false);
            await opener.click(); await page.locator('#detailDrawerClose').click();
            assert.equal(await page.locator('#detailDrawer').evaluate(e => e.inert), true);
            await opener.click(); await page.locator('#detailDrawerOverlay').click({ position: { x: 10, y: 10 } });
            assert.equal(await page.locator('#detailDrawer').evaluate(e => e.inert), true);
        });
        await test('theme labels initialize accurately without creating a preference', async () => {
            await page.setViewportSize({ width: 1280, height: 900 });
            for (const file of ['index.html', 'index-ru.html', 'index-uk.html', 'nolegal.html']) {
                await page.goto(base); await page.evaluate(() => localStorage.removeItem('sza-theme'));
                await page.emulateMedia({ colorScheme: 'light' }); await page.goto(base + file);
                const before = await page.locator('#themeBtn').getAttribute('aria-label');
                assert.equal(await page.evaluate(() => localStorage.getItem('sza-theme')), null);
                assert.equal(await page.locator('meta[name="theme-color"]').getAttribute('content'), '#eef3ea');
                await page.locator('#themeBtn').click();
                assert.notEqual(await page.locator('#themeBtn').getAttribute('aria-label'), before);
                assert.equal(await page.evaluate(() => localStorage.getItem('sza-theme')), 'dark');
            }
        });
        await test('HTTP failures retain a localized working Retry action', async () => {
            let blocked = true;
            await page.route('**/docs/FEATURES*.html', route => blocked ? route.fulfill({ status: 503, body: 'unavailable' }) : route.continue());
            await page.goto(base + 'index-ru.html');
            await page.getByRole('button', { name: 'Повторить', exact: true }).waitFor();
            await page.locator('#tab-vr').click();
            assert.equal(await page.getByRole('button', { name: 'Повторить', exact: true }).count(), 1);
            blocked = false; await page.getByRole('button', { name: 'Повторить', exact: true }).click();
            await page.waitForFunction(() => document.querySelectorAll('.v1-cat-btn').length, {}, { timeout: 10000 });
            assert.equal(await page.locator('#featureExplorerGrid').getAttribute('aria-busy'), 'false');
            await page.unroute('**/docs/FEATURES*.html');
        });
        await test('a 200 response without feature records is an error, not an empty success', async () => {
            await page.route('**/docs/FEATURES*.html', route => route.fulfill({ status: 200, contentType: 'text/html', body: '<h1>Not found</h1>' }));
            await page.goto(base); await page.getByRole('button', { name: 'Retry', exact: true }).waitFor();
            await page.unroute('**/docs/FEATURES*.html');
        });
        await test('inventory markup is inert and relative links resolve against its source page', async () => {
            const html = fixture.replace('Launcher &amp; desktop', '&lt;img src=x onerror=alert(1)&gt;').replace('Clock widgets.',
                'Clock widgets. <a href="howto/example.html" onclick="window.injected=true">Guide</a><img src="../favicon.ico" onerror="window.injected=true"><script>window.injected=true</script>');
            await page.route('**/docs/FEATURES*.html', route => route.fulfill({ contentType: 'text/html', body: html }));
            await page.goto(base); await page.waitForFunction(() => document.querySelectorAll('.v1-cat-btn').length, {}, { timeout: 10000 });
            assert.equal(await page.evaluate(() => window.injected), undefined);
            assert.equal(await page.locator('#full-features-content script, #full-features-content [onclick], #full-features-content [onerror]').count(), 0);
            assert.equal(await page.locator('#full-features-content a').getAttribute('href'), base + 'docs/howto/example.html');
            assert.equal(await page.locator('.feature-title img').count(), 0);
            await page.unroute('**/docs/FEATURES*.html');
        });
        await test('release metadata cannot inject HTML and only official asset URLs are used', async () => {
            const api = 'https://api.github.com/repos/SerZhyAle/FastMediaSorter_mob_v2/releases?per_page=100&page=1';
            await page.route(api, route => route.fulfill({ contentType: 'application/json', body: JSON.stringify([{
                tag_name: '<img src=x onerror=alert(1)>', assets: [{ name: 'FastMediaSorter-standard-test.apk',
                    browser_download_url: 'https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/download/test/standard.apk' },
                    { name: 'FastMediaSorter-vr-test.apk', browser_download_url: 'javascript:alert(1)' }] }]) }));
            await page.goto(base); await page.locator('#apkDownloads .apk-ver').waitFor();
            assert.equal(await page.locator('#apkDownloads img').count(), 0);
            assert.equal(await page.locator('#apkDownloads .apk-btn').count(), 1);
            assert.equal(await page.locator('#apkDownloads .apk-ver').innerText(), '<img src=x onerror=alert(1)>');
            await page.unroute(api);
            await page.goto(base); assert.match(await page.locator('#apkDownloads .apk-fallback').getAttribute('href'), /releases$/);
        });
        await test('different editions resolve from their own stable releases, with a permanent history link', async () => {
            const api = 'https://api.github.com/repos/SerZhyAle/FastMediaSorter_mob_v2/releases?per_page=100&page=1';
            const asset = (flavor, version) => ({ name: 'FastMediaSorter-' + flavor + '-' + version + '.apk',
                browser_download_url: 'https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/download/' + version + '/' + flavor + '.apk' });
            await page.route(api, route => route.fulfill({ contentType: 'application/json', body: JSON.stringify([
                { tag_name: 'preview', prerelease: true, published_at: '2026-10-06', assets: [asset('vr', 'preview')] },
                { tag_name: 'new', published_at: '2026-10-05', assets: [asset('standard', 'new')] },
                { tag_name: 'older', published_at: '2026-10-01', assets: [asset('vr', 'older'), asset('noLegal', 'older')] }
            ]) }));
            await page.goto(base); await page.locator('#apkDownloads .apk-btn').first().waitFor();
            assert.deepEqual(await page.locator('#apkDownloads .apk-ver').allTextContents(), ['new', 'older']);
            assert.equal(await page.locator('#apkDownloads .apk-fallback').count(), 1);
            await page.goto(base + 'nolegal.html'); await page.locator('#apkDownloads .apk-btn-nolegal').waitFor();
            assert.equal(await page.locator('#apkDownloads .apk-ver').innerText(), 'older');
            await page.unroute(api);
        });
        await test('a stalled inventory request times out instead of loading forever', async () => {
            const stalled = await browser.newPage();
            try {
                await stalled.route(/^https:\/\//, route => route.abort());
                await stalled.addInitScript(() => { const original = window.setTimeout; window.setTimeout = (fn, ms, ...args) => original(fn, ms === 15000 ? 50 : ms, ...args); });
                await stalled.route('**/docs/FEATURES*.html', async route => {
                    await new Promise(resolve => setTimeout(resolve, 250));
                    try { await route.fulfill({ contentType: 'text/html', body: '<h1>Late response</h1>' }); } catch (_) {}
                });
                await stalled.goto(base);
                await stalled.getByRole('button', { name: 'Retry', exact: true }).waitFor({ timeout: 2000 });
                assert.equal(await stalled.locator('#featureExplorerGrid').getAttribute('aria-busy'), 'false');
            } finally { await stalled.close(); }
        });
        await test('four edition cards expose verified links without JavaScript in every locale', async () => {
            const offline = await browser.newPage({ javaScriptEnabled: false });
            await offline.route(/^https:\/\//, route => route.abort());
            try {
                for (const locale of locales) {
                    await offline.goto(base + 'index' + locale + '.html');
                    assert.equal(await offline.locator('[data-download-edition]').count(), 4);
                    for (const kind of ['vr', 'wear']) {
                        const card = offline.locator('[data-download-edition="' + kind + '"]');
                        assert.equal(await card.locator('[data-edition-apk]').isVisible(), true);
                        assert.match(await card.locator('[data-edition-apk]').getAttribute('href'), /github\.com\/SerZhyAle\/FastMediaSorter_mob_v2\/releases\/download\//);
                        assert.ok((await card.locator('.edition-build').innerText()).includes('v2.'));
                    }
                    assert.equal(await offline.locator('[data-download-edition="noLegal"] [data-edition-apk]').isVisible(), false);
                    assert.equal(await offline.locator('[data-download-edition="watchface"] [data-edition-apk]').isVisible(), false);
                    assert.equal(await offline.locator('[data-download-edition="watchface"] a[href*="com.sza.fastmediasorter.watchface"]').count(), 1);
                    const link = offline.locator('[data-download-edition="wear"] [data-edition-apk]');
                    assert.equal(await link.innerText(), await link.getAttribute('data-download-label'));
                }
            } finally { await offline.close(); }
        });
        await test('new noLegal and watch-face APKs appear and preview builds are clearly marked', async () => {
            const api = 'https://api.github.com/repos/SerZhyAle/FastMediaSorter_mob_v2/releases?per_page=100&page=1';
            const asset = kind => ({ name: 'FastMediaSorter-' + kind + '-preview.apk',
                browser_download_url: 'https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/download/preview/' + kind + '.apk' });
            await page.route(api, route => route.fulfill({ contentType: 'application/json', body: JSON.stringify([
                { tag_name: 'preview', prerelease: true, published_at: '2026-10-06', assets: [asset('noLegal'), asset('wear'), asset('watchface')] },
                { tag_name: 'stable', published_at: '2026-10-05', assets: [{ name: 'FastMediaSorter-wear-stable.apk',
                    browser_download_url: 'https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/download/stable/wear.apk' }] }
            ]) }));
            await page.goto(base + 'index-ru.html');
            await page.locator('[data-download-edition="noLegal"] [data-edition-apk]').waitFor();
            for (const kind of ['noLegal', 'wear', 'watchface']) {
                const card = page.locator('[data-download-edition="' + kind + '"]');
                assert.match(await card.locator('[data-edition-apk]').getAttribute('href'), /\/preview\//);
                assert.ok((await card.locator('.edition-build').innerText()).includes('Тестовая сборка'));
            }
            assert.deepEqual(await page.locator('#apkDownloads .apk-ver').allTextContents(), ['stable']);
            await page.unroute(api);
        });
        await test('GitHub failure keeps verified download cards, while a complete empty history does not invent APKs', async () => {
            await page.goto(base);
            assert.equal(await page.locator('[data-download-edition="vr"] [data-edition-apk]').isVisible(), true);
            const api = 'https://api.github.com/repos/SerZhyAle/FastMediaSorter_mob_v2/releases?per_page=100&page=1';
            await page.route(api, route => route.fulfill({ contentType: 'application/json', body: '[]' }));
            await page.goto(base);
            await page.waitForFunction(() => document.querySelector('[data-download-edition="vr"] [data-edition-apk]').hidden);
            assert.equal(await page.locator('[data-download-edition="watchface"] a[href*="play.google.com"]').count(), 1);
            await page.unroute(api);
        });
        await test('search, platform filters and all four layouts remain functional', async () => {
            await page.goto(base); await page.waitForFunction(() => document.querySelectorAll('.v1-cat-btn').length, {}, { timeout: 10000 });
            for (const variant of [1, 2, 3, 4]) {
                await page.locator('#var-btn-v' + variant).click();
                await page.locator('#featureSearch').fill('ZZZ-no-match-999');
                assert.match(await page.locator('#featureExplorerGrid').innerText(), /No features/);
                await page.locator('#featureSearch').fill('');
                assert.ok((await page.locator('#featureExplorerGrid').innerText()).length > 0);
                await page.locator('#tab-vr').click();
                assert.equal(await page.locator('#tab-vr').getAttribute('aria-pressed'), 'true');
                await page.locator('#tab-all').click();
            }
        });
        await test('broadcast fallback hides incomplete links and encodes valid payloads', async () => {
            await page.goto(base + 'broadcast-import.html'); assert.equal(await page.locator('#open-app').isVisible(), false);
            await page.goto(base + 'broadcast-import.html#payload=a%26b');
            await page.waitForFunction(() => document.getElementById('open-app').getAttribute('href') === 'fmsbcast://import?payload=a%26b');
            assert.equal(await page.locator('#open-app').isVisible(), true);
        });
        if (process.env.SITE_AXE_SCRIPT) await test('automated accessibility has no confirmed violations in either theme', async () => {
            for (const file of ['index.html', 'index-ru.html', 'index-ar.html', 'nolegal.html']) {
                await page.goto(base + file);
                if (file.startsWith('index')) await page.waitForFunction(() => document.querySelectorAll('.v1-cat-btn').length, {}, { timeout: 10000 });
                for (const theme of ['light', 'dark']) {
                    await page.evaluate(t => document.documentElement.dataset.theme = t, theme);
                    await page.waitForTimeout(400);
                    await page.addScriptTag({ path: process.env.SITE_AXE_SCRIPT });
                    const violations = await page.evaluate(async () => (await axe.run(document)).violations);
                    assert.deepEqual(violations.map(v => ({ id: v.id, nodes: v.nodes.map(n => ({ target: n.target, summary: n.failureSummary })) })), [], file + ':' + theme);
                }
            }
        });
    } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
    console.log(`${passed} passed; ${failures.length} failed.`);
    process.exit(failures.length ? 1 : 0);
})().catch(error => { console.error(error); server.close(); process.exit(1); });
