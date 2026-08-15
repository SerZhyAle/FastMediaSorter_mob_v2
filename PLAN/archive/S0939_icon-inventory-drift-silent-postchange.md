# Стратегическая спецификация: S0939 - Тихий дрейф icon-inventory при правках заголовков настроек

**Ticket:** S0939
**Status:** Archived
**Priority:** 35
**Date:** 2026-07-04
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - обнаружено 2026-07-04 при закрытии S0916

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-04

**Захвачено во время:** S0916 (устранение дрейфа локализованной icon-легенды)

**Текст:**

Дрейф `docs/icons/icon-inventory.json` + `docs/ICON_LEGEND*.md` проходит тихо и всплывает только в CI или случайно. Причины:

1. Гейт `icon-inventory-sync` в `post-change.ps1` = SKIP, когда тронутый файл не является icon-ассетом/страницей легенды. Поэтому изменение иконки/строки заголовка настроек (например, S0882 разбил «Other features» на 4 заголовка с иконками ic_camera_capture/ic_video/ic_microphone/ic_display и удалил строку `settings_category_other_features`) НЕ триггерит проверку свежести инвентаря.
2. Проверка inventory-vs-source (Check 5, `IconInventoryExportTest`) - opt-in через `-IncludeExportTest`, применяется только в CI (локально дорого: полный gradle-тест ~4 мин).

Итог: инвентарь молча расходится с приложением; рендерер `render-icon-legend.ps1` падает в humanized-drawable fallback («Camera Capture» вместо реальной экранной строки), и это видно лишь при следующем случайном запуске гейта или в CI. В S0916 пришлось регенерировать инвентарь вручную и попутно обнаружился накопленный дрейф на 12 vector-строк + 2 иконки без SVG.

Идея превентива: правка settings-layout / строк / drawable, которые несут иконки заголовков, должна давать в `post-change` дешёвый сигнал о возможной несвежести инвентаря (быстрый диф без полного gradle-теста), либо иначе закрыть локальный разрыв, чтобы дрейф всплывал в момент изменения, а не в CI.

**Вложения:**

Вложений нет.

---

## 1. Проблема

`docs/icons/icon-inventory.json` и производные `docs/ICON_LEGEND*.md` могут расходиться с реальным settings UI без локального сигнала в момент правки. Сегодня `scripts/post-change.ps1` запускает `assert-icon-inventory-sync.ps1` только при изменении `docs/icons/**` или `docs/ICON_LEGEND*`, поэтому изменение `fragment_settings_*` и связанных string-ресурсов не поднимает флаг. В результате settings-header / settings-row правка может тихо сделать inventory устаревшим, а разработчик узнаёт об этом только позже - из CI, случайного ручного рендера или несвязанной doc-регенерации.

Локальный разрыв особенно заметен для settings surface, потому что `IconInventoryExportTest` реально сканирует `fragment_settings_*` на пары `csh_icon/csh_title` и `str_icon|ssr_icon` + title. Значит источник истины уже известен, но в дешёвом post-change gate отсутствует source-vs-inventory проверка именно для этого слоя.

---

## 2. Цели

1. Любая релевантная правка settings icon/title source (`fragment_settings_*`, связанные app strings) должна запускать локальный icon-inventory gate из `post-change.ps1`.
2. Gate должен уметь без Gradle/JVM сравнить settings-часть committed inventory с live source layouts и падать на дрейфе `settings-header` / `settings-row`.
3. Тяжёлый полный export-test (`IconInventoryExportTest`) остаётся opt-in/CI-only; локальная защита должна быть дешёвой и пригодной для частого запуска.

**Non-goals:**

- Полная дешёвая live-проверка всех поверхностей inventory (program-nav, send-to, player-command) без JVM.
- Проверка, что содержимое `docs/icons/svg/*.svg` соответствует актуальной геометрии drawable при неизменном имени ресурса.
- Перестройка `IconInventoryExportTest` или схемы `icon-inventory.json`.

