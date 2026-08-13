# Спецификация (draft): S1121 - VR-подсистема: пробелы в покрытии каталога/индекса/архитектуры

**Ticket:** S1121
**Status:** Archived
**Priority:** 35
**Date:** 2026-07-19
**Tier:** 2 - Small (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-19 (авто-парковка из S0986 impl + research, CLAUDE.md 3.1)

**Захвачено во время:** реализации S0986 (immersive VR subtitle rendering)

**Текст:**

Активная VR/immersive/XR-подсистема (`app_v2/src/vr/`, `ui/xr/`, `core/xr/`, нативный OpenXR C++) не покрыта тремя каноническими картами проекта. Три отдельных под-пункта (могут быть разбиты на отдельные тикеты при /spec-tech):

1. **Каталог классов не сканирует `src/vr`.** `dev/CATALOG/scripts/query.ps1 -ClassMatches` не находит ни новые (`SubtitleCueRenderer`, `SubtitleCueController`), ни существующие (`HudCanvasRenderer`, `DiagnosticXrActivity`) VR-классы. `catalog_sync.ps1 -Module app_v2` сканирует 1860 файлов = только `src/main`. Следствие: правило "New classes: fill role+status via set.ps1" неприменимо к VR-классам; навигация по VR-коду вынуждена идти в обход каталога (grep). Область: `dev/CATALOG/scripts/scan.ps1` (scan roots) - Rule 13 (чинить недостаточные скрипты).

2. **`dev/PROJECT_OPERATIONS_INDEX.md` §9 Feature-to-Path Map не содержит VR-раздела.** Между "Chromecast" и "Wear app" нет записи про `ui/xr/`, `core/xr/`, `app_v2/src/vr/`, несмотря на 37+ VR/XR-классов и нативный cpp. Роутинг research по VR обходит индекс.

3. **`docs/ARCHITECTURE.md` не описывает immersive VR/OpenXR-подсистему.** Нет секции про пайплайн текстур HUD-квада, контракт render-thread/EGL-конфайнмента, конвенцию JNI-моста - хотя это отдельный нетривиальный переиспользуемый паттерн (S0290/S0961/S0964/S0986). grep `immersive|OpenXR|HUD quad|xr_hud` в ARCHITECTURE.md = 0.

**Контекст:**

- Обнаружено при реализации S0986: mechanical-шаг "set role/status для новых классов" оказался невыполним (каталог не видит src/vr); research-агент дополнительно зафиксировал пункты 2-3.
- Dedup: поиск по каталогу (`search.ps1`) существующих тикетов на эти пробелы - не найдено (2026-07-19).

**Вложения:**

Вложений нет.

---

## 1. Проблема / симптом

VR-подсистема не находится через штатные инструменты навигации (каталог классов) и не описана в двух канонических документах (operations index, architecture). Это удорожает исследование VR-области и нарушает правила покрытия (Rule 13, документная синхронизация).

## 2. Цели

1. Каталог классов индексирует `src/vr` - навигация по VR-коду идёт штатным `query.ps1`, а не в обход через grep.
2. `dev/PROJECT_OPERATIONS_INDEX.md` §9 содержит VR/XR-раздел (роутинг research по VR-области).
3. `docs/ARCHITECTURE.md` описывает immersive VR/OpenXR-подсистему (native runtime, render-thread/EGL-конфайнмент, два текстурных канала, HUD-квад, re-entry).

Non-goals:

- Не менять VR-код; чисто tooling + документация.

## 3. Статус пунктов

- **Пункт 1 (каталог src/vr) - уже решён** до этого тикета. `dev/CATALOG/scripts/scan.ps1` уже включает `src\vr\java` (добавлено с работой над source-set S0404, 2026-07-17, после первичного захвата). Проверено: `query.ps1 -ClassMatches "*Hud*"` возвращает `HudCanvasRenderer` и другие VR-классы. Заполнение role/status для VR-классов (сейчас `unknown`) - отдельная работа, вне объёма.
- **Пункт 2 (operations index) - сделано.** Добавлен VR/XR-раздел в §9 Feature-to-Path Map.
- **Пункт 3 (architecture) - сделано.** Добавлена секция «Immersive VR / OpenXR Subsystem» в `docs/ARCHITECTURE.md`.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0986 (родительский - immersive subtitles, при реализации которого запаркован тикет); S0249/S0290/S0964/S0156 (VR-подсистема, референс в architecture-секции)

## 4. Проверка

- Пункт 1: `query.ps1 -Module app_v2 -ClassMatches "*Hud*"` -> VR-классы присутствуют (каталог видит src/vr).
- Пункт 2: grep `vr|xr` в `dev/PROJECT_OPERATIONS_INDEX.md` -> VR/XR-раздел присутствует.
- Пункт 3: grep `openxr|immersive` в `docs/ARCHITECTURE.md` -> секция присутствует.
- Document-registry: `validate.ps1` + `generate.ps1 -Check` -> PASS (ARCHITECTURE.md зарегистрирован).

---

## Last Audit

**Date:** 2026-07-21 (via /spec-all)
**Verdict:** Verified

- Item 1 (catalog scans `src/vr`): already resolved before this ticket - `scan.ps1` `$srcRoots` includes `src\vr\java` (S0404 source-set work, 2026-07-17). Verified: `query.ps1 -ClassMatches "*Hud*"` returns `HudCanvasRenderer` et al. Filling VR class role/status (currently `unknown`) is a separate follow-on, out of scope.
- Item 2 (operations index): VR/XR block added to `dev/PROJECT_OPERATIONS_INDEX.md` §9 Feature-to-Path Map. Verified: 6 VR/XR hits.
- Item 3 (architecture): "Immersive VR / OpenXR Subsystem" section added to `docs/ARCHITECTURE.md` (entry/gating, native runtime, render-thread/EGL confinement, two texture channels + HUD quad, re-entry). Grounded in `DiagnosticXrRenderThread`/`HudCanvasRenderer`/`NativeDiagnosticXrRuntime` KDoc. Verified: 7 VR hits.
- Document registry: `validate.ps1` PASS (23 records); `generate.ps1 -Check` "views current". `docs/ARCHITECTURE.md` is a registered document (architecture area).
- Scope: docs + tooling only, no VR code changed (per non-goal). No build required.
