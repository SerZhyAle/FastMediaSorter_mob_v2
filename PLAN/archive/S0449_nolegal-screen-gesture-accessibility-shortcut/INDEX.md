# Tactical Plan: S0449 - nolegal-screen-gesture-accessibility-shortcut

**Strategic spec:** [`../S0449_nolegal-screen-gesture-accessibility-shortcut.md`](../S0449_nolegal-screen-gesture-accessibility-shortcut.md)
**Research inputs:** none
**Feature:** Quick jump to accessibility settings inside the Left-edge screen gestures group
**Tier:** 2 - Easy (ad-hoc)
**Priority:** 55
**Status:** In Progress
**Phases:** 3 / 3 done
**Last updated:** 2026-06-15

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | strings | - | ✅ Done | 1/1 | [PHASE_01__strings.md](PHASE_01__strings.md) |
| 02 | settings-group-ui | 01 | ✅ Done | 3/3 | [PHASE_02__settings-group-ui.md](PHASE_02__settings-group-ui.md) |
| 03 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - both strategic §6 research items are Resolved.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES_noLegal*.md` (EN/RU/UK) updated - strategic §8 mandates a one-line entry (noLegal-only, gitignored; public `docs/FEATURES*.md` untouched).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S0449` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0449`.

---

## Blockers Log

- none.

---

## Change Log

- 2026-06-15 - Initial tactical plan authored by `/spec-tech`.
