# Tactical Plan: S0192 — draw-editor-toolbar-ux-v2

**Strategic spec:** [`../S0192_draw-editor-toolbar-ux-v2.md`](../S0192_draw-editor-toolbar-ux-v2.md)
**Feature:** Draw Editor Toolbar UX v2
**Tier:** —
**Priority:** 50
**Status:** In Progress (BlockNeedUserTest — awaiting operator on-device verification)
**Phases:** 6 / 7 done (Phase 07 partial — 3/4 steps done; Step 07.4 = operator-run `/spec-check`)
**Last updated:** 2026-05-16

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | action-list-engine | — | ✅ Done | 5/5 | [PHASE_01__action-list-engine.md](PHASE_01__action-list-engine.md) |
| 02 | prefs-and-settings | 01 | ✅ Done | 5/5 | [PHASE_02__prefs-and-settings.md](PHASE_02__prefs-and-settings.md) |
| 03 | oval-text-tools | 01, 02 | ✅ Done | 6/6 | [PHASE_03__oval-text-tools.md](PHASE_03__oval-text-tools.md) |
| 04 | keep-export | — | ✅ Done | 3/3 | [PHASE_04__keep-export.md](PHASE_04__keep-export.md) |
| 05 | toolbar-layout-bindings | 01, 02, 03, 04 | ✅ Done | 8/8 | [PHASE_05__toolbar-layout-bindings.md](PHASE_05__toolbar-layout-bindings.md) |
| 06 | save-in-place | 05 | ✅ Done | 3/3 | [PHASE_06__save-in-place.md](PHASE_06__save-in-place.md) |
| 07 | docs-catalog-cleanup | all | 🚧 In Progress | 3/4 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None — strategic §6 has no open research items. ADR-4 (silent fallback for read-only) and §9 Antigravity review items (Path caching, ClipData for FileProvider, eraser replay on transparent canvas, single-line text input) are addressed inside individual phase prompts; not blockers.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (Phase 07) — feature introduces user-visible capabilities (undo stack, oval, text, custom palette, in-place save, settings dialog, Keep export).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (Phase 07) — public API of `ImageDrawOverlayManager` changed; new helpers added.
- [ ] `/spec-check S0192` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0192`.

---

## Blockers Log

*(empty)*

---

## Change Log

- 2026-05-16 — Initial tactical plan authored by `/spec-tech`.
