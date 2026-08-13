# Draft: S0507 - Focus-highlight coverage audit + mechanical gate

**Status:** Archived
**Priority:** 45
**Date:** 2026-06-18
**Tier:** 3 - Moderate (ad-hoc)
**Origin:** owner request 2026-06-18 (multi-device input improvements)

> **Scope:** Compact spec (Simple path). Mechanical focus-highlight coverage gate (audit + ratchet); existing-gap remediation carved to S0518.

---

## 0. Идея

Shared focus-drawables существуют (`focus_button_background.xml`, `focus_tab_background.xml`, `item_focus_selector.xml`), но не гарантировано, что они применены ко *всем* фокусируемым контролам на всех экранах. На TV/D-pad/клавиатуре пользователь должен всегда видеть, где фокус, и отличать его не только по цвету (CLAUDE.md Rule 16).

## Проблема

Нет механической проверки, что каждый interactive/focusable элемент несёт видимый focus-state. Регрессии («новая кнопка без focus-фона») проходят незаметно.

## Цель (RU)

Провести аудит покрытия focus-highlight по `res/layout*` и добавить механический гейт (по образцу `scripts/quality/assert-neuroslop.ps1`), который флагует focusable/clickable вью без `foreground`/`background`/selector с focus-состоянием. Закрыть найденные пробелы, подключить гейт в `post-change.ps1`.

## Acceptance criteria

1. Существует ratchet-гейт `scripts/quality/assert-focus-highlight.ps1` (Report/`-Gate`/`-UpdateBaseline`/`-List`) по образцу `assert-layout-hardcoded-colors.ps1`; считает интерактивные вью (`clickable`/`focusable`/`onClick`) в `res/layout`+`layout-land` без распознанной focus-индикации (focus-drawable / `?attr/selectableItemBackground` / Material-framework-виджет).
2. Baseline засижен (`focus-highlight-baseline.txt`); `-Gate` exit 0 на baseline; рост валит гейт.
3. Гейт подключён в `post-change.ps1` для `ChangeType` Xml/Mixed.
4. Аудит текущего долга проведён (`-List`); ремедиация существующих пробелов вынесена в S0518 (baseline ратчетится вниз там).

> **Решение по объёму:** по прецеденту neuroslop-гейтов (baseline сидится на текущем долге, ратчетится вниз отдельно) S0507 поставляет механизм профилактики; закрытие 63 существующих пробелов через ~30 layout'ов с landscape-паритетом - отдельный объём (S0518).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0289 (multimodal parity), S0383 (neuroslop gate pattern), S0518 (gap remediation follow-up).

## Грубый объём

- Аудит: какие focusable вью не имеют focus-drawable (портрет + landscape парно, Rule 11).
- Скрипт-гейт `scripts/quality/assert-focus-highlight.ps1` с ratchet-baseline.
- Исправить текущие пробелы; подключить в `post-change.ps1`.
- Прогон на target-вариантах.

## Верификация

- Гейт-скрипт exit 0 после фиксов. Сборка ресурсов. Визуально — опционально (device-waived возможно).

## Связь

- S0289 (multimodal parity, §2 goal 9: focus различим не только цветом).
- S0383 (neuroslop ratchet-gate pattern, образец).
- S0518 (focus-highlight gap remediation - сводит baseline 63 -> 0).

---

## Phase 01 - Build the focus-highlight ratchet gate

**Files:**
- `scripts/quality/assert-focus-highlight.ps1` (new)
- `scripts/quality/focus-highlight-baseline.txt` (new, seeded 63)
- `scripts/post-change.ps1` (wire the gate for Xml/Mixed)

**Done:**

- Wrote `assert-focus-highlight.ps1`: element-level scan of `res/layout`+`layout-land`; flags `clickable`/`focusable`/`onClick` views lacking focus indication (focus-state drawable, `?attr/selectableItemBackground`, or a Material/framework intrinsic-focus widget / scroll container). Conservative heuristic to keep false positives low; baseline only ratchets DOWN.
- Audited current debt via `-List`: 63 gaps (custom clickable LinearLayout/View/CardView/MaterialCardView/FrameLayout + SettingsToggleRow/TranslationOverlayView). Seeded `focus-highlight-baseline.txt` = 63.
- Wired `focus-highlight-gate` into `post-change.ps1` (`$runsFocusHighlightGate`, Xml/Mixed); verified it PASSes in the pipeline.
- Existing-gap remediation carved to S0518 (ratchets baseline 63 -> 0).

**Status:** `[x]` done

---

## Last Audit

**Date:** 2026-06-18
**Mode:** strategic (Simple)
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 4 - WARN 0 - FAIL 0 - MANUAL 0 - EXEMPT 0

- [PASS §1] `scripts/quality/assert-focus-highlight.ps1` exists with Report/`-Gate`/`-UpdateBaseline`/`-List`; conservative interactive-without-focus heuristic over layout + layout-land.
- [PASS §2] `focus-highlight-baseline.txt` = 63; `-Gate` exits 0 at baseline; raising the count fails the gate.
- [PASS §3] Wired into `post-change.ps1` (Xml/Mixed); pipeline run shows `[focus-highlight-gate] PASS`.
- [PASS §4] `-List` audit captured; 63-gap remediation tracked as S0518 (baseline ratchets down there) - matches the neuroslop baseline-and-ratchet precedent.
- Mechanical tooling change - no ALL_FEATURES capability. Zero `Timber.d("S0507:` tags.
