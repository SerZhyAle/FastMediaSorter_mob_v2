/* Shared landing-page behavior; visual styling and the wave renderer remain unchanged. */
(function () {
    'use strict';
    var opener = null;
    var background = [];
    var previousOverflow = '';
    function fetchWithTimeout(url, options) {
        var controller = new AbortController();
        var timer = setTimeout(function () { controller.abort(); }, 15000);
        return fetch(url, Object.assign({}, options, { signal: controller.signal }))
            .finally(function () { clearTimeout(timer); });
    }
    function releaseUrl(value, repo) {
        try {
            var url = new URL(value);
            return url.protocol === 'https:' && url.hostname === 'github.com' &&
                url.pathname.indexOf('/' + repo + '/releases/download/') === 0;
        } catch (_) { return false; }
    }
    function releaseLabel(link, label, version) {
        var flavor = document.createElement('span');
        flavor.className = 'apk-flavor';
        flavor.textContent = label;
        var tag = document.createElement('span');
        tag.className = 'apk-ver';
        tag.textContent = version;
        link.append(flavor, document.createTextNode(' '), tag);
    }
    function loadReleaseDownloads(box) {
        if (!box) return;
        var repo = 'SerZhyAle/FastMediaSorter_mob_v2';
        var wanted = (box.getAttribute('data-flavors') || '').split(',').filter(Boolean);
        var labels = { standard: 'Standard', vr: 'VR', lite: 'Lite', photos: 'Photos', legacy: 'Legacy', wear: 'Wear OS', noLegal: 'noLegal' };
        var found = {};
        function readPage(page) {
            return fetchWithTimeout('https://api.github.com/repos/' + repo + '/releases?per_page=100&page=' + page,
                { headers: { Accept: 'application/vnd.github+json' } })
                .then(function (response) { if (!response.ok) throw new Error('releases ' + response.status); return response.json(); })
                .then(function (releases) {
                    if (!Array.isArray(releases)) throw new Error('Invalid release list');
                    releases.filter(function (release) { return !release.draft && !release.prerelease; })
                        .sort(function (a, b) { return String(b.published_at || b.created_at || '').localeCompare(String(a.published_at || a.created_at || '')); })
                        .forEach(function (release) {
                            wanted.forEach(function (flavor) {
                                if (found[flavor]) return;
                                var asset = (release.assets || []).find(function (item) {
                                    return typeof item.name === 'string' && item.name.indexOf('FastMediaSorter-' + flavor + '-') === 0 &&
                                        item.name.endsWith('.apk') && releaseUrl(item.browser_download_url, repo);
                                });
                                if (asset) found[flavor] = { url: asset.browser_download_url, version: release.tag_name || '' };
                            });
                        });
                    if (releases.length === 100 && page < 3 && wanted.some(function (flavor) { return !found[flavor]; })) return readPage(page + 1);
                });
        }
        // Keep verified results even if a later history page fails, and always retain the static history link.
        readPage(1).catch(function () {}).finally(function () {
            var fragment = document.createDocumentFragment();
            wanted.forEach(function (flavor) {
                if (!found[flavor]) return;
                var link = document.createElement('a');
                link.className = 'apk-btn' + (flavor === 'noLegal' ? ' apk-btn-nolegal' : '');
                link.href = found[flavor].url;
                link.setAttribute('rel', 'noopener');
                releaseLabel(link, labels[flavor] || flavor, found[flavor].version);
                fragment.appendChild(link);
            });
            box.insertBefore(fragment, box.firstChild);
        });
    }
    function prepareInventory(main, source) {
        var base = new URL(source, location.href);
        // The embedded inventory is a section of the landing, not a second page-level heading.
        main.querySelectorAll('h1').forEach(function (heading) {
            var sectionHeading = document.createElement('h2');
            Array.from(heading.attributes).forEach(function (attribute) { sectionHeading.setAttribute(attribute.name, attribute.value); });
            while (heading.firstChild) sectionHeading.appendChild(heading.firstChild);
            heading.replaceWith(sectionHeading);
        });
        main.querySelectorAll('script, style, iframe, object, embed, form, input, button, link, meta').forEach(function (node) { node.remove(); });
        main.querySelectorAll('*').forEach(function (node) {
            Array.from(node.attributes).forEach(function (attribute) {
                if (/^on/i.test(attribute.name) || attribute.name === 'srcdoc') node.removeAttribute(attribute.name);
            });
            ['href', 'src'].forEach(function (attribute) {
                if (!node.hasAttribute(attribute)) return;
                try {
                    var url = new URL(node.getAttribute(attribute), base);
                    if (!/^https?:$/.test(url.protocol) && !(attribute === 'href' && url.protocol === 'mailto:')) {
                        node.removeAttribute(attribute);
                    } else node.setAttribute(attribute, url.href);
                } catch (_) { node.removeAttribute(attribute); }
            });
            if (node.getAttribute('target') === '_blank') node.setAttribute('rel', 'noopener');
        });
    }
    function openDrawer() {
        var drawer = document.getElementById('detailDrawer');
        if (!drawer || !drawer.inert) return;
        opener = document.activeElement;
        drawer.inert = false;
        background = Array.from(document.body.children).filter(function (node) {
            return node !== drawer && node.id !== 'detailDrawerOverlay' && !/^(SCRIPT|STYLE)$/.test(node.tagName);
        }).map(function (node) { var old = node.inert; node.inert = true; return { node: node, old: old }; });
        previousOverflow = document.body.style.overflow;
        document.body.style.overflow = 'hidden';
        document.getElementById('detailDrawerClose').focus({ preventScroll: true });
    }
    function closeDrawer() {
        var drawer = document.getElementById('detailDrawer');
        if (!drawer || drawer.inert) return;
        drawer.inert = true;
        background.forEach(function (entry) { entry.node.inert = entry.old; });
        background = [];
        document.body.style.overflow = previousOverflow;
        if (opener && opener.isConnected) opener.focus({ preventScroll: true });
        opener = null;
    }
    document.addEventListener('keydown', function (event) {
        var drawer = document.getElementById('detailDrawer');
        if (!drawer || drawer.inert || event.key !== 'Tab') return;
        var controls = Array.from(drawer.querySelectorAll('a[href], button, input, [tabindex="0"]')).filter(function (node) {
            return !node.disabled && node.getClientRects().length;
        });
        var first = controls[0], last = controls[controls.length - 1];
        if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
        else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    });
    document.addEventListener('DOMContentLoaded', function () {
        var panel = document.querySelector('.explorer-panel');
        if (!panel) return;
        function syncButtons() {
            panel.querySelectorAll('.variant-btn, .tab-btn, .scenario-pill-btn, .v1-cat-btn, .toggle-v3-btn').forEach(function (button) {
                button.setAttribute('aria-pressed', String(button.classList.contains('active')));
            });
        }
        new MutationObserver(syncButtons).observe(panel, { subtree: true, childList: true, attributes: true, attributeFilter: ['class'] });
        syncButtons();
    });
    window.SiteUI = {
        fetch: fetchWithTimeout, loadReleaseDownloads: loadReleaseDownloads, releaseUrl: releaseUrl, releaseLabel: releaseLabel,
        prepareInventory: prepareInventory, openDrawer: openDrawer, closeDrawer: closeDrawer,
        initTheme: function (button, meta, apply) {
            apply(document.documentElement.getAttribute('data-theme') === 'light' ? 'light' : 'dark', false);
        }
    };
}());
