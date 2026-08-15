# Tactical Plan: S0101 — unified-permission-onboarding

**Strategic spec:** [`../S0101_unified-permission-onboarding.md`](../S0101_unified-permission-onboarding.md)
**Feature:** Unified permission registry + Welcome screen upgrade + contextual requests
**Tier:** 4 — Strategic
**Priority:** 5
**Status:** Done
**Phases:** 7 / 7 done
**Last updated:** 2026-05-06

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | registry-domain | — | ✅ Done | 5/5 | [PHASE_01__registry-domain.md](PHASE_01__registry-domain.md) |
| 02 | registry-impl | 01 | ✅ Done | 4/4 | [PHASE_02__registry-impl.md](PHASE_02__registry-impl.md) |
| 04 | contextual-request | 02 | ✅ Done | 4/4 | [PHASE_04__contextual-request.md](PHASE_04__contextual-request.md) |
| 05 | settings-screen | 04 | ✅ Done | 5/5 | [PHASE_05__settings-screen.md](PHASE_05__settings-screen.md) |
| 03 | welcome-integration | 05 | ✅ Done | 4/4 | [PHASE_03__welcome-upgrade.md](PHASE_03__welcome-upgrade.md) |
| 06 | ad-hoc-migration | 03, 04, 05 | ✅ Done | 3/3 | [PHASE_06__ad-hoc-migration.md](PHASE_06__ad-hoc-migration.md) |
| 07 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Phase 01 must not start while any blocker is unchecked.

- [x] **Research §6.1:** Resolved — `READ_MEDIA_*`/`READ_EXTERNAL_STORAGE` = required; `MANAGE_EXTERNAL_STORAGE`, `MANAGE_MEDIA` = optional; VR: `HAND_TRACKING`, `HEADSET_CAMERA` = optional (vr flavor only). `CAMERA` absent from all manifests.
- [x] **Research §6.2:** Resolved — `minSdk` field per registry entry is sufficient. No separate code path for API 23–25.
- [x] **Research §6.3:** Resolved — **Variant B**: after wizard, navigate to `PermissionsManagementFragment` (full-screen, shared with Settings). Wizard retains its introduction pages only.
- [x] **Research §6.4:** Resolved — **Mode A (buttons)**. Screen has: per-item status + action button, top-level **"Grant All"** batch button (`requestMultiplePermissions`), **"Open App Settings"** shortcut for OS-native toggles.
- [x] **Research §6.5:** Resolved — `SharedPreferences` (prefs name `perm_rationale_prefs`). DataStore migration not warranted.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (see strategic §8).
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated after Phase 01–06.
- [x] `/spec-check S0101` returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/7 done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0101`.

---

## Blockers Log

_(none yet)_

---

## Change Log

- 2026-05-06 — Initial tactical plan authored by `/spec-tech`.
