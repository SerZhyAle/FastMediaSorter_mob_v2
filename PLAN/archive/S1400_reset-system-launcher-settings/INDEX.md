# Tactical Plan: S1400 - reset-system-launcher-settings

**Strategic spec:** [`../S1400_reset-system-launcher-settings.md`](../S1400_reset-system-launcher-settings.md)
**Research inputs:** none
**Feature:** Reset the system launcher back to its as-installed state
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** In Progress
**Phases:** 4 / 5 done - Phase 05 added 2026-08-06 after the on-device run disproved the original ADR-2
**Last updated:** 2026-08-06

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | storage-clear | - | ✅ Done | 4/4 | [PHASE_01__storage-clear.md](PHASE_01__storage-clear.md) |
| 02 | reset-use-case | 01 | ✅ Done | 3/3 | [PHASE_02__reset-use-case.md](PHASE_02__reset-use-case.md) |
| 03 | settings-dialog-entry | 02 | ✅ Done | 5/5 | [PHASE_03__settings-dialog-entry.md](PHASE_03__settings-dialog-entry.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |
| 05 | reseed-owned-by-launcher | 02, 03 | ⬜ Not started | 0/3 | [PHASE_05__reseed-owned-by-launcher.md](PHASE_05__reseed-owned-by-launcher.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - every strategic §6 item is Resolved.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - not touched here; strategic §8 names a capability, which is recorded in `docs/ALL_FEATURES.jsonl` (Phase 04) and published by `/skill-release`.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated - the phase set adds a use case and public repository methods.
- [ ] `/spec-check S1400` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1400`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-08-06 - Initial tactical plan authored by `/spec-tech`.
