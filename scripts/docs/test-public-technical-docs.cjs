// Public technical-reference regression: validate source facts and published routes, not app builds.
// Run from the repository root: node scripts/docs/test-public-technical-docs.cjs
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '../..');
const read = file => fs.readFileSync(path.join(root, file), 'utf8');
const registry = read('docs/DOCUMENT_REGISTRY.jsonl').split(/\r?\n/).filter(s => s.trim()).map(s => JSON.parse(s));
const group = registry.find(r => r.id === 'public-technical-reference');
assert.ok(group && group.published && group.indexable, 'Technical references must have public publication metadata');
const files = group.paths.concat(['', '-ru', '-uk'].flatMap(s => ['docs/V2_TERMS' + s + '.md', 'docs/TODO_V2' + s + '.md']));
function frontmatter(text) {
    const match = text.match(/^---\r?\n([\s\S]*?)\r?\n---\r?\n/);
    assert.ok(match, 'Missing front matter');
    const permalink = match[1].match(/^permalink:\s*(\S+)/m);
    assert.ok(permalink, 'Missing permalink');
    return { url: permalink[1], body: text.slice(match[0].length) };
}
function walk(dir) {
    if (!fs.existsSync(path.join(root, dir))) return [];
    return fs.readdirSync(path.join(root, dir), { withFileTypes: true }).flatMap(entry => {
        const file = dir + '/' + entry.name;
        return entry.isDirectory() ? walk(file) : [file];
    });
}
const routes = new Map();
for (const file of walk('docs').concat(walk('documentation'))) {
    if (file.endsWith('.md')) {
        const match = read(file).match(/^---\r?\n[\s\S]*?^permalink:\s*(\S+)/m);
        if (match) routes.set(match[1], file);
    } else if (file.endsWith('.html')) {
        routes.set('/' + file, file);
        if (file.endsWith('/index.html')) routes.set('/' + file.slice(0, -10), file);
    }
}
for (const file of fs.readdirSync(root).filter(f => f.endsWith('.html'))) routes.set('/' + file, file);
const config = read('_config.yml');
const excluded = (config.split(/^exclude:\s*$/m)[1] || '').split(/\r?\n/).filter(s => /^\s+- /.test(s))
    .map(s => s.replace(/^\s+- /, '').trim().replace(/\/$/, ''));
