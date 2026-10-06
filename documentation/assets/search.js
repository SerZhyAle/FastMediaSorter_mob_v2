/** Shared documentation navigation and client-side search. */
(function () {
    'use strict';
    var index = null;
    var loading = null;
    var modal, input, results, opener;
    var activeIndex = -1;
    var previousOverflow = '';
    var inertState = [];
    var lang = document.documentElement.lang === 'ua' ? 'uk' : document.documentElement.lang;
    if (['en', 'ru', 'uk'].indexOf(lang) < 0) lang = 'en';
    var messages = {
        en: { title: 'Search documentation', placeholder: 'Search guides, controls, and features...',
            prompt: 'Enter a feature, control, or question.', loading: 'Loading documentation...',
            error: 'Documentation search could not be loaded.', retry: 'Retry', empty: 'No matching guides.',
            close: 'Close search', navigate: 'Navigate', select: 'Open', fallback: 'Other language',
            language: 'Documentation language', menu: 'Documentation navigation' },
        ru: { title: 'Поиск по документации', placeholder: 'Найти руководство, кнопку или функцию...',
            prompt: 'Введите функцию, название кнопки или вопрос.', loading: 'Загрузка документации...',
            error: 'Не удалось загрузить поиск по документации.', retry: 'Повторить', empty: 'Подходящих руководств нет.',
            close: 'Закрыть поиск', navigate: 'Выбрать', select: 'Открыть', fallback: 'Другой язык',
            language: 'Язык документации', menu: 'Навигация по документации' },
        uk: { title: 'Пошук у документації', placeholder: 'Знайти посібник, кнопку або функцію...',
            prompt: 'Введіть функцію, назву кнопки або запитання.', loading: 'Завантаження документації...',
            error: 'Не вдалося завантажити пошук у документації.', retry: 'Повторити', empty: 'Відповідних посібників немає.',
            close: 'Закрити пошук', navigate: 'Вибрати', select: 'Відкрити', fallback: 'Інша мова',
            language: 'Мова документації', menu: 'Навігація документації' }
    };
    var text = messages[lang];
    function getDocRelativePrefix() {
        var scripts = document.querySelectorAll('script[src]');
        for (var i = 0; i < scripts.length; i++) {
            var src = scripts[i].getAttribute('src');
            var at = src.indexOf('assets/search.js');
            if (at >= 0) return src.substring(0, at);
        }
        return '';
    }
    function escapeHtml(value) {
        return String(value || '').replace(/[&<>"']/g, function (c) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
        });
    }
    function highlight(value, query) {
        var raw = String(value || '');
        var terms = query.split(/\s+/).filter(Boolean).sort(function (a, b) { return b.length - a.length; });
        var pattern = terms.map(function (t) { return t.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'); }).join('|');
        if (!pattern) return escapeHtml(raw);
        var regex = new RegExp(pattern, 'gi'), html = '', last = 0, match;
        while ((match = regex.exec(raw))) {
            html += escapeHtml(raw.substring(last, match.index));
            html += '<mark class="doc-search-highlight">' + escapeHtml(match[0]) + '</mark>';
            last = match.index + match[0].length;
        }
        return html + escapeHtml(raw.substring(last));
    }
    function status(message, isError) {
        results.setAttribute('aria-busy', 'false');
        results.innerHTML = '<div class="doc-search-empty" role="status">' + escapeHtml(message) + '</div>';
        if (isError) {
            var retry = document.createElement('button');
            retry.className = 'doc-search-close';
            retry.textContent = text.retry;
            retry.addEventListener('click', function () { loadIndex(); });
            results.firstChild.appendChild(document.createElement('br'));
            results.firstChild.appendChild(retry);
        }
    }
    function loadIndex() {
        if (index) { search(); return Promise.resolve(); }
        if (loading) return loading;
        status(text.loading);
        results.setAttribute('aria-busy', 'true');
        loading = fetch(getDocRelativePrefix() + 'assets/search-index.json').then(function (response) {
            if (!response.ok) throw new Error('Index request failed');
            return response.json();
        }).then(function (data) {
            if (Array.isArray(data.pages)) return data.pages;
            if (!Array.isArray(data.chunks)) throw new Error('Invalid index');
            return Promise.all(data.chunks.map(function (chunk) {
                if (!/^search-pages-(en|ru|uk)-\d+\.json$/.test(chunk.file)) throw new Error('Invalid shard');
                return fetch(getDocRelativePrefix() + 'assets/' + chunk.file).then(function (response) {
                    if (!response.ok) throw new Error('Shard request failed');
                    return response.json();
                }).then(function (part) {
                    if (!Array.isArray(part.pages)) throw new Error('Invalid shard');
                    return part.pages;
                });
            })).then(function (parts) { return [].concat.apply([], parts); });
        }).then(function (pages) {
            index = pages.filter(function (p) { return p.published && p.url; });
            if (modal.classList.contains('active')) search();
        }).catch(function () {
            if (modal.classList.contains('active')) status(text.error, true);
        }).finally(function () { loading = null; });
        return loading;
    }
    function createModal() {
        if (modal) return;
        modal = document.createElement('div');
        modal.id = 'docSearchModal';
        modal.className = 'doc-search-backdrop';
        modal.setAttribute('role', 'dialog');
        modal.setAttribute('aria-modal', 'true');
        modal.setAttribute('aria-label', text.title);
        modal.innerHTML = '<div class="doc-search-modal"><div class="doc-search-header">' +
            '<span class="doc-search-icon" aria-hidden="true">⌕</span>' +
            '<input id="docSearchInput" class="doc-search-input" type="text" autocomplete="off" />' +
            '<button id="docSearchClose" class="doc-search-close">Esc</button></div>' +
            '<div id="docSearchResults" class="doc-search-results" aria-live="polite"></div>' +
            '<div class="doc-search-footer"><span><kbd>↑</kbd> <kbd>↓</kbd> ' + escapeHtml(text.navigate) +
            '</span><span><kbd>↵</kbd> ' + escapeHtml(text.select) + '</span><span><kbd>Esc</kbd></span></div></div>';
        document.body.appendChild(modal);
        input = document.getElementById('docSearchInput');
        results = document.getElementById('docSearchResults');
        input.placeholder = text.placeholder;
        input.setAttribute('aria-label', text.title);
        input.setAttribute('aria-controls', 'docSearchResults');
        var close = document.getElementById('docSearchClose');
        close.setAttribute('aria-label', text.close);
        close.addEventListener('click', closeSearch);
        input.addEventListener('input', search);
        modal.addEventListener('click', function (e) { if (e.target === modal) closeSearch(); });
        modal.addEventListener('keydown', handleKeys);
    }
    function openSearch() {
        closeDrawer();
        createModal();
        if (modal.classList.contains('active')) { input.focus(); return; }
        opener = document.activeElement;
        previousOverflow = document.body.style.overflow;
        inertState = Array.prototype.filter.call(document.body.children, function (node) { return node !== modal; })
            .map(function (node) { var old = node.inert; node.inert = true; return { node: node, old: old }; });
        document.body.style.overflow = 'hidden';
        modal.classList.add('active');
        input.focus();
        loadIndex();
    }
    function closeSearch() {
        if (!modal || !modal.classList.contains('active')) return;
        modal.classList.remove('active');
        inertState.forEach(function (entry) { entry.node.inert = entry.old; });
        inertState = [];
        document.body.style.overflow = previousOverflow;
        activeIndex = -1;
        if (opener && opener.isConnected) opener.focus();
    }
    function excerpt(page, query) {
        var body = page.body || '', lower = body.toLowerCase();
        var terms = query.toLowerCase().split(/\s+/).filter(Boolean);
        var at = -1;
        terms.some(function (term) { at = lower.indexOf(term); return at >= 0; });
        if (at < 0) return page.description || '';
        var start = Math.max(0, at - 65);
        return (start ? '…' : '') + body.substring(start, start + 230) + (start + 230 < body.length ? '…' : '');
    }
    function search() {
        activeIndex = -1;
        input.removeAttribute('aria-describedby');
        if (!index) return;
        results.setAttribute('aria-busy', 'false');
        var query = input.value.trim(), q = query.toLowerCase();
        if (!q) { status(text.prompt); return; }
        var terms = q.split(/\s+/).filter(Boolean);
        var matches = [];
        index.forEach(function (page) {
            var title = (page.title || '').toLowerCase();
            var headings = (page.headings || []).join(' ').toLowerCase();
            var description = (page.description || '').toLowerCase();
            var body = (page.body || '').toLowerCase();
            var keywords = (page.keywords || '').toLowerCase();
            var all = [title, headings, description, body, keywords].join(' ');
            if (!terms.every(function (term) { return all.indexOf(term) >= 0; })) return;
            var score = title === q ? 100 : title.indexOf(q) >= 0 ? 50 : 0;
            terms.forEach(function (term) {
                score += title.indexOf(term) >= 0 ? 20 : headings.indexOf(term) >= 0 ? 12 :
                    description.indexOf(term) >= 0 ? 8 : 2;
            });
            matches.push({ page: page, score: score, local: page.lang === lang || (!page.lang && lang === 'en') });
        });
        matches.sort(function (a, b) { return Number(b.local) - Number(a.local) || b.score - a.score; });
        if (!matches.length) { status(text.empty); return; }
        results.innerHTML = matches.slice(0, 12).map(function (match, i) {
            var page = match.page;
            var href = page.url.indexOf('documentation/') === 0 ? getDocRelativePrefix() + page.url.substring(14) : page.url;
            // Generated indexes contain relative documentation paths only; do not accept executable URLs.
            if (/^(?:[a-z]+:|\/\/)/i.test(href)) return '';
            var label = match.local ? '' : ' · ' + text.fallback + ': ' + (page.lang || 'en').toUpperCase();
            return '<a href="' + escapeHtml(href) + '" class="doc-search-item" id="docSearchItem' + i + '">' +
                '<div class="doc-search-item-title">' + highlight(page.title, query) + '</div>' +
                '<div class="doc-search-item-desc">' + highlight(excerpt(page, query), query) + '</div>' +
                '<div class="doc-search-item-meta">' + escapeHtml(page.category || text.title) + escapeHtml(label) + '</div></a>';
        }).join('');
    }
    function handleKeys(e) {
        var items = results.querySelectorAll('.doc-search-item');
        if (e.key === 'Escape') { e.preventDefault(); closeSearch(); return; }
        if (e.key === 'Tab') {
            var focusable = modal.querySelectorAll('input, button, a[href]');
            var first = focusable[0], last = focusable[focusable.length - 1];
            if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
            else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
            return;
        }
        if (document.activeElement !== input) return;
        if ((e.key === 'ArrowDown' || e.key === 'ArrowUp') && items.length) {
            e.preventDefault();
            activeIndex = e.key === 'ArrowDown' ? (activeIndex + 1) % items.length :
                (activeIndex < 0 ? items.length - 1 : (activeIndex - 1 + items.length) % items.length);
            items.forEach(function (item, i) {
                item.classList.toggle('selected', i === activeIndex);
                if (i === activeIndex) item.setAttribute('aria-current', 'true');
                else item.removeAttribute('aria-current');
            });
            input.setAttribute('aria-describedby', items[activeIndex].id);
            items[activeIndex].scrollIntoView({ block: 'nearest' });
        } else if (e.key === 'Enter' && items.length) {
            e.preventDefault(); items[Math.max(0, activeIndex)].click();
        }
    }
    var drawer, menuButton, backdrop, drawerOpener;
    var drawerInert = [];
    var drawerOverflow = '';
    function closeDrawer() {
        if (!drawer || !drawer.classList.contains('active')) return;
        drawer.classList.remove('active', 'open');
        if (backdrop) backdrop.classList.remove('active');
        menuButton.setAttribute('aria-expanded', 'false');
        drawerInert.forEach(function (entry) { entry.node.inert = entry.old; });
        drawerInert = [];
        document.body.style.overflow = drawerOverflow;
        if (drawerOpener) drawerOpener.focus();
    }
    function setupNavigation() {
        document.documentElement.setAttribute('data-lang', lang === 'uk' ? 'ua' : lang);
        var languageButton = document.getElementById('langBtn');
        var languageMenu = document.getElementById('langMenu');
        if (languageButton && languageMenu) {
            // Preserve old bookmarked query URLs while using real translated pages for new links.
            var legacy = new URLSearchParams(window.location.search).get('lang');
            var legacyLink = ['en', 'ru', 'uk'].indexOf(legacy) >= 0 ?
                languageMenu.querySelector('a[data-lang="' + legacy + '"]') : null;
            if (legacyLink && legacy !== lang) {
                var destination = new URL(legacyLink.href);
                var params = new URLSearchParams(window.location.search);
                params.delete('lang');
                destination.search = params.toString();
                destination.hash = window.location.hash;
                window.location.replace(destination.href);
                return;
            }
            languageButton.setAttribute('aria-expanded', 'false');
            languageButton.setAttribute('aria-controls', 'langMenu');
            languageButton.setAttribute('aria-label', text.language);
            languageButton.textContent = lang.toUpperCase();
            languageMenu.removeAttribute('role');
            languageButton.addEventListener('click', function (e) {
                e.stopImmediatePropagation();
                var show = languageMenu.classList.toggle('show');
                languageButton.setAttribute('aria-expanded', String(show));
                if (show) languageMenu.querySelector('a[href]').focus();
            }, true);
            languageMenu.querySelectorAll('a[data-lang]').forEach(function (link) {
                link.classList.toggle('active', link.dataset.lang === lang);
                if (link.dataset.lang === lang) link.setAttribute('aria-current', 'page');
                else link.removeAttribute('aria-current');
                link.addEventListener('click', function (e) {
                    e.stopImmediatePropagation();
                    try { localStorage.setItem('sza-lang', link.dataset.lang === 'uk' ? 'ua' : link.dataset.lang); }
                    catch (ignore) { /* Navigation works when storage is unavailable. */ }
                }, true);
            });
            document.addEventListener('click', function (e) {
                if (!e.target.closest('.doc-lang-picker')) {
                    languageMenu.classList.remove('show'); languageButton.setAttribute('aria-expanded', 'false');
                }
            });
        }
        var themeButton = document.getElementById('themeBtn');
        if (themeButton) themeButton.addEventListener('click', function (e) {
            e.stopImmediatePropagation();
            var next = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
            document.documentElement.dataset.theme = next;
            try { localStorage.setItem('sza-theme', next); } catch (ignore) { /* Theme still changes. */ }
        }, true);
        drawer = document.querySelector('.doc-sidebar');
        menuButton = document.getElementById('mobileMenuBtn');
        backdrop = document.getElementById('docSidebarBackdrop');
        if (drawer && menuButton) {
            drawer.id = drawer.id || 'docNavigation';
            menuButton.setAttribute('aria-controls', drawer.id);
            menuButton.setAttribute('aria-expanded', 'false');
            menuButton.setAttribute('aria-label', text.menu);
            menuButton.addEventListener('click', function (e) {
                e.stopImmediatePropagation();
                if (drawer.classList.contains('active')) { closeDrawer(); return; }
                drawerOpener = menuButton;
                drawerOverflow = document.body.style.overflow;
                document.body.style.overflow = 'hidden';
                drawerInert = Array.prototype.map.call(document.querySelectorAll('.doc-content, .doc-toc, .doc-footer, .doc-breadcrumbs'), function (node) {
                    var old = node.inert; node.inert = true; return { node: node, old: old };
                });
                drawer.classList.add('active');
                if (backdrop) backdrop.classList.add('active');
                menuButton.setAttribute('aria-expanded', 'true');
                var first = drawer.querySelector('a[href]');
                if (first) first.focus();
            }, true);
            if (backdrop) backdrop.addEventListener('click', function (e) { e.stopImmediatePropagation(); closeDrawer(); }, true);
        }
        window.addEventListener('resize', function () { if (window.innerWidth > 768) closeDrawer(); });
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Tab' && drawer && drawer.classList.contains('active')) {
                var links = drawer.querySelectorAll('a[href]');
                if (links.length && e.shiftKey && document.activeElement === links[0]) { e.preventDefault(); links[links.length - 1].focus(); }
                else if (links.length && !e.shiftKey && document.activeElement === links[links.length - 1]) { e.preventDefault(); links[0].focus(); }
            }
            if (e.key === 'Escape') {
                if (languageMenu && languageMenu.classList.contains('show')) {
                    languageMenu.classList.remove('show'); languageButton.setAttribute('aria-expanded', 'false');
                    languageButton.focus();
                }
                closeDrawer();
            }
        });
    }
    document.addEventListener('keydown', function (e) {
        var focused = document.activeElement;
        if (focused && (focused.matches('input, textarea, select') || focused.isContentEditable)) return;
        if (e.key === '/' || ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k')) {
            e.preventDefault(); openSearch();
        }
    });
    function initialize() {
        setupNavigation();
        document.querySelectorAll('[data-search-trigger]').forEach(function (trigger) {
            trigger.addEventListener('click', function (e) { e.preventDefault(); openSearch(); });
        });
    }
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initialize);
    else initialize();
    window.FastMediaDocsSearch = { open: openSearch, close: closeSearch };
}());
