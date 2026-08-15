# Tactical Plan: S1289 - launcher-resource-cells-composed-logo

**Strategic spec:** [`../S1289_launcher-resource-cells-composed-logo.md`](../S1289_launcher-resource-cells-composed-logo.md)
**Research inputs:** none (strategic §6 fully Resolved inline on 2026-08-05)
**Feature:** Launcher resource tiles show the composed resource logo used by the main window
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 2 / 2 done
**Last updated:** 2026-08-05

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | composed-resource-icon | - | ✅ Done | 5/6 (01.5 deferred) | [PHASE_01__composed-resource-icon.md](PHASE_01__composed-resource-icon.md) |
| 02 | docs-catalog-cleanup | 01 | ✅ Done | 2/2 | [PHASE_02__docs-catalog-cleanup.md](PHASE_02__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Every strategic §6 item carries `Status: Resolved`.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - not touched here; strategic §8 names a user-visible capability, which is recorded in `docs/ALL_FEATURES.jsonl` and published by `/skill-release`.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated - phase 01 adds public types.
- [ ] `/spec-check S1289` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1289`.

---

## Blockers Log

- none

---

## Change Log

- 2026-08-05 - Initial tactical plan authored by `/spec-tech`.
