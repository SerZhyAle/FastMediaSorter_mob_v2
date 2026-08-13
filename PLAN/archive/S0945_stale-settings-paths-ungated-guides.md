# Стратегическая спецификация: S0945 - Устаревшие пути настроек в гайдах вне охвата HOW_TO-гейта

**Ticket:** S0945
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-05
**Tier:** TBD
**Roadmap entry:** Ad-hoc - out-of-scope finding during S0814

> **Scope:** STRATEGIC (Draft skeleton, /spec-draft). Захват симптома; без research и тактики.

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-05

**Захвачено во время:** /spec-dev S0814 (Phase 01, починка howto-settings-paths-gate)

**Текст (симптом, verbatim):**

Во время S0814 гейт `assert-howto-settings-paths.ps1` (S0558) поймал 12 битых путей в HOW_TO (несуществующая вкладка "Management") - починено в рамках S0814. Но гейт покрывает ТОЛЬКО три файла HOW_TO*. Скан остальных нарративных гайдов (README / QUICK_START / FAQ / TROUBLESHOOTING, EN+RU+UK) по `→`-цепочкам с "Settings/Настройки/Налаштування" даёт десятки строк с вкладками, которых нет в приложении (реальные вкладки: General / Media / Operations / Playback):

- `Settings → Input → Keybindings` - README.md:137, QUICK_START*.md:184-185, FAQ*.md:179/220 (реально: Operations → Controls & Keybindings).
- `Settings → Interface → Device profile` - QUICK_START*.md:36 (вкладки Interface нет).
- `Settings → Appearance → Theme` - FAQ*.md:361/374 (вкладки Appearance нет).
- `Settings → Audio` / `Настройки → раздел Аудио` - FAQ*.md:264/305, QUICK_START*.md:177-178, README.md:198, README_UK.md:243 (Audio - подсекция Media, не вкладка).
- `Settings → Add Folder → ..` - FAQ*.md:56/76/84/90, QUICK_START*.md:140/147 (проверить фактический флоу добавления ресурса).
- `Settings → Edit folder / Резервное копирование / Приложения → Разрешения` - TROUBLESHOOTING*.md - смесь путей приложения и системных настроек Android; системные - ок, пути приложения не проверяются ничем.

Симптом двухслойный: (1) конкретные битые пути надо выправить по манифесту настроек; (2) системно - гейт S0558 валидирует только HOW_TO*, остальные гайды дрейфуют молча. Возможное направление: расширить охват гейта на все нарративные гайды (или их `→`-строки), либо сузить конвенцию "точные пути только в HOW_TO, остальные гайды описывают вход без цепочки".

**Вложения:**

Вложений нет.

---

## 1. Проблема

Нарративные гайды (README / QUICK_START / FAQ / TROUBLESHOOTING, EN+RU+UK) содержат десятки `Settings → ..`-цепочек, называющих вкладки/разделы, которых нет в приложении. Реальные вкладки (ground truth - `docs/settings/howto-path-vocab.json`, S0558):

- Вкладки: General/Общие/Загальні, Media/Медиа/Медіа, Operations/Операции/Операції, Playback/Воспроизведение/Відтворення.
- Media-подсекции: Images, Documents, Other.
- Навигируемые не-manifest экраны: Controls & Keybindings (под Operations).

Битые вкладки в гайдах: `Input`, `Interface`, `Appearance`, `Audio`-как-вкладка - ни одна не существует. Пути с ними фактически неверны и вводят пользователя в заблуждение под любой конвенцией.

Симптом двухслойный:
- Layer 1 - конкретные битые пути (инвентарь в §0).
- Layer 2 - системно: gate S0558 (`assert-howto-settings-paths.ps1`) валидирует ТОЛЬКО `docs/HOW_TO*`. README/QUICK_START/FAQ/TROUBLESHOOTING дрейфуют молча - у правок этих файлов нет автоматической верификации.

## 2. Цели

1. Устранить фактически неверные `Settings → ..`-пути в нарративных гайдах (все 3 локали).
2. Закрыть системный дрейф: у путей настроек в нарративных гайдах должна быть та же защита от дрейфа, что у HOW_TO (S0558), либо явная конвенция, снимающая необходимость такой защиты.

**Non-goals:**

- Переписывание содержания гайдов сверх строк с путями настроек.

## 3. Пожелания и ограничения

