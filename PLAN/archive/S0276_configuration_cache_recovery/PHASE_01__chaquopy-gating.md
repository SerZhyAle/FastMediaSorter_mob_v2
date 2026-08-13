# Phase 01 - Chaquopy Gating

**Strategic spec:** [`../S0276_configuration_cache_recovery.md`](../S0276_configuration_cache_recovery.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 2 / 2
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Constrain Chaquopy activation to explicit noLegal intent only, so IDE sync and unrelated Gradle invocations cannot accidentally pull the incompatible plugin into the graph.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6 research items blocking this phase are Resolved.
- [x] Working tree is clean or on a feature branch.
- [x] Backup for `app_v2/build.gradle.kts` exists in `temp/`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/build.gradle.kts` | Modified | ≤ 500 |
| `a.ps1` | Modified | ≤ 250 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 01.1 - Remove machine-local Chaquopy fallback

**Files:** `app_v2/build.gradle.kts`
**Depends on:** - start of phase

**Prompt for developer:**

> Delete the `local.properties` fallback from the `isNoLegalBuild` calculation. Keep only explicit `-Pchaquopy.enabled=true|false` override and task-name auto-detect for `noLegal`. Update the surrounding comments so they document why the fallback is unsafe once configuration cache is enabled by default.

**Verification:**

- `Grep` - `val isNoLegalBuild = when` present in `app_v2/build.gradle.kts`.
- `Grep` - `_chaquopyLocalProps` returns zero hits in `app_v2/build.gradle.kts`.
- `Grep` - `else -> false` present in the `isNoLegalBuild` branch.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 - Verification 3/3 PASS. Files: `app_v2/build.gradle.kts`. Dev log recorded.

---

### Step 01.2 - Stop launcher-side local.properties mutation

**Files:** `a.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Remove the helper that rewrites `local.properties` to toggle `chaquopy.enabled`. The launcher must stop mutating machine-local state now that build logic no longer reads it for Chaquopy activation.

**Verification:**

- `Grep` - `Set-ChaquopyLocalState` returns zero hits in `a.ps1`.
- `Grep` - `chaquopyLocalState` returns zero hits in `a.ps1`.
- `Grep` - `chaquopy.enabled=true` returns zero hits in `a.ps1`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 - Verification 3/3 PASS. Files: `a.ps1`. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - validated by `:app_v2:assembleStandardDebug --configuration-cache` and `:app_v2:assembleNoLegalDebug --no-configuration-cache`.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/<module>.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module <app_v2|wear>` (one-shot wrapper for scan + render).

---

## Handoff Notes to Next Phase

No implicit local toggle remains. Every noLegal path must now declare both Chaquopy intent and CC opt-out in its own invocation contract.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