---

## 3. Пожелания и ограничения

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0916 (устранённый дрейф - источник находки), S0882 (изменение, вызвавшее дрейф).
- **Delegated by user - /spec-all auto-approval:** scope ограничен script/doc tooling; меняем только локальный drift gate для settings-поверхностей и post-change routing, без продуктового поведения приложения.

---

## 4. Контекст текущей архитектуры

`IconInventoryExportTest` уже умеет собирать settings inventory из compiled `fragment_settings_*` layouts, читая `csh_icon/csh_title`, `str_icon/str_title` и `ssr_icon/ssr_title`. `render-icon-legend.ps1` затем строит docs-легенду из committed `icon-inventory.json` и текущих app strings. Из-за этого string drift для уже существующей inventory строки может быть заметен при legend re-render, но новый/удалённый settings icon row остаётся невидимым без полного export-test.

`assert-icon-inventory-sync.ps1` сегодня проверяет asset coverage, orphan SVGs, legend freshness и locale parity, а full inventory freshness выполняет только через тяжёлый Robolectric `-IncludeExportTest`. Этого достаточно для CI, но недостаточно для дешёвого локального сигнала на settings-правках.

---

## 5. Предлагаемый подход

1. Добавить в `assert-icon-inventory-sync.ps1` новый pure-PowerShell stage, который сканирует source `fragment_settings_*.xml`, извлекает те же settings icon/title пары, нормализует их к полям inventory и сравнивает с committed subset (`surface = settings-header|settings-row`).
2. Расширить trigger в `scripts/post-change.ps1`, чтобы icon-inventory gate запускался не только на `docs/icons/**`, но и на релевантных app source changes: settings layouts и merged string-table XML (`values*/strings*.xml`).
3. Обновить tactical/spec documentation так, чтобы причина новой проверки и дешёвый scope были явно зафиксированы.

**Implemented date:** 2026-07-04

---

## 6. Открытые вопросы / Research items

- Resolved: для settings surface достаточно pure-PS source scan `fragment_settings_*.xml` vs committed inventory subset; для string drift локальный сигнал обеспечивается расширенным trigger existing legend freshness check на `values*/strings*.xml`.

---

## 12. Ссылка на тактическую спецификацию

Тактический план: [`S0939_icon-inventory-drift-silent-postchange/INDEX.md`](S0939_icon-inventory-drift-silent-postchange/INDEX.md)

## Last Audit

**Date:** 2026-07-05
**Mode:** full (strategic + phase 01)
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 4 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 0

### Manual / on-device

- [x] Goal 1/2 - cheap settings-source-vs-inventory stage present in `assert-icon-inventory-sync.ps1`: `Get-SettingsSourceEntries()` scans `fragment_settings_*.xml` for `csh_icon/csh_title` (settings-header), `str_icon/str_title` and `ssr_icon/ssr_title` (settings-row), compares the committed `settings-header|settings-row` inventory subset vs live source, and `Add-Fail 'settings-source-fresh'` on a missing/stale entry - pure PowerShell, no JVM. Runs unconditionally (main body, not behind `-IncludeExportTest`).
- [x] Goal 1/3 - `post-change.ps1` `$runsIconInventoryGate` fires for `app_v2/src/main/res/layout/fragment_settings_*.xml` and `app_v2/src/main/res/values*/strings*.xml` (tagged "S0815/S0939"), not only `docs/icons/**` / `docs/ICON_LEGEND*`.
- [x] Goal 3 - heavy `IconInventoryExportTest` (all surfaces) stays opt-in via `-IncludeExportTest` (gate prints "inventory-vs-source freshness skipped").
- [x] Gate green: `assert-icon-inventory-sync.ps1` -> exit 0 (60 vector svgs, no orphans, legend fresh, locales in parity, settings-source in sync).
- [x] Tactical INDEX Phase 01 Done (3/3); all Completion Gate items checked.
