# Tactical Plan: S0079 — bugfix-file-op-progress-dialog-landscape-npe

**Strategic spec:** [`../S0079_bugfix-file-op-progress-dialog-landscape-npe.md`](../S0079_bugfix-file-op-progress-dialog-landscape-npe.md)
**Feature:** NPE fix — progress dialog landscape layout missing two TextViews
**Tier:** 1 — Quick Win
**Priority:** 80
**Status:** Done
**Phases:** 2 / 2 done
**Last updated:** 2026-05-04

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | landscape-layout-fix | — | ✅ Done | 1/1 | [PHASE_01__landscape-layout-fix.md](PHASE_01__landscape-layout-fix.md) |
| 02 | docs-catalog-cleanup | 01 | ✅ Done | 2/2 | [PHASE_02__docs-catalog-cleanup.md](PHASE_02__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

No open research items — see strategic §6.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` — no update needed (bugfix, not a new feature; see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for `app_v2/src/main/res/layout-land/dialog_file_operation_progress.xml`.
- [ ] `dev/CATALOG/app_v2.jsonl` — no regen needed (no Kotlin file changed).
- [ ] `/spec-check S0079` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0079`.

---

## Blockers Log

*(none)*

---

## Change Log

- 2026-05-04 — Initial tactical plan authored by `/spec-tech`.
