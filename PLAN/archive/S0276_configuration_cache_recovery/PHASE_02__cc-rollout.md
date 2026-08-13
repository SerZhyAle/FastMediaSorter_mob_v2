# Phase 02 - CC Rollout

**Strategic spec:** [`../S0276_configuration_cache_recovery.md`](../S0276_configuration_cache_recovery.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Enable configuration cache by default for the repository while preserving explicit noLegal opt-out behavior in every generic script that can target the Chaquopy graph.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6 research items blocking this phase are Resolved.
- [x] Working tree is clean or on a feature branch.
- [x] Dry-run evidence for non-noLegal and noLegal task graphs exists in `temp/`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `gradle.properties` | Modified | ≤ 80 |
| `scripts/builders/build-debug.PS1` | Modified | ≤ 250 |
| `scripts/builders/build-and-push-all.ps1` | Modified | ≤ 250 |
| `scripts/utils/recover-kapt-stall.ps1` | Modified | ≤ 250 |

> File projected >500 lines after edit → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 02.1 - Enable configuration cache globally

**Files:** `gradle.properties`
**Depends on:** Phase 01

**Prompt for developer:**

> Flip `org.gradle.configuration-cache` to `true` and replace the old global-disable comment with the new S0276 contract: default-on for non-noLegal, explicit `--no-configuration-cache` on noLegal/Chaquopy paths.

**Verification:**

- `Grep` - `org.gradle.configuration-cache=true` present in `gradle.properties`.
- `Grep` - `S0276` present in the surrounding comment block.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 - Verification 2/2 PASS. Files: `gradle.properties`. Dev log recorded.

---

### Step 02.2 - Guard generic noLegal helpers

**Files:** `scripts/builders/build-debug.PS1`, `scripts/utils/recover-kapt-stall.ps1`
**Depends on:** Step 02.1

**Prompt for developer:**

> For any helper that can target a noLegal task dynamically, append `--no-configuration-cache` when the resolved task scope includes `noLegal`. Do not change the non-noLegal path beyond what is necessary for this guard.

**Verification:**

- `Grep` - `--no-configuration-cache` present in `scripts/builders/build-debug.PS1`.
- `Grep` - `--no-configuration-cache` present in `scripts/utils/recover-kapt-stall.ps1`.
- `Grep` - `Log\\.d\\(` returns zero hits across both files.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 - Verification 3/3 PASS. Files: `scripts/builders/build-debug.PS1`, `scripts/utils/recover-kapt-stall.ps1`. Dev log recorded.

---

### Step 02.3 - Guard batch noLegal pass

**Files:** `scripts/builders/build-and-push-all.ps1`
**Depends on:** Step 02.2

**Prompt for developer:**

> Keep pass 1 on the non-noLegal graph cache-enabled and force pass 2 (`assembleNoLegal*`) to run with `--no-configuration-cache`. The script must document the split clearly in comments next to the Gradle invocation.

**Verification:**

- `Grep` - `assembleNoLegalDebug assembleNoLegalRelease` present in `scripts/builders/build-and-push-all.ps1`.
- `Grep` - `--no-configuration-cache` present in `scripts/builders/build-and-push-all.ps1`.
- `Grep` - `--configuration-cache` present in `scripts/builders/build-and-push-all.ps1`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 - Verification 3/3 PASS. Files: `scripts/builders/build-and-push-all.ps1`. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - validated by `:app_v2:assembleStandardDebug --configuration-cache` and `:app_v2:assembleNoLegalDebug --no-configuration-cache`.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/<module>.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module <app_v2|wear>` (one-shot wrapper for scan + render).

---

## Handoff Notes to Next Phase

Global CC state is now a repo contract rather than a local convention. Every remaining noLegal path is explicit about the opt-out.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
