# Tactical Plan: S1436 - unified-permissions-contract

**Strategic spec:** [`../S1436_unified-permissions-contract.md`](../S1436_unified-permissions-contract.md)
**Research inputs:** [`research/01__permission-surface-inventory.md`](research/01__permission-surface-inventory.md), [`research/02__declared-permissions-matrix.md`](research/02__declared-permissions-matrix.md)
**Feature:** The permission registry becomes the mandatory contract for composition, text, grant route and manifest parity
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 90
**Status:** BlockNeedUserTest
**Phases:** 6 / 6 done
**Last updated:** 2026-08-06

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | build-condition-axes | - | ✅ Done | 3/3 | [PHASE_01__build-condition-axes.md](PHASE_01__build-condition-axes.md) |
| 02 | grant-kind-and-request-mechanics | 01 | ✅ Done | 6/6 | [PHASE_02__grant-kind-and-request-mechanics.md](PHASE_02__grant-kind-and-request-mechanics.md) |
| 03 | registry-completeness | 01, 02 | ✅ Done | 8/8 | [PHASE_03__registry-completeness.md](PHASE_03__registry-completeness.md) |
| 04 | manifest-parity-gate | 03 | ✅ Done | 3/3 | [PHASE_04__manifest-parity-gate.md](PHASE_04__manifest-parity-gate.md) |
| 05 | single-text-source | 03 | ✅ Done | 6/6 | [PHASE_05__single-text-source.md](PHASE_05__single-text-source.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 5/5 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All six strategic §6 items are `Resolved`, two of them by the research artifacts linked above and four by the owner quiz recorded in strategic §12.

---

## Ordering rationale

- 01 first: every later phase filters entries on axes the registry cannot express today, so the axes must exist before a row depending on one can be written.
- 02 before 03: the new entries are exactly the ones that are not granted by a runtime dialog, so the grant-kind property and the single grant-route owner must exist before an entry can declare one.
- 03 before 04: the parity gate compares the registry against the manifest, and would fail on a registry that is still missing five permissions.
- 03 before 05: the text source is read per entry, so the entry set is settled before its texts become the single source.
- 04 and 05 are independent of each other and may be implemented in either order; 04 is listed first so the mechanical protection lands before the larger text migration churns the same files.
- 06 last: it deletes the string keys that phase 05 displaces, and regenerates inventories that can only be correct once every entry and string exists.

---

## Cross-ticket note

S1426 rebuilt the shared permission row (state indicator, one action button, one-line description) and its phases 01-05 are already in the working tree; this plan is written against that rebuilt row, per strategic §10. Only S1426's own `docs-catalog-cleanup` phase is outstanding, and it touches no file this plan touches.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - not edited here; strategic §8 carries a user-facing sentence, so the capability is recorded in `docs/ALL_FEATURES.jsonl` (phase 06) and `/skill-release` publishes the showcase.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S1436` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1436`.

---

## Blockers Log

- 2026-08-06 - Resolved same day: the eight `Timber.d("S1436: ..")` probes are in the tree, at the row build, the onboarding page, the storage rationale, the QR camera denial, the screen-recording denial, the geotag toggle, the battery-optimization dialog and the recorder widget. They waited on `CODE.LOCK`, held by a live sibling session (`/spec-dev S1179`) - queued rather than walked around, per Rule 23. `.\a.ps1 dq` exit 0 with them in place.
- 2026-08-06 - Two findings parked rather than absorbed: `S1447` (settings-helper dialogs shown untracked, phase-05 boundary audit) and `S1449` (the DataStore unit-test family failing on a Windows rename, step 06.5). The stale icon inventory found by the same suite is already `S1194`.
- 2026-08-06 - Phase 02 step 02.5 blocked: `.\a.ps1 fk` is red from `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherTrayManager.kt`, a sibling session's in-flight S1415 edit, so the step's compile predicate cannot be evaluated. Not a defect in this ticket - no compile error touches a file it changed, and every step through 02.4 closed green. Next: re-run `/spec-dev S1436 --step 02.5` once that file compiles.

---

## Change Log

- 2026-08-06 - Initial tactical plan authored by `/spec-tech`.
