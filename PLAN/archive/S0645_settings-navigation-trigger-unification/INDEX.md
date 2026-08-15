# Tactical Plan: S0645 - settings-navigation-trigger-unification

**Strategic spec:** [`../S0645_settings-navigation-trigger-unification.md`](../S0645_settings-navigation-trigger-unification.md)
**Research inputs:** none
**Feature:** Unify settings navigation-trigger rows to a single etalon (title + hint + real arrow, no-stretch content, icon preserved, portrait + landscape)
**Tier:** Ad-hoc UI consistency
**Priority:** 55
**Status:** Implemented - BlockNeedUserTest (device visual verification pending)
**Phases:** 4 / 4 done
**Last updated:** 2026-06-24

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Etalon definition (locked by strategic §6 owner decisions, 2026-06-23)

A navigation-trigger row opens another screen / activity / dialog (NOT a value picker). Its canonical form:

- Rendered by `SettingsSelectionRow` in **navigation mode** (`app:ssr_navMode="true"`) - no new widget class (§6.1).
- Trailing glyph = real arrow `@drawable/ic_arrow_forward` (`->`), never the value chevron `>` (§6.2 cross-batch rule with S0644).
- No-stretch content: row stays full-width-clickable, but the icon + text + arrow hug the left and the arrow sits right after the text (reuse the existing inline layout collapse).
- Leading icon preserved when present; optional subtitle/hint preserved.

Value-selection rows (S0644) keep the chevron `>` and are out of scope here.

---

## Nav-row coverage inventory (audit result)

True navigation rows found across all settings screens (portrait + landscape):

- **Bucket A** - already `SettingsSelectionRow`, already `setOnRowClickListener`; need only the `ssr_navMode` flag:
  - `row_saved_authorizations` (General settings) -> `AuthSessionsActivity`.
  - `rowOpenStatistics` (General settings, has `ic_history` icon) -> `StatisticsActivity`.
- **Bucket B** - ad-hoc layout; need migration to the widget + handler change (`setOnClickListener` -> `setOnRowClickListener`):
  - `layoutExtensionsManager` (Other media settings) -> `ExtensionsManagerFragment`. **This is the owner etalon row** ("Download OCR or translation").
  - `rowControlsKeybindings` (Operations settings, wrapped in a `MaterialCardView`) -> `SettingsActivity.openKeybindingRemap`.

Out of scope (value-selection rows, S0644 - keep chevron): `rowDeviceProfile`, `row_link_autodownload_resource`, `rowScreenshotGestureActionUp/Right/Down`, `rowScreenshotDestination`.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | widget-nav-mode | - | ✅ Done | 2/2 | [PHASE_01__widget-nav-mode.md](PHASE_01__widget-nav-mode.md) |
| 02 | apply-existing-rows | 01 | ✅ Done | 2/2 | [PHASE_02__apply-existing-rows.md](PHASE_02__apply-existing-rows.md) |
| 03 | migrate-adhoc-rows | 01 | ✅ Done | 6/6 | [PHASE_03__migrate-adhoc-rows.md](PHASE_03__migrate-adhoc-rows.md) |
| 04 | docs-catalog-cleanup | 01,02,03 | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - both strategic §6 owner decisions are Resolved (quiz, 2026-06-23): extend `SettingsSelectionRow` with a nav mode; arrow `->` for nav vs chevron `>` for value.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (strategic §8 absent; no FEATURES sentence).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (public widget API changed).
- [ ] `/spec-check S0645` returns `Verified` (after device test confirms the visual etalon).
- [ ] Strategic spec `Status:` advanced past `Tactical`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All code done: this is a visual change - insert `Timber.d("S0645: …")` tags at the changed nav-row entry points, set status `BlockNeedUserTest`, device-verify, then `/spec-check`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-24 - Initial tactical plan authored by `/spec-tech`.