### 3.3 Owner inputs (Approval gate)

Блокирующее решение (определяет саму форму Layer 1 - правки противоположны):

- **Вариант A - расширить охват gate.** S0558 (или новый gate) сканирует `→`-цепочки во всех нарративных гайдах. Layer 1 = *исправить* каждый путь к манифест-истине; далее gate верифицирует и держит их. Плюс: единая точность. Минус: нарративные гайды легитимно используют более свободную прозу, gate может стать шумным.
- **Вариант B - сузить конвенцию.** Точные `Settings → ..`-цепочки живут только в HOW_TO*; остальные гайды описывают вход без стрелочной цепочки. Layer 1 = *убрать/ослабить* цепочки в README/FAQ/QUICK_START/TROUBLESHOOTING. Плюс: меньше поверхности дрейфа. Минус: гайды теряют пошаговую точность.

**Решение владельца (2026-07-05): Вариант A.** Layer 1 = *исправить* каждый `Settings → ..`-путь в нарративных гайдах к манифест-истине (все 3 локали). Layer 2 = расширить охват gate S0558 (`assert-howto-settings-paths.ps1`) на README / QUICK_START / FAQ / TROUBLESHOOTING - его `$files`-набор параметризуется тривиально, resolve+parity-машинерия переиспользуется без изменений. Целевой end-state: единая точность путей во всех гайдах + автоматическая защита от дрейфа.

- **Related tickets:** S0814 (контекст обнаружения), S0558 (gate - расширяется на нарративные гайды).

## 10. Связи с другими спеками

- S0814 - контекст обнаружения (сверка нарративных гайдов).
- S0558 - существующий howto-settings-paths-gate (кандидат на расширение охвата).

---

### Quiz decisions (2026-07-05)

- Как обрабатывать `Settings → ..`-цепочки в нарративных гайдах (Вариант A vs B vs Hybrid)? → **A: correct + gate all** (gate уже параметризуется по `$files`; расширение тривиально, единая точность путей + автоматическая защита от дрейфа для всех гайдов, а не только HOW_TO*).

---

## Implementation state (2026-07-05, /spec-all)

**Correction to the "trivial $files" assumption.** The owner's Variant A stands (correct + gate all), but the resolve machinery is NOT reusable unchanged for narrative guides: (a) the positional cross-locale **parity** check assumes 1:1 recipe order across locales, which HOW_TO has but narrative prose does not; (b) narrative guides legitimately use U+2192 arrows in prose and multi-clause/run-on sentences, so the HOW_TO resolver reports heavy noise.

### Phase A - gate machinery (DONE, verified)

`scripts/quality/assert-howto-settings-paths.ps1` refactored:
- Per-file scan extracted into `Scan-File`; scan now iterates a list of **file groups**.
- HOW_TO group keeps positional cross-locale parity; narrative groups are **resolve-only** (parity skipped - correct for reordered prose).
- Narrative scanning is **opt-in via `-IncludeNarrative`** (default OFF), so the hard `-Gate` path (post-change) stays HOW_TO-only and green until the narrative paths are fixed. Verified: default `-Gate` -> OK (17 HOW_TO recipes, parity); `-IncludeNarrative` -> FAIL (106 raw issues).

### Phase B - recipe-shape discriminator (DONE, verified)

Two guards added to `Scan-File`, both trilingual and driven off vocab data, not per-locale script literals:
- **System-Settings skip** (`Test-SystemPath`): a `Settings → ..` chain whose first post-anchor segment is an Android OS node (Apps/Permissions/Storage/Notifications/Display/Security + localized) or that names the app's own product (`FastMediaSorter`) is an OS-Settings path, out of app scope - skipped silently. Nodes live in `howto-path-vocab.json` -> `systemSettingsNodes` (uk includes both `Застосунки` and `Додатки`).
- **Bare-tab prose prefix** (`Resolve-TabPrefix`): prose like "Settings → General to open a dashboard.." names a real tab followed by free prose (no further arrow); resolved as a bare-tab reference (prose dropped). Word boundary required, so `Input`/`Interface`/`Audio` (no real tab prefix) still fail.

Effect: 106 raw issues -> 88, all 88 genuine broken paths (prose + OS-Settings false-positives gone). Default `-Gate` stayed green throughout.

### Phase C - Layer 1 fixes (DONE, gate = 0)

