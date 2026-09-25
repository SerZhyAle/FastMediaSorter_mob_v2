/**
 * FastMediaSorter Documentation - In-Browser Client Search Engine
 * Part of S2970 (Documentation Site HTML & In-Browser Search)
 */
(function () {
    'use strict';

    var searchIndex = [];
    var isIndexLoaded = false;
    var modalElement = null;
    var inputElement = null;
    var resultsElement = null;
    var activeIndex = -1;

    // 1. Resolve relative path to search-index.json
    function getSearchIndexPath() {
        var currentPath = window.location.pathname;
        if (currentPath.indexOf('/design-system/') !== -1 || currentPath.indexOf('/getting-started/') !== -1 ||
            currentPath.indexOf('/storage/') !== -1 || currentPath.indexOf('/network/') !== -1 ||
            currentPath.indexOf('/player/') !== -1 || currentPath.indexOf('/audio/') !== -1 ||
            currentPath.indexOf('/settings/') !== -1 || currentPath.indexOf('/programs/') !== -1) {
            return '../assets/search-index.json';
        }
        return 'assets/search-index.json';
    }

    function loadSearchIndex() {
        if (isIndexLoaded) return;
        var path = getSearchIndexPath();
        fetch(path)
            .then(function (res) { return res.json(); })
            .then(function (data) {
                searchIndex = data.pages || [];
                isIndexLoaded = true;
            })
            .catch(function (err) {
                console.warn('Search index load failed:', err);
            });
    }

    // 2. Build DOM Modal if not present
    function createSearchModal() {
        if (modalElement) return;

        var backdrop = document.createElement('div');
        backdrop.className = 'doc-search-backdrop';
        backdrop.id = 'docSearchModal';
        backdrop.setAttribute('role', 'dialog');
        backdrop.setAttribute('aria-modal', 'true');
        backdrop.setAttribute('aria-label', 'Documentation Search');

        backdrop.innerHTML = [
            '<div class="doc-search-modal">',
            '  <div class="doc-search-header">',
            '    <span class="doc-search-icon">🔍</span>',
            '    <input type="text" class="doc-search-input" id="docSearchInput" placeholder="Search documentation, recipes, and features..." aria-autocomplete="list" autocomplete="off" />',
            '    <button class="doc-search-close" id="docSearchClose" aria-label="Close search (Esc)">Esc</button>',
            '  </div>',
            '  <div class="doc-search-results" id="docSearchResults">',
            '    <div class="doc-search-empty">Type a keyword, feature, or recipe name to search...</div>',
            '  </div>',
            '  <div class="doc-search-footer">',
            '    <span><kbd>↑</kbd> <kbd>↓</kbd> to navigate</span>',
            '    <span><kbd>↵</kbd> to select</span>',
            '    <span><kbd>Esc</kbd> to close</span>',
            '  </div>',
            '</div>'
        ].join('\n');

        document.body.appendChild(backdrop);
        modalElement = backdrop;
        inputElement = document.getElementById('docSearchInput');
        resultsElement = document.getElementById('docSearchResults');

        // Close on backdrop click
        backdrop.addEventListener('click', function (e) {
            if (e.target === backdrop) closeSearch();
        });

        document.getElementById('docSearchClose').addEventListener('click', closeSearch);

        // Input listener
        inputElement.addEventListener('input', function () {
            performSearch(inputElement.value.trim());
        });

        // Keyboard navigation inside modal
        inputElement.addEventListener('keydown', handleModalKeys);
    }

    function openSearch() {
        createSearchModal();
        loadSearchIndex();
        modalElement.classList.add('active');
        document.body.style.overflow = 'hidden';
        setTimeout(function () {
            inputElement.focus();
            if (inputElement.value.trim()) {
                performSearch(inputElement.value.trim());
            }
        }, 50);
    }

    function closeSearch() {
        if (!modalElement) return;
        modalElement.classList.remove('active');
        document.body.style.overflow = '';
        activeIndex = -1;
    }

    // 3. Search Matching & Scoring
    function performSearch(query) {
        activeIndex = -1;
        if (!query) {
            resultsElement.innerHTML = '<div class="doc-search-empty">Type a keyword, feature, or recipe name to search...</div>';
            return;
        }

        var q = query.toLowerCase();
        var terms = q.split(/\s+/).filter(function (t) { return t.length > 0; });
        var matches = [];

        var currentPath = window.location.pathname;
        var activeLang = (currentPath.indexOf('-ru.html') !== -1 || localStorage.getItem('sza-docs-lang') === 'ru') ? 'ru' : 'en';

        for (var i = 0; i < searchIndex.length; i++) {
            var item = searchIndex[i];
            var score = 0;
            var titleLower = (item.title || '').toLowerCase();
            var descLower = (item.description || '').toLowerCase();
            var keywordsLower = (item.keywords || '').toLowerCase();
            var categoryLower = (item.category || '').toLowerCase();

            // Language match weighting
            if (item.lang === activeLang || (!item.lang && activeLang === 'en')) {
                score += 15;
            }

            // Score evaluation
            if (titleLower === q) score += 100;
            else if (titleLower.indexOf(q) === 0) score += 50;
            else if (titleLower.indexOf(q) !== -1) score += 30;

            if (categoryLower.indexOf(q) !== -1) score += 20;
            if (descLower.indexOf(q) !== -1) score += 10;
            if (keywordsLower.indexOf(q) !== -1) score += 5;

            // Check multi-term overlap
            var allTermsMatch = true;
            for (var t = 0; t < terms.length; t++) {
                if (keywordsLower.indexOf(terms[t]) === -1) {
                    allTermsMatch = false;
                    break;
                }
            }
            if (allTermsMatch && terms.length > 1) score += 25;

            if (score > 0) {
                matches.push({ item: item, score: score });
            }
        }

        matches.sort(function (a, b) { return b.score - a.score; });
        renderResults(matches, query);
    }

    function highlightTokens(text, query) {
        if (!text || !query) return text || '';
        var safeQuery = query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
        var regex = new RegExp('(' + safeQuery + ')', 'gi');
        return text.replace(regex, '<mark class="doc-search-highlight">$1</mark>');
    }

    function renderResults(matches, query) {
        if (matches.length === 0) {
            resultsElement.innerHTML = '<div class="doc-search-empty">No documentation matches found for "<strong>' + escapeHtml(query) + '</strong>".</div>';
            return;
        }

        var html = '';
        var limit = Math.min(matches.length, 12);
        for (var i = 0; i < limit; i++) {
            var p = matches[i].item;
            var badgeText = p.published ? 'Published' : 'Planned (' + (p.ticket || 'S2970') + ')';
            var badgeClass = p.published ? 'doc-badge-standard' : 'doc-badge-sm';
            var targetHref = p.url || '#';

            // Resolve relative link from current directory
            var currentPath = window.location.pathname;
            var prefix = (currentPath.indexOf('/design-system/') !== -1 || currentPath.indexOf('/getting-started/') !== -1 ||
                          currentPath.indexOf('/storage/') !== -1 || currentPath.indexOf('/network/') !== -1 ||
                          currentPath.indexOf('/player/') !== -1 || currentPath.indexOf('/audio/') !== -1 ||
                          currentPath.indexOf('/settings/') !== -1 || currentPath.indexOf('/programs/') !== -1) ? '../' : '';
            
            var resolvedHref = targetHref.indexOf('documentation/') === 0 ? prefix + targetHref.substring('documentation/'.length) : targetHref;

            html += [
                '<a href="' + resolvedHref + '" class="doc-search-item" data-index="' + i + '">',
                '  <div class="doc-search-item-title">',
                '    <span>' + highlightTokens(escapeHtml(p.title), query) + '</span>',
                '    <span class="doc-badge ' + badgeClass + '">' + badgeText + '</span>',
                '  </div>',
                '  <div class="doc-search-item-desc">' + highlightTokens(escapeHtml(p.description || ''), query) + '</div>',
                '  <div class="doc-search-item-meta">' + escapeHtml(p.category || 'Guide') + ' &bull; ' + escapeHtml(p.ticket || 'Corpus') + '</div>',
                '</a>'
            ].join('\n');
        }

        resultsElement.innerHTML = html;
    }

    function handleModalKeys(e) {
        var items = resultsElement.querySelectorAll('.doc-search-item');
        if (e.key === 'Escape') {
            closeSearch();
            e.preventDefault();
        } else if (e.key === 'ArrowDown') {
            e.preventDefault();
            if (items.length === 0) return;
            activeIndex = (activeIndex + 1) % items.length;
            updateActiveItem(items);
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            if (items.length === 0) return;
            activeIndex = (activeIndex - 1 + items.length) % items.length;
            updateActiveItem(items);
        } else if (e.key === 'Enter') {
            if (activeIndex >= 0 && activeIndex < items.length) {
                e.preventDefault();
                items[activeIndex].click();
            }
        }
    }

    function updateActiveItem(items) {
        for (var i = 0; i < items.length; i++) {
            if (i === activeIndex) {
                items[i].classList.add('selected');
                items[i].scrollIntoView({ block: 'nearest' });
            } else {
                items[i].classList.remove('selected');
            }
        }
    }

    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    // 4. Global Keyboard Handler (/ or Ctrl+K)
    document.addEventListener('keydown', function (e) {
        var activeTag = (document.activeElement && document.activeElement.tagName) || '';
        if (activeTag === 'INPUT' || activeTag === 'TEXTAREA') return;

        if (e.key === '/' || ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k')) {
            e.preventDefault();
            openSearch();
        }
    });

    // Wire global click handler on any button with [data-search-trigger]
    document.addEventListener('DOMContentLoaded', function () {
        var triggers = document.querySelectorAll('[data-search-trigger]');
        for (var i = 0; i < triggers.length; i++) {
            triggers[i].addEventListener('click', function (e) {
                e.preventDefault();
                openSearch();
            });
        }
    });

    // Expose API
    window.FastMediaDocsSearch = {
        open: openSearch,
        close: closeSearch
    };
})();
