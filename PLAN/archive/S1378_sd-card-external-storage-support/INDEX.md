# Tactical Plan: S1378 - sd-card-external-storage-support

**Strategic spec:** [`../S1378_sd-card-external-storage-support.md`](../S1378_sd-card-external-storage-support.md)
**Research inputs:** [`research/01__as-is-removable-storage.md`](research/01__as-is-removable-storage.md)
**Feature:** SD card and mounted external storage support
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 50
**Status:** In Progress
**Phases:** 7 / 7 done
**Last updated:** 2026-08-05

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | volume-registry | - | ✅ Done | 6/6 | [PHASE_01__volume-registry.md](PHASE_01__volume-registry.md) |
| 02 | resource-volume-binding | 01 | ✅ Done | 5/5 | [PHASE_02__resource-volume-binding.md](PHASE_02__resource-volume-binding.md) |
| 03 | saf-operation-strategy | 01 | ✅ Done | 5/5 | [PHASE_03__saf-operation-strategy.md](PHASE_03__saf-operation-strategy.md) |
| 04 | saf-directory-operations | 03 | ✅ Done | 7/7 | [PHASE_04__saf-directory-operations.md](PHASE_04__saf-directory-operations.md) |
| 05 | destination-space-preflight | 01, 04 | ✅ Done | 4/4 | [PHASE_05__destination-space-preflight.md](PHASE_05__destination-space-preflight.md) |
| 06 | volume-picker-ui | 01, 02 | ✅ Done | 6/6 | [PHASE_06__volume-picker-ui.md](PHASE_06__volume-picker-ui.md) |
| 07 | docs-catalog-cleanup | all | ✅ Done | 5/5 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Coverage notes

Two strategic §2 goals are already satisfied by shipped code and carry no phase of their own - both are acceptance checks during the device sweep, not work items:

- Goal 2 (media on the volume browses and sorts like built-in storage) - the local scanner already branches to its document-tree path for a `content://` resource; nothing blocks it once such a resource can be added.
- Goal 6 (granted access survives a restart) - the add-resource flow already takes a persistable permission on the chosen tree; Phase 02 only adds the volume binding beside it.

---

## Pre-Implementation Blockers

Strategic §6 carries no item in `Status: Open` - items 2, 3 and 4 were resolved as recorded decisions before this plan was written, and §3.4 holds the owner's `/ui-clarify` record covering every surface Phase 06 touches.

- none

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - strategic §8 carries a FEATURES sentence, so the release pipeline picks it up from the `ALL_FEATURES` record written in Phase 07.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated - this spec adds public classes.
- [ ] `/spec-check S1378` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/7 done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1378`.

---

## Blockers Log

- none

---

## Change Log

- 2026-08-03 - Initial tactical plan authored by `/spec-tech`.
- 2026-08-05 - Phase 01 implemented. Two files added beyond the phase's original list (`StorageVolumeSource.kt`, `StorageVolumeEntryPoint.kt`) because `UriPathResolver` is a synchronous object and cannot await the suspend repository - rationale recorded in the phase file.