let links = 0;
for (const file of files) {
    const text = read(file);
    const page = frontmatter(text);
    assert.equal((page.body.match(/^# /gm) || []).length, 1, file + ': expected one H1');
    assert.ok(!/TODO\(|\bTBD\b/.test(page.body), file + ': unfinished placeholder');
    const language = text.match(/^lang:\s*(\S+)/m);
    if (language && language[1] !== 'en') assert.ok(page.body.includes('<div lang="' + language[1] + '" markdown="1">'), file + ': translated body language must override the English theme');
    for (const link of page.body.matchAll(/!?\[[^\]]*\]\(([^\s)]+)(?:\s+"[^"]*")?\)/g)) {
        const href = link[1]; links++;
        if (/^https?:/.test(href)) {
            const source = href.match(/^https:\/\/github\.com\/SerZhyAle\/FastMediaSorter_mob_v2\/blob\/main\/(.*?)(?:#.*)?$/);
            if (source) assert.ok(fs.existsSync(path.join(root, decodeURIComponent(source[1]))), file + ': missing source ' + href);
            continue;
        }
        const target = new URL(href, 'https://site.test' + page.url);
        assert.ok(!excluded.some(p => target.pathname.startsWith('/' + p + '/') || target.pathname === '/' + p), file + ': unpublished path ' + href);
        const physical = routes.get(decodeURIComponent(target.pathname));
        assert.ok(physical, file + ': no public route ' + href);
        if (target.hash && physical.endsWith('.html')) {
            const id = decodeURIComponent(target.hash.slice(1));
            assert.ok(read(physical).includes('id="' + id + '"'), file + ': missing anchor ' + href);
        }
    }
}
// Read balanced Gradle blocks without interpreting braces inside comments or strings.
function block(text, expression) {
    const match = text.match(expression); assert.ok(match, 'Missing Gradle block: ' + expression);
    let start = text.indexOf('{', match.index), depth = 0, mode = '', escaped = false;
    for (let i = start; i < text.length; i++) {
        const c = text[i], next = text[i + 1];
        if (mode === 'line') { if (c === '\n') mode = ''; continue; }
        if (mode === 'comment') { if (c === '*' && next === '/') { mode = ''; i++; } continue; }
        if (mode === 'string') {
            if (escaped) escaped = false; else if (c === '\\') escaped = true; else if (c === '"') mode = '';
            continue;
        }
        if (c === '/' && next === '/') { mode = 'line'; i++; continue; }
        if (c === '/' && next === '*') { mode = 'comment'; i++; continue; }
        if (c === '"') { mode = 'string'; continue; }
        if (c === '{') depth++;
        if (c === '}' && --depth === 0) return text.slice(start + 1, i);
    }
    throw new Error('Unbalanced Gradle block: ' + expression);
}
const property = (text, name) => { const m = text.match(new RegExp('^\\s*' + name + '\\s*=\\s*(\\d+)', 'm')); return m ? Number(m[1]) : undefined; };
const app = read('app_v2/build.gradle.kts'), wear = read('wear/build.gradle.kts'), face = read('watchface/build.gradle.kts');
const defaults = text => block(text, /^\s*defaultConfig\s*\{/m);
const appDefaults = defaults(app), wearDefaults = defaults(wear), faceDefaults = defaults(face);
const flavors = block(app, /^\s*productFlavors\s*\{/m);
const names = Array.from(flavors.matchAll(/^\s*create\("([^"]+)"\)/gm), m => m[1]);
const sdk = (text, def) => [property(def, 'minSdk'), property(def, 'targetSdk'), property(text, 'compileSdk')];
const variant = name => { const body = block(flavors, new RegExp('^\\s*create\\("' + name + '"\\)\\s*\\{', 'm')); return [property(body, 'minSdk') ?? property(appDefaults, 'minSdk'), property(body, 'targetSdk') ?? property(appDefaults, 'targetSdk'), property(app, 'compileSdk')]; };
const expected = new Map([
    ['standard / noLegal / lite / photos', variant('standard')], ['legacy / foss', variant('legacy')],
    ['vr', variant('vr')], ['xr', variant('xr')], ['wear: standard / noLegal', sdk(wear, wearDefaults)], ['watchface', sdk(face, faceDefaults)]
]);
for (const name of ['noLegal', 'lite', 'photos']) assert.deepEqual(variant(name), variant('standard'), name + ': split requirement row');
assert.deepEqual(variant('foss'), variant('legacy'), 'foss: split requirement row');
for (const suffix of ['', '-ru', '-uk']) {
    const text = read('docs/TECHNICAL_REQUIREMENTS' + suffix + '.md');
    const rows = new Map(Array.from(text.matchAll(/^\| `([^`]+)` \| (\d+) \| (\d+) \| (\d+) \|$/gm), m => [m[1], m.slice(2).map(Number)]));
    assert.deepEqual(rows, expected, suffix + ': public SDK table differs from Gradle');
}
const stack = read('docs/TECH_STACK.md');
for (const name of names) assert.ok(stack.includes('`' + name + '`'), 'Missing declared flavor: ' + name);
for (const module of Array.from(read('settings.gradle.kts').matchAll(/^include\(":([^"]+)"\)/gm), m => m[1])) assert.ok(stack.includes('`' + module + '`'), 'Missing included module: ' + module);
assert.ok(!/There is no Gradle version catalog|6,000-9,000|2024\.02\.00/.test(stack), 'Obsolete stack claims');
const wearNamespace = wear.match(/^\s*namespace = "([^"]+)"/m)[1];
const wearId = wearDefaults.match(/^\s*applicationId = "([^"]+)"/m)[1];
assert.ok(stack.includes('`' + wearNamespace + '`') && stack.includes('**`' + wearId + '`**'), 'Namespace and application identity must remain distinct');
const rootBuild = read('build.gradle.kts');
const plugin = id => { const m = rootBuild.match(new RegExp('id\\("' + id.replaceAll('.', '\\.') + '"\\) version "([^" ]+)"')); assert.ok(m, id); return m[1]; };
const tableVersions = new Map(Array.from(stack.matchAll(/^\| ([^|]+?) \| `([^`]+)` \|$/gm), m => [m[1].trim(), m[2]]));
const wrapper = read('gradle/wrapper/gradle-wrapper.properties').match(/gradle-([\d.]+)-/)[1];
for (const [label, actual] of [
    ['Gradle wrapper', wrapper], ['Android Gradle Plugin', plugin('com.android.application')],
    ['Kotlin and Compose compiler plugin', plugin('org.jetbrains.kotlin.plugin.compose')],
    ['KSP', plugin('com.google.devtools.ksp')], ['Hilt plugin', plugin('com.google.dagger.hilt.android')],
    ['Chaquopy plugin', plugin('com.chaquo.python')]
]) assert.equal(tableVersions.get(label), actual, 'Build tool drift: ' + label);
const catalog = read('gradle/libs.versions.toml');
const version = name => { const m = catalog.match(new RegExp('^' + name + ' = "([^" ]+)"', 'm')); assert.ok(m, name); return m[1]; };
assert.ok(stack.includes('BOM is declared as `' + version('compose-bom') + '`'), 'Compose BOM drift');
assert.ok(stack.includes('libraries are declared as `' + version('androidx-wear-compose-material') + '`'), 'Wear Compose drift');
assert.ok(stack.includes('bcprov-jdk18on:' + version('bouncycastle')), 'Crypto version drift');
assert.ok(stack.includes('nanohttpd:' + version('nanohttpd')), 'Cast proxy version drift');
console.log('Public technical docs: PASS - ' + files.length + ' pages, ' + links + ' links, three Gradle-verified SDK tables, modules and edition names');
