        (function () {
            var allFeatures = [];
            var activeFilter = 'all'; // 'all', 'standard', 'vr'
            var searchQuery = '';
            var activeVariant = 'v1'; // 'v1', 'v2', 'v3', 'v4'
            var activeCategoryV1 = null;
            var activeToggleV3 = 'key'; // 'key', 'all'
            var activeScenarioV4 = 'all';
            var featuresPage = 'docs/FEATURES-ru.html';

            // FEATURES-ru.md is curated to one entry per capability; no noise rows to suppress.
            var hiddenFeatureTitles = [];

            // FEATURES opens every pillar with its standout capabilities, so position marks a key feature
            // and no title list can go stale when a bullet is renamed.
            var KEY_FEATURES_PER_PILLAR = 2;

            // Columns name FEATURES pillars by number, which every locale shares.
            var dashboardColumns = [
                { id: 'shell', label: 'Оболочка и запуск', categories: [1, 4] },
                { id: 'media', label: 'Медиа и трансляции', categories: [2, 3] },
                { id: 'tools', label: 'Инструменты и мониторинг', categories: [5, 7] },
                { id: 'files', label: 'Файлы и часы', categories: [6, 8] }
            ];

            function pillarNumber(f) {
                var numMatch = f.category.match(/^(\d+)/);
                return numMatch ? parseInt(numMatch[1], 10) : -1;
            }

            function pillarLabel(category) {
                return category.replace(/^\d+\.\s*/, '');
            }

            // A pillar added to FEATURES later gets a column of its own instead of vanishing from the map.
            function columnsForPillars(features) {
                var columns = dashboardColumns.slice();
                features.forEach(function (f) {
                    var num = pillarNumber(f);
                    var covered = columns.some(function (c) { return c.categories.indexOf(num) !== -1; });
                    if (!covered) {
                        columns.push({ id: 'pillar-' + num, label: pillarLabel(f.category), categories: [num] });
                    }
                });
                return columns;
            }

            // A FEATURES tag lists every flavor that ships the capability ("Standard / Lite / VR / noLegal");
            // the older "VR Only" form still reads as the single flavor it names.
            function hasFlavor(f, flavor) {
                return f.tag.split('/').some(function (t) { return t.trim().replace(/ Only$/, '') === flavor; });
            }

            function flavorKind(f) {
                var standard = hasFlavor(f, 'Standard');
                var vr = hasFlavor(f, 'VR');
                if (vr && !standard) return 'vr';
                if (standard && !vr) return 'standard';
                return 'common';
            }

            var scenariosList = [
                { id: 'launcher', label: 'Домашний экран' },
                { id: 'watch', label: 'Спутник на часах' },
                { id: 'radio', label: 'Трансляции' },
                { id: 'all', label: 'Все сценарии' },
                { id: 'photo', label: 'Фоторамка' },
                { id: 'music', label: 'В авто' },
                { id: 'video', label: 'Кинотеатр' },
                { id: 'smb', label: 'Настройка SMB' },
                { id: 'backup', label: 'Автобэкап' },
                { id: 'download', label: 'Органайзер' },
                { id: 'offline', label: 'Офлайн-папка' },
                { id: 'docs', label: 'Документы из облака' },
                { id: 'ocr', label: 'OCR-перевод' },
                { id: 'tv', label: 'NAS для TV' },
                { id: 'duplicates', label: 'Очистка дубликатов' },
                { id: 'chromecast', label: 'Chromecast' },
                { id: 'widgets', label: 'Умные виджеты' },
                { id: 'vrcinema', label: 'Иммерсивный кинотеатр' },
                { id: 'calculator', label: 'Быстрые вычисления' },
                { id: 'cloudnotes', label: 'Облачные заметки' }
            ];

            // A scenario matches words inside each FEATURES bullet, so a renamed bullet stays in its scenario;
            // the earlier list of exact bullet titles went stale and left every scenario pill empty.
            var scenarioKeywords = {
                launcher: ['launcher', 'desktop', 'home screen', 'taskbar', 'start menu', 'лаунчер', 'рабоч', 'домашн', 'панель задач', 'робоч', 'пуск'],
                watch: ['watch', 'wear', 'час', 'годин'],
                radio: ['radio', 'stream', 'iptv', 'радио', 'радіо', 'трансляц', 'поток', 'потік'],
                photo: ['слайд-шоу', 'изображени', 'gif', 'webp', 'картинк'],
                music: ['музык', 'аудио', 'радио', 'звук', 'стерео', 'текст песн'],
                video: ['видео', 'субтитр', 'фильм', 'кино', 'телевизор'],
                smb: ['smb', 'nas', 'sftp', 'ftp', 'папки пк'],
                backup: ['резервн', 'восстановлени', 'съёмка сразу', 'виджет камеры'],
                download: ['загрузк', 'сортировк', 'файлового менеджера', 'корзин', 'папки целиком'],
                offline: ['офлайн', 'локально', 'без обязательной отправки', 'карты памяти', 'съёмные'],
                docs: ['pdf', 'epub', 'документ', 'markdown', '.txt'],
                ocr: ['ocr', 'перевод', 'распозна'],
                tv: ['телевизор', 'd-pad', 'nas'],
                duplicates: ['дубликат', 'одинаковые', 'корзин', 'отмен', 'очистк'],
                chromecast: ['транслируйте', 'телевизор', 'вещани'],
                widgets: ['виджет'],
                vrcinema: ['шлем', 'openxr', 'vr180', '360', 'виртуальном экране', 'sbs'],
                calculator: ['калькулятор', 'вычислен', 'секундомер', 'мини-программ'],
                cloudnotes: ['markdown', 'заметк', '.txt', 'google drive']
            };

            function featureMentions(f, keyword) {
                return (f.title + ' ' + f.text).toLowerCase().indexOf(keyword) !== -1;
            }

            function matchesScenario(f, scenarioId) {
                return (scenarioKeywords[scenarioId] || []).some(function (k) { return featureMentions(f, k); });
            }

            // FEATURES pillars get renumbered and renamed between releases, so a scenario links to the pillar
            // holding its primary capability today instead of to a remembered anchor.
            function scenarioPillarUrl(scenarioId) {
                var keywords = scenarioKeywords[scenarioId] || [];
                for (var i = 0; i < keywords.length; i++) {
                    var hit = allFeatures.find(function (f) { return featureMentions(f, keywords[i]); });
                    if (hit) return featuresPage + '#' + hit.anchor;
                }
                return null;
            }

            // Mirrors Jekyll's GFM heading ids for a page rendered without them.
            function headingAnchor(node) {
                return node.id || node.textContent.trim().toLowerCase().replace(/[^\p{L}\p{N}_\s-]/gu, '').replace(/[ \t]/g, '-');
            }

            function getScenarioLinks(title, desc) {
                var text = (title + ' ' + desc).toLowerCase();
                var links = [];

                if (text.indexOf('слайд-шоу') !== -1 || text.indexOf('фоторамк') !== -1) {
                    links.push({ url: 'documentation/images/slideshow-and-transitions-ru.html', label: 'Рецепт: Фоторамка' });
                }
                if (text.indexOf('музык') !== -1 || text.indexOf('аудио') !== -1 || text.indexOf('midi') !== -1 || text.indexOf('рул') !== -1) {
                    links.push({ url: 'documentation/audio/playing-and-organizing-music-ru.html', label: 'Рецепт: В авто' });
                }
                if (text.indexOf('видео') !== -1 || text.indexOf('фильм') !== -1 || text.indexOf('сериал') !== -1 || text.indexOf('3d') !== -1 || text.indexOf('кинотеатр') !== -1) {
                    links.push({ url: 'documentation/player/video-playback-controls-ru.html', label: 'Рецепт: Кинотеатр' });
                }
                if (text.indexOf('smb') !== -1 || text.indexOf('сетев') !== -1 || text.indexOf('nas') !== -1 || text.indexOf('хост') !== -1) {
                    links.push({ url: 'documentation/network/smb-samba-shares-ru.html', label: 'Настройка SMB' });
                }
                if (text.indexOf('бэкап') !== -1 || text.indexOf('резерв') !== -1 || text.indexOf('копир') !== -1) {
                    links.push({ url: 'documentation/storage/scheduled-operations-ru.html', label: 'Автобэкап' });
                }
                if (text.indexOf('загруз') !== -1 || text.indexOf('козин') !== -1 || text.indexOf('удален') !== -1 || text.indexOf('перемещ') !== -1) {
                    links.push({ url: 'documentation/storage/destination-targets-setup-ru.html', label: 'Органайзер' });
                }
                if (text.indexOf('офлайн') !== -1 || text.indexOf('поезд') !== -1 || text.indexOf('локальн') !== -1) {
                    links.push({ url: 'documentation/network/network-sync-and-cache-ru.html', label: 'Офлайн-папка' });
                }
                if (text.indexOf('документ') !== -1 || text.indexOf('pdf') !== -1 || text.indexOf('epub') !== -1 || text.indexOf('чтен') !== -1) {
                    links.push({ url: 'documentation/documents/pdf-epub-viewing-ru.html', label: 'Документы из облака' });
                }
                if (text.indexOf('ocr') !== -1 || text.indexOf('перевод') !== -1 || text.indexOf('tesseract') !== -1 || text.indexOf('распознав') !== -1) {
                    links.push({ url: 'documentation/tools/ocr-text-recognition-ru.html', label: 'OCR-перевод' });
                }
                if (text.indexOf('tv') !== -1 || text.indexOf('гостин') !== -1 || text.indexOf('телевиз') !== -1) {
                    links.push({ url: 'documentation/network/smb-samba-shares-ru.html', label: 'NAS для TV' });
                }
                if (text.indexOf('watch') !== -1 || text.indexOf('wear') !== -1 || text.indexOf('час') !== -1 || text.indexOf('смарт-час') !== -1) {
                    links.push({ url: 'documentation/wear/standalone-music-playback-ru.html', label: 'Сценарий: Музыка на часах' });
                    links.push({ url: 'documentation/wear/phone-and-network-folders-on-watch-ru.html', label: 'Сценарий: Сеть на часах' });
                }
                if (text.indexOf('дублик') !== -1 || text.indexOf('sha-256') !== -1) {
                    var duplicatesUrl = scenarioPillarUrl('duplicates');
                    if (duplicatesUrl) links.push({ url: duplicatesUrl, label: 'Очистка дубликатов' });
                }
                if (text.indexOf('chromecast') !== -1 || text.indexOf('cast') !== -1) {
                    var chromecastUrl = scenarioPillarUrl('chromecast');
                    if (chromecastUrl) links.push({ url: chromecastUrl, label: 'Chromecast' });
                }
                if (text.indexOf('виджет') !== -1 || text.indexOf('widget') !== -1) {
                    links.push({ url: 'documentation/launcher/home-screen-widgets-ru.html', label: 'Умные виджеты' });
                }
                if (text.indexOf('vrcinema') !== -1 || text.indexOf('кинотеатр') !== -1 || text.indexOf('openxr') !== -1) {
                    links.push({ url: 'documentation/vr/spatial-cinema-playback-ru.html', label: 'Иммерсивный кинотеатр' });
                }
                if (text.indexOf('вычислен') !== -1 || text.indexOf('формул') !== -1 || text.indexOf('калькулятор') !== -1 || text.indexOf('расчёт') !== -1 || text.indexOf('math') !== -1 || text.indexOf('calculation') !== -1) {
                    links.push({ url: 'documentation/programs/calculator-and-stopwatch-ru.html', label: 'Быстрые вычисления' });
                }
                if (text.indexOf('cloudnotes') !== -1 || text.indexOf('заметк') !== -1 || text.indexOf('markdown') !== -1) {
                    links.push({ url: 'documentation/documents/text-code-editor-ru.html', label: 'Облачные заметки' });
                }

                return links;
            }

            function mapTitleToScenario(title) {
                var t = title.toLowerCase();
                if (t.indexOf('рамк') !== -1 || t.indexOf('frame') !== -1) return 'photo';
                if (t.indexOf('муз') !== -1 || t.indexOf('music') !== -1) return 'music';
                if (t.indexOf('кино') !== -1 || t.indexOf('cinema') !== -1) return 'video';
                if (t.indexOf('орган') !== -1 || t.indexOf('organizer') !== -1) return 'download';
                if (t.indexOf('бекап') !== -1 || t.indexOf('бэкап') !== -1 || t.indexOf('backup') !== -1) return 'backup';
                if (t.indexOf('smb') !== -1 || t.indexOf('сет') !== -1) return 'smb';
                if (t.indexOf('поез') !== -1 || t.indexOf('travel') !== -1 || t.indexOf('офлайн') !== -1 || t.indexOf('offline') !== -1) return 'offline';
                if (t.indexOf('облак') !== -1 || t.indexOf('docs') !== -1 || t.indexOf('cloud') !== -1) return 'docs';
                if (t.indexOf('ocr') !== -1 || t.indexOf('перевод') !== -1 || t.indexOf('translation') !== -1) return 'ocr';
                if (t.indexOf('tv') !== -1 || t.indexOf('тв') !== -1 || t.indexOf('телевиз') !== -1) return 'tv';
                if (t.indexOf('дубл') !== -1 || t.indexOf('duplicates') !== -1 || t.indexOf('cleanup') !== -1) return 'duplicates';
                if (t.indexOf('chromecast') !== -1 || t.indexOf('cast') !== -1) return 'chromecast';
                if (t.indexOf('виджет') !== -1 || t.indexOf('widget') !== -1) return 'widgets';
                if (t.indexOf('openxr') !== -1 || t.indexOf('кинотеатр') !== -1) return 'vrcinema';
                if (t.indexOf('вычислен') !== -1 || t.indexOf('расчёт') !== -1 || t.indexOf('math') !== -1) return 'calculator';
                if (t.indexOf('заметк') !== -1 || t.indexOf('markdown') !== -1) return 'cloudnotes';
                return null;
            }

            function renderExplorer() {
                if (!allFeatures.length) return;
                var grid = document.getElementById('featureExplorerGrid');
                grid.innerHTML = '';

                // Скрыть/показать специфичные контроли вариантов
                document.getElementById('toggleV3Controls').style.display = (activeVariant === 'v3') ? 'flex' : 'none';
                document.getElementById('scenariosPillsContainer').style.display = (activeVariant === 'v4') ? 'flex' : 'none';

                var filtered = allFeatures.filter(function (f) {
                    // Фильтр по платформе
                    if (activeFilter === 'standard') {
                        if (!hasFlavor(f, 'Standard')) return false;
                    } else if (activeFilter === 'vr') {
                        if (!hasFlavor(f, 'VR')) return false;
                    }

                    // Фильтр по поиску
                    if (searchQuery) {
                        var q = searchQuery.toLowerCase();
                        return f.title.toLowerCase().indexOf(q) !== -1 ||
                            f.text.toLowerCase().indexOf(q) !== -1 ||
                            f.category.toLowerCase().indexOf(q) !== -1;
                    }

                    return true;
                });

                if (filtered.length === 0) {
                    grid.innerHTML = '<div style="grid-column: 1/-1; text-align: center; color: var(--text-muted); padding: 3rem 0;">Ничего не найдено. Попробуйте изменить запрос.</div>';
                    return;
                }

                function getFeatureCardHtml(f, forceOpen) {
                    var kind = flavorKind(f);
                    var tagClass = 'tag-' + kind;
                    var badgeClass = 'badge-' + kind;
                    var badgeLabel = f.tag;

                    var headerHtml = '<span class="feature-header">' +
                        '<span class="feature-title">' + f.htmlTitle + '</span>' +
                        '<span style="display: flex; align-items: center; gap: 0.5rem; flex-shrink: 0;">' +
                        '<span class="feature-badge ' + badgeClass + '">' + badgeLabel + '</span>' +
                        (forceOpen ? '' : '<span class="toggle-icon">▼</span>') +
                        '</span>' +
                        '</span>';

                    var descHtml = '<div class="feature-desc">' + f.desc + '</div>';

                    var linksHtml = '';
                    var scLinks = getScenarioLinks(f.title, f.text);
                    if (scLinks.length > 0) {
                        linksHtml = '<div class="feature-links">';
                        scLinks.forEach(function (l) {
                            linksHtml += '<a href="' + l.url + '" class="feature-link" target="_blank">' + l.label + '</a>';
                        });
                        linksHtml += '</div>';
                    }

                    if (forceOpen) {
                        return '<div class="feature-card ' + tagClass + '">' +
                            headerHtml + descHtml + linksHtml +
                            '</div>';
                    } else {
                        return '<div class="feature-card ' + tagClass + '">' +
                            '<details class="feature-details">' +
                            '<summary>' + headerHtml + '</summary>' +
                            descHtml + linksHtml +
                            '</details>' +
                            '</div>';
                    }
                }

                if (activeVariant === 'v1') {
                    // --- Вариант 1: Category Explorer ---
                    var grouped = {};
                    var availableCats = [];
                    filtered.forEach(function (f) {
                        if (!grouped[f.category]) {
                            grouped[f.category] = [];
                            availableCats.push({ id: f.category, label: pillarLabel(f.category) });
                        }
                        grouped[f.category].push(f);
                    });

                    if (availableCats.length === 0) {
                        grid.innerHTML = '<div style="grid-column: 1/-1; text-align: center; color: var(--text-muted); padding: 3rem 0;">Нет доступных категорий для текущих фильтров.</div>';
                        return;
                    }
                    if (!activeCategoryV1 || !grouped[activeCategoryV1] || grouped[activeCategoryV1].length === 0) {
                        activeCategoryV1 = availableCats[0].id;
                    }

                    var container = document.createElement('div');
                    container.className = 'explorer-v1-container';

                    var sidebar = document.createElement('div');
                    sidebar.className = 'explorer-v1-sidebar';
                    availableCats.forEach(function (cat) {
                        var btn = document.createElement('button');
                        btn.className = 'v1-cat-btn' + (cat.id === activeCategoryV1 ? ' active' : '');
                        btn.innerText = cat.label;
                        btn.addEventListener('click', function () {
                            activeCategoryV1 = cat.id;
                            renderExplorer();
                            grid.querySelector('.v1-cat-btn.active').focus({ preventScroll: true });
                        });
                        sidebar.appendChild(btn);
                    });

                    var content = document.createElement('div');
                    content.className = 'explorer-v1-content';

                    var catFeatures = grouped[activeCategoryV1];
                    var majorList = [];
                    var minorList = [];
                    catFeatures.forEach(function (f) {
                        if (f.isKey) {
                            majorList.push(f);
                        } else {
                            minorList.push(f);
                        }
                    });

                    if (majorList.length > 0) {
                        var majorGrid = document.createElement('div');
                        majorGrid.className = 'feature-grid';
                        majorList.forEach(function (f) {
                            majorGrid.innerHTML += getFeatureCardHtml(f, true);
                        });
                        content.appendChild(majorGrid);
                    }

                    if (minorList.length > 0) {
                        var minorTitle = document.createElement('h3');
                        minorTitle.className = 'v1-accordion-title';
                        minorTitle.innerText = 'Другие функции';
                        content.appendChild(minorTitle);

                        var minorGrid = document.createElement('div');
                        minorGrid.className = 'feature-grid';
                        minorList.forEach(function (f) {
                            minorGrid.innerHTML += getFeatureCardHtml(f, false);
                        });
                        content.appendChild(minorGrid);
                    }

                    container.appendChild(sidebar);
                    container.appendChild(content);
                    grid.appendChild(container);

                } else if (activeVariant === 'v2') {
                    // --- Вариант 2: Media Dashboard ---
                    var container = document.createElement('div');
                    container.className = 'dashboard-v2-grid';

                    columnsForPillars(filtered).forEach(function (col) {
                        var colFeatures = filtered.filter(function (f) {
                            return col.categories.indexOf(pillarNumber(f)) !== -1;
                        });

                        if (colFeatures.length === 0) return;

                        var colDiv = document.createElement('div');
                        colDiv.className = 'dashboard-v2-col';

                        var header = document.createElement('div');
                        header.className = 'dashboard-v2-header';
                        header.innerText = col.label;
                        colDiv.appendChild(header);

                        var list = document.createElement('div');
                        list.className = 'dashboard-v2-list';
                        colFeatures.forEach(function (f) {
                            var item = document.createElement('button');
                            item.type = 'button';
                            item.className = 'dashboard-v2-item';
                            
                            var tagBadge = '';
                            if (flavorKind(f) === 'vr') tagBadge = ' <span style="font-size:0.65rem; color:#22d3ee; border:1px solid rgba(0,229,255,0.3); padding:0.1rem 0.3rem; border-radius:4px; margin-left:0.5rem; text-transform:uppercase;">VR</span>';
                            
                            item.innerHTML = '<span>' + f.htmlTitle + tagBadge + '</span><span class="dashboard-v2-item-arrow">→</span>';
                            item.addEventListener('click', function () {
                                openDrawer(f);
                            });
                            list.appendChild(item);
                        });
                        colDiv.appendChild(list);
                        container.appendChild(colDiv);
                    });

                    grid.appendChild(container);

                } else if (activeVariant === 'v3') {
                    // --- Вариант 3: Highlights / All ---
                    if (activeToggleV3 === 'key') {
                        var majorGrid = document.createElement('div');
                        majorGrid.className = 'major-grid-v3';

                        var keyList = filtered.filter(function (f) {
                            return f.isKey;
                        });

                        if (keyList.length === 0) {
                            grid.innerHTML = '<div style="grid-column: 1/-1; text-align: center; color: var(--text-muted); padding: 3rem 0;">В текущих фильтрах ключевые функции отсутствуют.</div>';
                            return;
                        }

                        keyList.forEach(function (f) {
                            var tagClass = 'tag-' + flavorKind(f);

                            var scLinks = getScenarioLinks(f.title, f.text);
                            var linksHtml = '';
                            if (scLinks.length > 0) {
                                linksHtml = '<div class="feature-links">';
                                scLinks.forEach(function (l) {
                                    linksHtml += '<a href="' + l.url + '" class="feature-link" target="_blank">' + l.label + '</a>';
                                });
                                linksHtml += '</div>';
                            }

                            var card = document.createElement('div');
                            card.className = 'feature-card-major ' + tagClass;
                            card.innerHTML = '<div>' +
                                '<div style="display:flex; justify-content:space-between; align-items:center; margin-bottom: 0.5rem;">' +
                                '<span class="feature-title">' + f.htmlTitle + '</span>' +
                                '<span class="feature-badge badge-' + flavorKind(f) + '">' + f.tag + '</span>' +
                                '</div>' +
                                '<div class="feature-desc">' + f.desc + '</div>' +
                                '</div>' + linksHtml;
                            majorGrid.appendChild(card);
                        });
                        grid.appendChild(majorGrid);
                    } else {
                        var treeContainer = document.createElement('div');
                        treeContainer.className = 'compact-tree-container';

                        var categories = [];
                        filtered.forEach(function (f) {
                            if (categories.indexOf(f.category) === -1) {
                                categories.push(f.category);
                            }
                        });

                        categories.forEach(function (cat) {
                            var catFeatures = filtered.filter(function (f) { return f.category === cat; });
                            var catDiv = document.createElement('div');
                            catDiv.className = 'compact-tree-cat';

                            var title = document.createElement('div');
                            title.className = 'compact-tree-cat-title';
                            title.innerText = cat;
                            catDiv.appendChild(title);

                            var list = document.createElement('div');
                            list.className = 'compact-tree-list';
                            catFeatures.forEach(function (f) {
                                var item = document.createElement('div');
                                item.className = 'compact-tree-item';
                                var tagBadge = ' <span style="font-size:0.65rem; opacity:0.7;">[' + f.tag + ']</span>';
                                item.innerHTML = '<span class="compact-tree-item-text"><strong class="compact-tree-item-title">' + f.htmlTitle + '</strong>' + tagBadge + ': ' + f.desc + '</span>';
                                list.appendChild(item);
                            });
                            catDiv.appendChild(list);
                            treeContainer.appendChild(catDiv);
                        });

                        grid.appendChild(treeContainer);
                    }

                } else if (activeVariant === 'v4') {
                    // --- Вариант 4: Scenario Filter ---
                    var scenarioFeatures = filtered;
                    if (activeScenarioV4 !== 'all') {
                        scenarioFeatures = filtered.filter(function (f) {
                            return matchesScenario(f, activeScenarioV4);
                        });
                    }

                    if (activeScenarioV4 !== 'all') {
                        var scenarioObj = scenariosList.find(function (s) { return s.id === activeScenarioV4; });
                        var labelText = scenarioObj ? scenarioObj.label : activeScenarioV4;

                        var badge = document.createElement('div');
                        badge.style.gridColumn = '1/-1';
                        badge.innerHTML = '<div class="filter-pill-active">' +
                            '<span>Показаны функции для: <strong>' + labelText + '</strong></span>' +
                            '<button class="clear-filter-btn" id="clearScenarioFilter" aria-label="Сбросить фильтр" title="Сбросить фильтр"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" aria-hidden="true" focusable="false"><line x1="6" y1="6" x2="18" y2="18"/><line x1="18" y1="6" x2="6" y2="18"/></svg></button>' +
                            '</div>';
                        grid.appendChild(badge);
                    }

                    if (scenarioFeatures.length === 0) {
                        var noData = document.createElement('div');
                        noData.style.gridColumn = '1/-1';
                        noData.style.textAlign = 'center';
                        noData.style.color = 'var(--text-muted)';
                        noData.style.padding = '3rem 0';
                        noData.innerText = 'Функций для этого сценария по выбранным фильтрам не найдено.';
                        grid.appendChild(noData);
                    } else {
                        var scenarioGrid = document.createElement('div');
                        scenarioGrid.style.gridColumn = '1/-1';
                        scenarioGrid.className = 'feature-grid';
                        scenarioFeatures.forEach(function (f) {
                            scenarioGrid.innerHTML += getFeatureCardHtml(f, true);
                        });
                        grid.appendChild(scenarioGrid);
                    }

                    var clearBtn = document.getElementById('clearScenarioFilter');
                    if (clearBtn) {
                        clearBtn.addEventListener('click', function () {
                            activeScenarioV4 = 'all';
                            document.querySelectorAll('.scenario-pill-btn').forEach(function (b) {
                                if (b.getAttribute('data-id') === 'all') b.classList.add('active');
                                else b.classList.remove('active');
                            });
                            renderExplorer();
                        });
                    }
                }
            }

            function openDrawer(feature) {
                document.getElementById('drawerTitle').innerText = feature.title;
                document.getElementById('drawerCat').innerText = feature.category;
                document.getElementById('drawerDesc').innerHTML = feature.desc;

                var linksHtml = '';
                var scLinks = getScenarioLinks(feature.title, feature.text);
                if (scLinks.length > 0) {
                    linksHtml = '<div style="margin-top: 1rem; border-top: 1px solid var(--border-glass); padding-top: 1rem;"><div class="detail-drawer-cat" style="margin-bottom: 0.5rem;">Связанные сценарии:</div>';
                    scLinks.forEach(function (l) {
                        linksHtml += '<a href="' + l.url + '" class="feature-link" target="_blank" style="margin-right: 0.8rem; display: inline-block;">' + l.label + '</a>';
                    });
                    linksHtml += '</div>';
                }
                document.getElementById('drawerLinks').innerHTML = linksHtml;

                document.getElementById('detailDrawer').classList.add('open');
                document.getElementById('detailDrawerOverlay').classList.add('open');
                window.SiteUI.openDrawer();
            }

            function closeDrawer() {
                document.getElementById('detailDrawer').classList.remove('open');
                document.getElementById('detailDrawerOverlay').classList.remove('open');
                window.SiteUI.closeDrawer();
            }

            function buildScenarioPills() {
                var container = document.getElementById('scenariosPills');
                if (!container) return;
                container.innerHTML = '';
                scenariosList.forEach(function (sc) {
                    var btn = document.createElement('button');
                    btn.className = 'scenario-pill-btn' + (sc.id === activeScenarioV4 ? ' active' : '');
                    btn.setAttribute('data-id', sc.id);
                    btn.innerText = sc.label;
                    btn.addEventListener('click', function () {
                        document.querySelectorAll('.scenario-pill-btn').forEach(function (b) { b.classList.remove('active'); });
                        btn.classList.add('active');
                        activeScenarioV4 = sc.id;
                        renderExplorer();
                    });
                    container.appendChild(btn);
                });
            }

            function setupSwitcher() {
                var btns = {
                    'v1': document.getElementById('var-btn-v1'),
                    'v2': document.getElementById('var-btn-v2'),
                    'v3': document.getElementById('var-btn-v3'),
                    'v4': document.getElementById('var-btn-v4')
                };

                function setVariant(v) {
                    activeVariant = v;
                    for (var key in btns) {
                        if (btns[key]) {
                            if (key === v) btns[key].classList.add('active');
                            else btns[key].classList.remove('active');
                        }
                    }
                    renderExplorer();
                }

                if (btns.v1) btns.v1.addEventListener('click', function () { setVariant('v1'); });
                if (btns.v2) btns.v2.addEventListener('click', function () { setVariant('v2'); });
                if (btns.v3) btns.v3.addEventListener('click', function () { setVariant('v3'); });
                if (btns.v4) btns.v4.addEventListener('click', function () { setVariant('v4'); });

                var keyBtn = document.getElementById('toggle-v3-key');
                var allBtn = document.getElementById('toggle-v3-all');
                if (keyBtn && allBtn) {
                    keyBtn.addEventListener('click', function () {
                        keyBtn.classList.add('active');
                        allBtn.classList.remove('active');
                        activeToggleV3 = 'key';
                        renderExplorer();
                    });
                    allBtn.addEventListener('click', function () {
                        allBtn.classList.add('active');
                        keyBtn.classList.remove('active');
                        activeToggleV3 = 'all';
                        renderExplorer();
                    });
                }
            }

            function setupDrawer() {
                var closeBtn = document.getElementById('detailDrawerClose');
                var overlay = document.getElementById('detailDrawerOverlay');
                if (closeBtn) closeBtn.addEventListener('click', closeDrawer);
                if (overlay) overlay.addEventListener('click', closeDrawer);
                document.addEventListener('keydown', function (e) {
                    if (e.key === 'Escape') closeDrawer();
                });
            }

            function setupScenarioCards() {
                document.querySelectorAll('.scenario-card').forEach(function (card) {
                    var select = card.querySelector('.scenario-select');
                    if (select) select.disabled = false;
                    card.addEventListener('click', function (e) {
                        if (e.target.tagName === 'A' || e.target.closest('a') || e.target.tagName === 'SUMMARY' || e.target.closest('summary')) return;
                        var h3 = card.querySelector('h3');
                        if (!h3) return;

                        var scId = card.getAttribute('data-scenario');
                        if (scId) {
                            e.preventDefault();
                            activeVariant = 'v4';
                            activeScenarioV4 = scId;

                            document.querySelectorAll('.variant-btn').forEach(function (b) {
                                if (b.id === 'var-btn-v4') b.classList.add('active');
                                else b.classList.remove('active');
                            });

                            document.querySelectorAll('.scenario-pill-btn').forEach(function (b) {
                                if (b.getAttribute('data-id') === scId) b.classList.add('active');
                                else b.classList.remove('active');
                            });

                            renderExplorer();
                            document.querySelector('.explorer-panel').scrollIntoView({ behavior: 'smooth' });
                            var selected = document.querySelector('.scenario-pill-btn.active');
                            if (selected) selected.focus({ preventScroll: true });
                        }
                    });
                });
            }

            // Controls remain usable when the inventory request fails.
            setupSwitcher();
            buildScenarioPills();
            if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', setupDrawer);
            else setupDrawer();
            setupScenarioCards();

            function loadFeatures() {
            document.getElementById('featureExplorerGrid').setAttribute('aria-busy', 'true');
            window.SiteUI.fetch(featuresPage)
                .then(function (r) { if (!r.ok) throw new Error('features ' + r.status); return r.text(); })
                .then(function (text) {
                    // GitHub Pages serves FEATURES only as Jekyll's rendered page, never as markdown source,
                    // so the explorer reads the rendered headings and list items.
                    var page = new DOMParser().parseFromString(text, 'text/html');
                    var main = page.querySelector('.main-content') || page.body;
                    window.SiteUI.prepareInventory(main, featuresPage);
                    allFeatures = [];
                    document.getElementById('full-features-content').innerHTML = main.innerHTML;

                    var currentCategory = null;
                    var currentAnchor = null;
                    var rank = 0;
                    main.querySelectorAll('h2, li').forEach(function (node) {
                        if (node.tagName === 'H2') {
                            var heading = node.textContent.trim();
                            currentCategory = /^\d+\./.test(heading) ? heading : null;
                            currentAnchor = headingAnchor(node);
                            rank = 0;
                            return;
                        }
                        if (!currentCategory) return;
                        var body = node.cloneNode(true);
                        var strong = body.querySelector(':scope > strong');
                        if (!strong) return;
                        var title = strong.textContent.trim();
                        var escapedTitle = document.createElement('span');
                        escapedTitle.textContent = title;
                        if (hiddenFeatureTitles.indexOf(title) !== -1) return;
                        body.removeChild(strong);

                        var tag = 'Standard / VR';
                        var code = body.querySelector(':scope > code');
                        if (code && /^\[.*\]$/.test(code.textContent.trim())) {
                            tag = code.textContent.trim().slice(1, -1);
                            body.removeChild(code);
                        }

                        allFeatures.push({
                            category: currentCategory,
                            anchor: currentAnchor,
                            title: title,
                            htmlTitle: escapedTitle.innerHTML,
                            tag: tag,
                            desc: body.innerHTML.trim().replace(/^:\s*/, ''),
                            text: body.textContent.trim().replace(/^:\s*/, ''),
                            isKey: rank < KEY_FEATURES_PER_PILLAR
                        });
                        rank++;
                    });

                    if (!allFeatures.length) throw new Error('Empty feature inventory');
                    renderExplorer();
                })
                .catch(function (err) {
                    console.error(err);
                    document.getElementById('featureExplorerGrid').innerHTML =
                        '<div style="grid-column: 1/-1; text-align: center; color: var(--text-muted); padding: 3rem 0;">Не удалось загрузить реестр возможностей. Попробуйте обновить страницу.</div>';
                    document.getElementById('full-features-content').textContent = document.getElementById('featureExplorerGrid').textContent;
                })
                .finally(function () {
                    var grid = document.getElementById('featureExplorerGrid');
                    grid.setAttribute('aria-busy', 'false');
                    if (!allFeatures.length) {
                        var retry = document.createElement('button');
                        retry.type = 'button';
                        retry.className = 'btn btn-ghost';
                        retry.textContent = 'Повторить';
                        retry.addEventListener('click', loadFeatures);
                        grid.appendChild(retry);
                    }
                });
            }
            loadFeatures();

            // Tabs events wiring
            document.getElementById('tab-all').addEventListener('click', function (e) {
                document.querySelectorAll('.tab-btn').forEach(function (b) { b.classList.remove('active'); });
                e.target.classList.add('active');
                activeFilter = 'all';
                renderExplorer();
            });

            document.getElementById('tab-standard').addEventListener('click', function (e) {
                document.querySelectorAll('.tab-btn').forEach(function (b) { b.classList.remove('active'); });
                e.target.classList.add('active');
                activeFilter = 'standard';
                renderExplorer();
            });

            document.getElementById('tab-vr').addEventListener('click', function (e) {
                document.querySelectorAll('.tab-btn').forEach(function (b) { b.classList.remove('active'); });
                e.target.classList.add('active');
                activeFilter = 'vr';
                renderExplorer();
            });

            // Навешивание обработчика на поиск
            document.getElementById('featureSearch').addEventListener('input', function (e) {
                searchQuery = e.target.value;
                renderExplorer();
            });
        }());
