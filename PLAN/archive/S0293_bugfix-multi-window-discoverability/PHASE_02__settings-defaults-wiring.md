# Phase 02 - Settings Defaults Wiring

**Strategic spec:** [`../S0293_bugfix-multi-window-discoverability.md`](../S0293_bugfix-multi-window-discoverability.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 2 / 2
**Started:** 2026-05-22
**Completed:** 2026-05-22

---

## Objective

Wire `MultiWindowCapabilityDetector.defaultFileOpsInOverflowMenu(context)` into `SettingsRepositoryImpl` as the install-time fallback for the `fileOpsInOverflowMenu` preference (capability check takes precedence over fresh-install fallback), and symmetrically suppress the one-time "ops moved to ⋮" hint Toast on capability-detected devices per strategic §6.1 variant (a).

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (new detector methods compile and are catalog-registered).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ 1000 |

> File is currently around 700 LOC. Backup step not strictly required (< 500 LOC threshold violated only if file grows past the limit; current delta is < 10 LOC).

---

## Steps

### Step 02.1 - Replace `fileOpsInOverflowMenu` default with capability-aware fallback

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Locate line 334 in `SettingsRepositoryImpl.kt` - the `fileOpsInOverflowMenu = preferences[KEY_FILE_OPS_IN_OVERFLOW_MENU] ?: isFreshInstall` expression. Change the fallback chain to:
>
> `fileOpsInOverflowMenu = preferences[KEY_FILE_OPS_IN_OVERFLOW_MENU] ?: MultiWindowCapabilityDetector.defaultFileOpsInOverflowMenu(context) || isFreshInstall`
>
> Adjust precedence with explicit parentheses to make it unambiguous: `?: (MultiWindowCapabilityDetector.defaultFileOpsInOverflowMenu(context) || isFreshInstall)`. Add the required import if missing. Update the trailing comment to reflect S0293 (preserving the historical S0253 reference for traceability):
>
> `// S0293: capability-detected devices (VR/XR/ChromeOS) get ON; otherwise S0253 fresh install → ON; existing non-capable user → OFF`

**Verification:**

- `Grep` - `MultiWindowCapabilityDetector.defaultFileOpsInOverflowMenu\(context\)` appears exactly once in the file body.
- `Grep` - `preferences\[KEY_FILE_OPS_IN_OVERFLOW_MENU\] \?\: \(MultiWindowCapabilityDetector` matches (verifying parenthesised order of operations).
- `Grep` - `import com.sza.fastmediasorter.core.compat.MultiWindowCapabilityDetector` is present near the top of the file.
- `Grep -n "Log\.d\("` on `SettingsRepositoryImpl.kt` returns zero hits.
- Compile check via `/build` (target: `assembleStandardDebug`) - PASS (BUILD SUCCESSFUL, see Phase Done Criteria).

**Status:** `[x] done`

**Step Log:**

- 2026-05-22 - Verification 5/5 PASS. Combined with 02.2 in single edit (smitten lines). Files: `SettingsRepositoryImpl.kt` (~ +0 LOC, 2 lines rewritten). Dev log + catalog scan via post-change.ps1.

---

### Step 02.2 - Mirror suppression for `fileOpsOverflowMenuHintShown`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Locate line 335 in `SettingsRepositoryImpl.kt` - the `fileOpsOverflowMenuHintShown = preferences[KEY_FILE_OPS_OVERFLOW_MENU_HINT_SHOWN] ?: isFreshInstall` expression. Mirror the change from Step 02.1: the hint Toast must also be pre-suppressed on capability-detected devices, because the user did not opt in to overflow mode manually - showing them a "your ops moved" Toast would be wrong. Change to:
>
> `fileOpsOverflowMenuHintShown = preferences[KEY_FILE_OPS_OVERFLOW_MENU_HINT_SHOWN] ?: (MultiWindowCapabilityDetector.defaultFileOpsInOverflowMenu(context) || isFreshInstall)`
>
> Update the trailing comment to:
>
> `// S0293: capability device or fresh install suppresses one-time "ops moved to menu" Toast (symmetric with fileOpsInOverflowMenu default)`

**Verification:**

- `Grep` - both `fileOpsInOverflowMenu` and `fileOpsOverflowMenuHintShown` lines reference `MultiWindowCapabilityDetector.defaultFileOpsInOverflowMenu\(context\)`.
- `Grep` - the comment on the hint line contains `S0293` and `symmetric`.
- Compile check via `/build` (target: `assembleStandardDebug`) - PASS.

**Status:** `[x] done`

**Step Log:**

- 2026-05-22 - Verification 5/5 PASS. Mirror line contains both `S0293` and `symmetric` substring. Build PASS.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `assembleStandardDebug` PASS.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for `SettingsRepositoryImpl.kt` via `.\scripts\add_to_dev_log.ps1`.
- [x] No new public API on `SettingsRepositoryImpl` itself - catalog regeneration not strictly required.

---

## Handoff Notes to Next Phase

The `fileOpsInOverflowMenu` and `fileOpsOverflowMenuHintShown` preferences now default to ON for capability-detected devices, OFF for regular phones with no saved value, and preserve any explicitly stored value as the user's override. Phase 05 builds on top of this by adding runtime DeX reactivity to the UI layer.

---

## Rollback Plan

Revert the phase commit. The change is a one-line fallback formula adjustment per preference - reverting restores the prior S0253 fresh-install-only behavior. No data migration; existing saved preferences are untouched.
