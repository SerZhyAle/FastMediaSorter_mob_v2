# Tactical Plan: S1579 - bugfix-camera-open-blocks-main-thread

**Strategic spec:** [`../S1579_bugfix-camera-open-blocks-main-thread.md`](../S1579_bugfix-camera-open-blocks-main-thread.md)
**Research inputs:** none
**Feature:** Camera bring-up off the main thread
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 90
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-08-11

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | directory-prep-off-main | - | ✅ Done | 4/4 | [PHASE_01__directory-prep-off-main.md](PHASE_01__directory-prep-off-main.md) |
| 02 | extension-availability-cache | 01 | ✅ Done | 2/2 | [PHASE_02__extension-availability-cache.md](PHASE_02__extension-availability-cache.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 2/2 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- none - strategic §6 carries no open research item.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: strategic spec is a bugfix with no showcase sentence.
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [x] Audit returns `Verified` - device re-verification 2026-08-12, recorded in the strategic spec `## Last Audit`.
- [x] Strategic spec `Status:` advanced to `Verified`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1579`.

---

## Blockers Log

- none.

---

## Change Log

- 2026-08-11 - Initial tactical plan authored by `/spec-tech`.
