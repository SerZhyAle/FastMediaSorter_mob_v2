# Phase 06 - Docs / Catalog / Cleanup

**Strategic spec:** [`../S0301_nolegal-office-document-embedded-renderer.md`](../S0301_nolegal-office-document-embedded-renderer.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 05
**Blocks:** none - final phase
**Steps done:** 4 / 4
**Started:** 2026-05-30
**Completed:** 2026-05-30

---

## Objective

Finalize noLegal-only feature docs, Kotlin catalog refresh, and the spec closure inputs after implementation.

---

## Prerequisites

- [x] Phase 05 is ✅ Done.
- [x] Strategic §6.8 blocker is Resolved.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES_noLegal.md` | Modified | ≤ 260 |
| `docs/FEATURES_noLegal_RU.md` | Modified | ≤ 260 |
| `docs/FEATURES_noLegal_UK.md` | Modified | ≤ 260 |
| `PLAN/S0301_nolegal-office-document-embedded-renderer.md` | Modified | ≤ 320 |
| `PLAN/S0301_nolegal-office-document-embedded-renderer/INDEX.md` | Modified | ≤ 260 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 06.1 - Add the noLegal feature inventory entry

**Files:** `docs/FEATURES_noLegal.md`, `docs/FEATURES_noLegal_RU.md`, `docs/FEATURES_noLegal_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a concise noLegal-only feature bullet describing the embedded Office viewer, its read-only scope, and the family coverage that actually shipped. Do not touch public `docs/FEATURES*.md` because this capability is not part of market builds.

**Verification:**

- `Grep` - `Office` exists in `docs/FEATURES_noLegal.md`.
- `Grep` - `Office` exists in `docs/FEATURES_noLegal_RU.md`.
- `Grep` - `Office` exists in `docs/FEATURES_noLegal_UK.md`.
- `Grep` - no new `Office` bullet was added to public `docs/FEATURES.md`.

**Status:** `[x]` done

---

### Step 06.2 - Close changelog and catalog hygiene

**Files:** `PLAN/S0301_nolegal-office-document-embedded-renderer/INDEX.md`
**Depends on:** Step 06.1

**Prompt for developer:**

> Run the mechanical closure tasks for all Kotlin/XML/doc changes from this ticket: dev log entries, `catalog_sync` for `app_v2`, and any string-localization audit triggered by the Office viewer keys. Update the tactical index progress rows and counters to reflect the actual phase state.

**Verification:**

- `Grep` - `Phase 06` row is `✅ Done` in `INDEX.md`.
- `Grep` - `Phases: 6 / 6 done` exists in `INDEX.md`.
- `Grep` - `S0301` exists in `dev/CHANGELOG.md`.

**Status:** `[x]` done

---

### Step 06.3 - Prepare the audit surface for `/spec-check`

**Files:** `PLAN/S0301_nolegal-office-document-embedded-renderer.md`, `PLAN/S0301_nolegal-office-document-embedded-renderer/INDEX.md`
**Depends on:** Step 06.2

**Prompt for developer:**

> Ensure the strategic and tactical artifacts are ready for `/spec-check S0301`: no unchecked phase steps, no stale blockers, and no drift between the strategic criteria and what actually shipped.

**Verification:**

- `Grep` - no unchecked `- [ ]` markers remain in completed phases for S0301 except the INDEX completion gate.
- `Grep` - no `TODO(phase-06)` hits exist under `PLAN/S0301_nolegal-office-document-embedded-renderer*`.
- `Grep` - `Status:` in the strategic spec is still `Tactical` or later before `/spec-check`. Result: `In Progress` (later than Tactical, before Verified) - OK.

**Status:** `[x]` done

---

### Step 06.4 - Run final validation for the noLegal target

**Files:** `PLAN/S0301_nolegal-office-document-embedded-renderer/INDEX.md`
**Depends on:** Step 06.3

**Prompt for developer:**

> Run `/build` for the noLegal target and record the exact validation command/result in the step log. If it passes, proceed to `/spec-check S0301`; if it fails, stop and keep the phase open.

**Step log:** `/build` noLegal target = `$env:GRADLE_USER_HOME=temp\gradle_iso; .\gradlew.bat assembleNoLegalDebug --console=plain --no-daemon` -> BUILD SUCCESSFUL (1m14s). Standard target also verified: `assembleStandardDebug` -> BUILD SUCCESSFUL (1m33s). Phase 06 changed only docs (no Kotlin), so the Phase 05 build remains representative. Result: PASS -> proceed to `/spec-check S0301`.

**Verification:**

- `Grep` - the Step 06.4 log records the `/build` command and PASS/FAIL result.
- `Grep` - `/spec-check S0301` is listed in the INDEX Completion Gate.
- `Grep` - no `TODO(phase-06)` hits remain.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] Project compiles - noLegal BUILD SUCCESSFUL (Phase 05, no Kotlin changed in Phase 06).
- [x] `Grep` for `TODO(phase-06)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) - no data migration or public market surface changed.