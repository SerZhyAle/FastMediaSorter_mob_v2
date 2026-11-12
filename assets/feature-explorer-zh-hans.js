        (function () {
            var allFeatures = [];
            var activeFilter = 'all'; // 'all', 'standard', 'vr'
            var searchQuery = '';
            var activeVariant = 'v1'; // 'v1', 'v2', 'v3', 'v4'
            var activeCategoryV1 = null;
            var activeToggleV3 = 'key'; // 'key', 'all'
            var activeScenarioV4 = 'all';
            var featuresPage = 'docs/FEATURES.html';

            // FEATURES.md is curated to one entry per capability; no noise rows to suppress.
            var hiddenFeatureTitles = [];

            // FEATURES opens every pillar with its standout capabilities, so position marks a key feature
            // and no title list can go stale when a bullet is renamed.
            var KEY_FEATURES_PER_PILLAR = 2;

            // Columns name FEATURES pillars by number, which every locale shares.
            var dashboardColumns = [
                { id: 'shell', label: '外壳与启动', categories: [1, 4] },
                { id: 'media', label: '媒体与直播流', categories: [2, 3] },
                { id: 'tools', label: '工具与监控', categories: [5, 7] },
                { id: 'files', label: '文件与手表', categories: [6, 8] }
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
                { id: 'launcher', label: '主屏幕' },
                { id: 'watch', label: '手表伴侣' },
                { id: 'radio', label: '直播' },
                { id: 'all', label: '全部场景' },
                { id: 'photo', label: '电子相框' },
                { id: 'music', label: '车载音乐' },
                { id: 'video', label: '家庭影院' },
                { id: 'smb', label: 'SMB 设置' },
                { id: 'backup', label: '相机备份' },
                { id: 'download', label: '下载整理器' },
                { id: 'offline', label: '离线旅行' },
                { id: 'docs', label: '云文档' },
                { id: 'ocr', label: 'OCR 翻译' },
                { id: 'tv', label: 'NAS 媒体库' },
                { id: 'duplicates', label: '重复文件清理' },
                { id: 'chromecast', label: 'Chromecast' },
                { id: 'widgets', label: '智能小组件' },
                { id: 'vrcinema', label: '沉浸式影院' },
                { id: 'calculator', label: '快速计算' },
                { id: 'cloudnotes', label: '云端与 Markdown 笔记' }
            ];

            // A scenario matches words inside each FEATURES bullet, so a renamed bullet stays in its scenario;
            // the earlier list of exact bullet titles went stale and left every scenario pill empty.
            var scenarioKeywords = {
                launcher: ['launcher', 'desktop', 'home screen', 'taskbar', 'start menu', 'лаунчер', 'рабоч', 'домашн', 'панель задач', 'робоч', 'пуск'],
                watch: ['watch', 'wear', 'час', 'годин'],
                radio: ['radio', 'stream', 'iptv', 'радио', 'радіо', 'трансляц', 'поток', 'потік'],
                photo: ['slideshow', 'image viewer', 'gif', 'webp', 'picture', 'photo frame'],
                music: ['music', 'audio', 'radio', 'mono ', 'stereo balance', 'lyrics'],
                video: ['video', 'subtitle', 'movie', 'cinema', 'television'],
                smb: ['smb', 'nas', 'sftp', 'ftp', 'your pc'],
                backup: ['backup', 'restore', 'capture into', 'camera widget'],
                download: ['download', 'sorting', 'file manager', 'trash', 'move whole folders'],
                offline: ['offline', 'locally', 'without sending', 'memory card', 'removable'],
                docs: ['pdf', 'epub', 'document', 'markdown', '.txt'],
                ocr: ['ocr', 'translat', 'recogni'],
                tv: ['television', 'd-pad', 'nas'],
                duplicates: ['duplicate', 'identical', 'trash', 'undo', 'cleanup'],
                chromecast: ['cast ', 'television'],
                widgets: ['widget'],
                vrcinema: ['headset', 'openxr', 'vr180', '360', 'virtual screen', 'sbs'],
                calculator: ['calculat', 'math', 'stopwatch', 'mini-program'],
                cloudnotes: ['markdown', 'note', '.txt', 'google drive']
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

                if (text.indexOf('slideshow') !== -1 || text.indexOf('photo') !== -1 || text.indexOf('frame') !== -1) {
                    links.push({ url: 'documentation/images/slideshow-and-transitions.html', label: '方案：电子相框' });
                }
                if (text.indexOf('music') !== -1 || text.indexOf('audio') !== -1 || text.indexOf('midi') !== -1 || text.indexOf('wheel') !== -1) {
                    links.push({ url: 'documentation/audio/playing-and-organizing-music.html', label: '方案：车载音乐' });
                }
                if (text.indexOf('video') !== -1 || text.indexOf('movie') !== -1 || text.indexOf('exoplayer') !== -1 || text.indexOf('3d') !== -1 || text.indexOf('cinema') !== -1) {
                    links.push({ url: 'documentation/player/video-playback-controls.html', label: '方案：家庭影院' });
                }
                if (text.indexOf('smb') !== -1 || text.indexOf('network') !== -1 || text.indexOf('nas') !== -1 || text.indexOf('host') !== -1) {
                    links.push({ url: 'documentation/network/smb-samba-shares.html', label: 'SMB 设置指南' });
                }
                if (text.indexOf('backup') !== -1 || text.indexOf('copy') !== -1) {
                    links.push({ url: 'documentation/storage/scheduled-operations.html', label: '相机备份' });
                }
                if (text.indexOf('download') !== -1 || text.indexOf('organizer') !== -1 || text.indexOf('delete') !== -1 || text.indexOf('move') !== -1) {
                    links.push({ url: 'documentation/storage/destination-targets-setup.html', label: '下载整理器' });
                }
                if (text.indexOf('offline') !== -1 || text.indexOf('travel') !== -1 || text.indexOf('local folder') !== -1) {
                    links.push({ url: 'documentation/network/network-sync-and-cache.html', label: '离线旅行文件夹' });
                }
                if (text.indexOf('document') !== -1 || text.indexOf('pdf') !== -1 || text.indexOf('epub') !== -1 || text.indexOf('reading') !== -1) {
                    links.push({ url: 'documentation/documents/pdf-epub-viewing.html', label: '云文档' });
                }
                if (text.indexOf('ocr') !== -1 || text.indexOf('translation') !== -1 || text.indexOf('translate') !== -1 || text.indexOf('tesseract') !== -1) {
                    links.push({ url: 'documentation/tools/ocr-text-recognition.html', label: 'OCR 翻译' });
                }
                if (text.indexOf('tv') !== -1 || text.indexOf('living-room') !== -1 || text.indexOf('living room') !== -1) {
                    links.push({ url: 'documentation/network/smb-samba-shares.html', label: 'NAS 媒体库' });
                }
                if (text.indexOf('watch') !== -1 || text.indexOf('wear') !== -1 || text.indexOf('smartwatch') !== -1) {
                    links.push({ url: 'documentation/wear/standalone-music-playback.html', label: '手表音乐' });
                    links.push({ url: 'documentation/wear/phone-and-network-folders-on-watch.html', label: '手表网络' });
                }
                if (text.indexOf('duplicate') !== -1 || text.indexOf('sha-256') !== -1) {
                    var duplicatesUrl = scenarioPillarUrl('duplicates');
                    if (duplicatesUrl) links.push({ url: duplicatesUrl, label: '重复文件清理' });
                }
                if (text.indexOf('chromecast') !== -1 || text.indexOf('cast ') !== -1 || text.indexOf('cast-compatible') !== -1) {
                    var chromecastUrl = scenarioPillarUrl('chromecast');
                    if (chromecastUrl) links.push({ url: chromecastUrl, label: 'Chromecast 播放' });
                }
                if (text.indexOf('widgets') !== -1 || text.indexOf('widget') !== -1) {
                    links.push({ url: 'documentation/launcher/home-screen-widgets.html', label: '智能小组件' });
                }
                if (text.indexOf('vrcinema') !== -1 || text.indexOf('immersive cinema') !== -1 || text.indexOf('openxr') !== -1) {
                    links.push({ url: 'documentation/vr/spatial-cinema-playback.html', label: '沉浸式影院' });
                }
                if (text.indexOf('calculator') !== -1 || text.indexOf('math') !== -1 || text.indexOf('calculation') !== -1 || text.indexOf('scientific') !== -1) {
                    links.push({ url: 'documentation/programs/calculator-and-stopwatch.html', label: '快速计算' });
                }
                if (text.indexOf('cloudnotes') !== -1 || text.indexOf('note') !== -1 || text.indexOf('markdown') !== -1) {
                    links.push({ url: 'documentation/documents/text-code-editor.html', label: '云端与 Markdown 笔记' });
                }

                return links;
            }

            function mapTitleToScenario(title) {
                var t = title.toLowerCase();
                if (t.indexOf('frame') !== -1 || t.indexOf('photo') !== -1) return 'photo';
                if (t.indexOf('car') !== -1 || t.indexOf('music') !== -1) return 'music';
                if (t.indexOf('cinema') !== -1 || t.indexOf('movie') !== -1 || t.indexOf('video') !== -1) return 'video';
                if (t.indexOf('organizer') !== -1 || t.indexOf('download') !== -1) return 'download';
                if (t.indexOf('backup') !== -1) return 'backup';
                if (t.indexOf('smb') !== -1 || t.indexOf('network') !== -1) return 'smb';
                if (t.indexOf('travel') !== -1 || t.indexOf('offline') !== -1) return 'offline';
                if (t.indexOf('docs') !== -1 || t.indexOf('cloud') !== -1 || t.indexOf('document') !== -1 || t.indexOf('epub') !== -1) return 'docs';
                if (t.indexOf('ocr') !== -1 || t.indexOf('translation') !== -1) return 'ocr';
                if (t.indexOf('tv') !== -1 || t.indexOf('nas') !== -1) return 'tv';
                if (t.indexOf('duplicate') !== -1 || t.indexOf('cleanup') !== -1) return 'duplicates';
                if (t.indexOf('chromecast') !== -1 || t.indexOf('cast') !== -1) return 'chromecast';
                if (t.indexOf('widget') !== -1) return 'widgets';
                if (t.indexOf('openxr') !== -1 || t.indexOf('immersive cinema') !== -1) return 'vrcinema';
                if (t.indexOf('calculation') !== -1 || t.indexOf('math') !== -1) return 'calculator';
                if (t.indexOf('note') !== -1 || t.indexOf('markdown') !== -1) return 'cloudnotes';
                return null;
            }

            function renderExplorer() {
                if (!allFeatures.length) return;
                var grid = document.getElementById('featureExplorerGrid');
                grid.innerHTML = '';

                // Show/hide variant specific controls
                document.getElementById('toggleV3Controls').style.display = (activeVariant === 'v3') ? 'flex' : 'none';
                document.getElementById('scenariosPillsContainer').style.display = (activeVariant === 'v4') ? 'flex' : 'none';

                var filtered = allFeatures.filter(function (f) {
                    // Platform Filter
                    if (activeFilter === 'standard') {
                        if (!hasFlavor(f, 'Standard')) return false;
                    } else if (activeFilter === 'vr') {
                        if (!hasFlavor(f, 'VR')) return false;
                    }

                    // Text Search Filter
                    if (searchQuery) {
                        var q = searchQuery.toLowerCase();
                        return f.title.toLowerCase().indexOf(q) !== -1 ||
                            f.text.toLowerCase().indexOf(q) !== -1 ||
                            f.category.toLowerCase().indexOf(q) !== -1;
                    }

                    return true;
                });

                if (filtered.length === 0) {
                    grid.innerHTML = '<div style="grid-column: 1/-1; text-align: center; color: var(--text-muted); padding: 3rem 0;">未找到符合条件的功能。</div>';
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
                    // --- Variant 1: Category Explorer ---
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
                        grid.innerHTML = '<div style="grid-column: 1/-1; text-align: center; color: var(--text-muted); padding: 3rem 0;">当前筛选条件下没有可用分类。</div>';
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
                        minorTitle.innerText = '其他功能';
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
                    // --- Variant 2: Media Dashboard ---
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
                    // --- Variant 3: Highlights / All ---
                    if (activeToggleV3 === 'key') {
                        var majorGrid = document.createElement('div');
                        majorGrid.className = 'major-grid-v3';

                        var keyList = filtered.filter(function (f) {
                            return f.isKey;
                        });

                        if (keyList.length === 0) {
                            grid.innerHTML = '<div style="grid-column: 1/-1; text-align: center; color: var(--text-muted); padding: 3rem 0;">当前筛选条件下未找到重点功能。</div>';
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
                    // --- Variant 4: Scenario Filter ---
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
                            '<span>Showing features for: <strong>' + labelText + '</strong></span>' +
                            '<button class="clear-filter-btn" id="clearScenarioFilter" aria-label="Clear filter" title="Clear filter"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" aria-hidden="true" focusable="false"><line x1="6" y1="6" x2="18" y2="18"/><line x1="18" y1="6" x2="6" y2="18"/></svg></button>' +
                            '</div>';
                        grid.appendChild(badge);
                    }

                    if (scenarioFeatures.length === 0) {
                        var noData = document.createElement('div');
                        noData.style.gridColumn = '1/-1';
                        noData.style.textAlign = 'center';
                        noData.style.color = 'var(--text-muted)';
                        noData.style.padding = '3rem 0';
                        noData.innerText = '在当前筛选条件下，未找到该场景对应的功能。';
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
                    linksHtml = '<div style="margin-top: 1rem; border-top: 1px solid var(--border-glass); padding-top: 1rem;"><div class="detail-drawer-cat" style="margin-bottom: 0.5rem;">Related Scenarios:</div>';
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

                        var tag = '标准版 / VR';
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
                        '<div style="grid-column: 1/-1; text-align: center; color: var(--text-muted); padding: 3rem 0;">加载功能清单失败，请刷新页面。</div>';
                    document.getElementById('full-features-content').textContent = document.getElementById('featureExplorerGrid').textContent;
                })
                .finally(function () {
                    var grid = document.getElementById('featureExplorerGrid');
                    grid.setAttribute('aria-busy', 'false');
                    if (!allFeatures.length) {
                        var retry = document.createElement('button');
                        retry.type = 'button';
                        retry.className = 'btn btn-ghost';
                        retry.textContent = '重试';
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

            // Search input wiring
            document.getElementById('featureSearch').addEventListener('input', function (e) {
                searchQuery = e.target.value;
                renderExplorer();
            });
        }());
