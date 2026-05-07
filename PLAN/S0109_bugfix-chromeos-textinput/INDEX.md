# Tactical Plan: S0109 — bugfix-chromeos-textinput

**Strategic spec:** [`../S0109_bugfix-chromeos-textinput.md`](../S0109_bugfix-chromeos-textinput.md)
**Feature:** Chrome OS (ARC++) text input fix in form screens
**Tier:** ad-hoc bugfix
**Priority:** 90
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-05-07

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | addresource-guard | — | ✅ Done | 2/2 | [PHASE_01__addresource-guard.md](PHASE_01__addresource-guard.md) |
| 02 | resource-editor-escape | 01 | ✅ Done | 1/1 | [PHASE_02__resource-editor-escape.md](PHASE_02__resource-editor-escape.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 3/3 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Research items from strategic §6 resolved through code analysis — no blockers.

**§6.1 Scope of screens — Resolved.**
Activities with `onKeyDown` override identified: `AddResourceActivity` (primary — uses `AddResourceKeyboardDelegate` with no text-field guard), `SettingsActivity` (already fixed via `SettingsKeyboardNavigationManager.isTextEditorFocused()`), `ResourceEditorActivity` (minor — only Escape/F1/Ctrl+S, character keys pass through).

**§6.2 Chrome OS dispatch mechanism — Resolved (implementation-independent).**
Whether Chrome OS ARC++ inverts dispatch order (Activity before View) or bypasses View entirely, the guard in `handleKeyDown()` — returning `false` for non-global keys when a text editor is focused — passes control back to `super.onKeyDown()` in both scenarios. The existing `SettingsKeyboardNavigationManager` uses this pattern and is confirmed not to block text input.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0109` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0109`.

---

## Blockers Log

_(none)_

---

## Change Log

- 2026-05-07 — Initial tactical plan authored by `/spec-tech`.
