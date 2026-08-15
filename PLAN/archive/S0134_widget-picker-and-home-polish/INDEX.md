# Tactical Plan: S0134 — widget-picker-and-home-polish

**Strategic spec:** [`../S0134_widget-picker-and-home-polish.md`](../S0134_widget-picker-and-home-polish.md)
**Feature:** Widget Picker & Home Screen Visual Polish
**Tier:** 3 — Moderate (ad-hoc)
**Priority:** 70
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-05-09

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | strings-and-icons | — | ✅ Done | 3/3 | [PHASE_01__strings-and-icons.md](PHASE_01__strings-and-icons.md) |
| 02 | picker-metadata | 01 | ✅ Done | 3/3 | [PHASE_02__picker-metadata.md](PHASE_02__picker-metadata.md) |
| 03 | adaptive-background | 01 | ✅ Done | 4/4 | [PHASE_03__adaptive-background.md](PHASE_03__adaptive-background.md) |
| 04 | favorites-empty-state | 01 | ✅ Done | 4/4 | [PHASE_04__favorites-empty-state.md](PHASE_04__favorites-empty-state.md) |
| 05 | docs-catalog-cleanup | 02, 03, 04 | ✅ Done | 4/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research:** RU/UK translation of `widget_resource_launch_label` resolved — RU «Папка», UK «Тека». Strategic §6.2.
- [x] **Research:** Material You theme test plan resolved — Pixel Launcher + Nova + Niagara during BlockNeedUserTest. Strategic §6.1.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` regenerated if public API changed.
- [ ] `/spec-check S0134` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0134`.

---

## Blockers Log

- 2026-05-09 — Initial plan authored. Two soft research blockers raised.
- 2026-05-10 — Both research blockers resolved by owner; implementation can proceed.
- 2026-05-10 — Step 02.1 expanded by `/spec-dev`: discovered `CameraPhotosWidgetProvider` and `RandomMusicWidgetProvider` were Kotlin-only (not registered in manifest). Owner approved registering them inside S0134 scope so all five providers appear in the picker with proper label/icon.

---

## Change Log

- 2026-05-09 — Initial tactical plan authored by `/spec-tech`.
