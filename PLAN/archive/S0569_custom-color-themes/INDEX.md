# Tactical Plan: S0569 - custom-color-themes

**Strategic spec:** [`../S0569_custom-color-themes.md`](../S0569_custom-color-themes.md)
**Research inputs:** none
**Feature:** Custom Color Themes (dark green, dark blue, dark red, light green, light blue, light red)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Implemented - awaiting on-device test
**Phases:** 5 / 5 done
**Last updated:** 2026-06-21

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | foundations-and-resources | - | ✅ Done | 3/3 | [PHASE_01__foundations-and-resources.md](PHASE_01__foundations-and-resources.md) |
| 02 | theme-registry-and-logic | - | ✅ Done | 2/2 | [PHASE_02__theme-registry-and-logic.md](PHASE_02__theme-registry-and-logic.md) |
| 03 | theme-application | 01, 02 | ✅ Done | 2/2 | [PHASE_03__theme-application.md](PHASE_03__theme-application.md) |
| 04 | settings-ui | 01, 02 | ✅ Done | 2/2 | [PHASE_04__settings-ui.md](PHASE_04__settings-ui.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

> **Topology note.** Phases 01 (resources) and 02 (registry logic) are independent foundations. Phase 03 consumes `R.style.ThemeOverlay_FastMediaSorter_*` (from 01) and the extended theme values (from 02). Phase 04 consumes the `color_theme_options` item order (from 01) and the extended values (from 02). It does NOT depend on 03 - it is ordered last among feature phases per the "user-visible change last" heuristic.

> **Cross-phase invariant (ordering).** The six new `<item>` entries appended to `color_theme_options` in Phase 01 and the spinner-position mapping in Phase 04 MUST share one fixed order: `DARK_GREEN, DARK_BLUE, DARK_RED, LIGHT_GREEN, LIGHT_BLUE, LIGHT_RED` = positions `3..8`. Index 0/1/2 stay `AUTO/LIGHT/DARK`.

---

## Pre-Implementation Blockers

Both strategic §6 items are resolved under the §3.3 autonomy rule ("agent may decide palettes within Material 3 with explicit assumptions"); resolutions are recorded here and applied by Phase 01/03.

- [x] **Research:** §6.1 Exact color palettes. Resolved (assumption): each custom theme is a fixed-brightness Material 3 palette. Accents - Green `#2E7D32`, Blue `#1565C0`, Red `#C62828` (dark variants on a dark tinted surface; light variants on a Material `*50` tinted surface). Full per-attribute values are listed in Phase 01 Step 01.2. Chosen for >= 4.5:1 text contrast against their surfaces (WCAG AA); device test confirms.
- [x] **Research:** §6.2 Translucent-screen compatibility. Resolved: overlays override only color attributes and never `android:windowIsTranslucent`. The window background drawable of a translucent host (e.g. `ReceiveShareActivity` via `Theme.FastMediaSorter.Transparent`) is fixed at window attach - before `onCreate` - so a later `theme.applyStyle()` cannot repaint it. The overlay therefore applies unconditionally in `BaseActivity` without breaking translucency.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/ALL_FEATURES.jsonl` carries the new capability record (added via `scripts/all_features/add.ps1`). `docs/FEATURES*.md` is NOT edited here - it is populated only by `/skill-release` from the ALL_FEATURES diff (CLAUDE.md §11).
- [ ] `dev/CHANGELOG.md` has an entry for the change set.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (public API of `ColorThemePrefs` changed).
- [ ] `/spec-check S0569` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/5 done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0569`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-21 - Initial tactical plan authored by `/spec-tech`.
- 2026-06-21 - Re-authored by `/spec-tech` after codebase verification: colors consolidated into `values/colors.xml` (fixed palettes), overlay-application placement aligned with the existing `applyCompactDialogButtonsOverlay` pattern, FEATURES edit replaced with `ALL_FEATURES.jsonl` per CLAUDE.md §11.