All 88 broken paths corrected to manifest truth across EN/RU/UK (README, QUICK_START, FAQ, TROUBLESHOOTING). Representative mappings:
- `Input → Keybindings` -> `Operations → Controls & Keybindings`.
- `Interface → Device profile` -> `General → Device profile`.
- `Audio` / `Audio section` slideshow-music refs -> `Media → Images → "Play music during slideshow"` (the toggle lives in the Images sub-section, not an Audio tab).
- `Add Folder → SMB/Cloud/..` -> `+ button → Network/Cloud → ..` (adding a resource is the main-screen "+", never Settings) - drops the `Settings` anchor entirely.
- Media card labels `Translation, OCR and Google Lens` -> `Media → Other`; `Text, PDF and EPUB viewing` -> `Media → Documents`.
- Stale/variant localized labels fixed to exact manifest titles (e.g. RU "Всегда показывать зоны нажатия" -> "Всегда показывать сетку сенсорных зон"; "Компактный режим" -> "Компактные кнопки плеера"; "Enable detailed errors"/"Включить подробные ошибки" -> Operations "Show detailed errors"/"Показывать детальные ошибки").
- Per-resource actions (`Edit folder → Disable thumbnails`) reworded to drop the `Settings` anchor (they are not app-Settings rows).

Result: `-IncludeNarrative` (now default) reports OK - 49 recipes across 5 groups, all resolve.

### Phase D - promote (DONE)

- Narrative groups are now unconditional in `assert-howto-settings-paths.ps1` (`-IncludeNarrative` retained as a no-op).
- `post-change.ps1` `$runsHowToPathGate` regex extended to `docs/(HOW_TO|README|QUICK_START|FAQ|TROUBLESHOOTING)[A-Z_]*\.md$`, so editing any narrative guide (all locales) fires the gate; `docs/howto/scenario-*.md` and unrelated docs correctly excluded.
- Rule 22 `assert-settings-doc-sync.ps1` (which chains this gate) verified green - a manifest/vocab change now re-checks all five guide groups.

### Known limitation (documented, not a blocker)

Paren/bracket-anchored recipes (`(Settings → ..)`) are still not scanned: the anchor requires a preceding space, and widening it to `(`/`[` would false-positive on Markdown links such as `[Налаштування SMB](url)`. The four such lines (FAQ theme/battery refs) were corrected by hand for accuracy but remain outside automated drift protection. Likewise a handful of `Settings > X` chains use `>` (not U+2192) and are intentionally not treated as recipes.

---

## Last Audit

**Audited:** 2026-07-06 (/spec-all F5, static)

**Verdict: Verified.** Both goals met.

- Goal 1 (Layer 1 - accurate paths): `assert-howto-settings-paths.ps1 -IncludeNarrative` reports OK - 49 recipes across 5 guide groups, all resolve against the settings manifest; HOW_TO locales remain in parity. Every one of the 88 broken narrative paths corrected to manifest truth across EN/RU/UK.
- Goal 2 (Layer 2 - drift protection): narrative groups now scanned by default; `post-change.ps1` fires the gate on any README/QUICK_START/FAQ/TROUBLESHOOTING edit (all locales); Rule 22 `assert-settings-doc-sync.ps1` chains it green, so a manifest/vocab rename re-validates every guide.

**Evidence (commands, exit 0):**
- `assert-howto-settings-paths.ps1 -Gate` -> OK, 49 recipes / 5 groups (narrative now default-on).
- `assert-settings-doc-sync.ps1` -> OK - catalog complete, manifest fresh, annotations covered, reference up to date, HOW_TO recipes in sync.
- Detection-regex sanity: matches HOW_TO/README/QUICK_START/FAQ/TROUBLESHOOTING (+ `_RU`/`_UK`), excludes `docs/howto/scenario-*.md` and unrelated docs.

**Residual (tracked in Known limitation above, not blocking):** paren/bracket-anchored and `>`-separated chains stay outside automated scanning by design (Markdown-link false-positive risk).

**Files touched:** `scripts/quality/assert-howto-settings-paths.ps1`, `scripts/post-change.ps1`, `docs/settings/howto-path-vocab.json`, and the 12 narrative guides (README/QUICK_START/FAQ/TROUBLESHOOTING x EN/RU/UK). No `.kt`, no debug tags, no device test required (static text gate).
