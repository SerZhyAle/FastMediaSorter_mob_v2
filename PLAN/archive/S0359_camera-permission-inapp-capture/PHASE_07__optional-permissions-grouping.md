# Phase 07 - "Optional permissions" visual grouping in the permissions screen

**Strategic spec:** [`../S0359_camera-permission-inapp-capture.md`](../S0359_camera-permission-inapp-capture.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** -
**Steps done:** 3 / 3
**Started:** 2026-06-05
**Completed:** 2026-06-05

---

## Objective

Group all `optional==true` permission entries (camera, microphone, local network, notifications, battery) under one synthetic "Optional permissions" header in the permissions screen, each with its usage description. Required entries keep their existing group headers. Visual-only - the set of requested permissions does not change.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (CAMERA entry registered as optional).
- [ ] `/ui-clarify` confirms required entries keep group headers while optional entries collapse under one header.
- [ ] Working tree clean or on `DEBUG-v013`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PermissionsManagementFragment.kt` | Modified | ≤ +30 |
| `app_v2/src/main/res/values/strings.xml` (+ `-ru`, `-uk`) | Modified | ≤ +2 each |

> No `layout-land/fragment_permissions_management.xml` exists (vertically-scrolling list) - no landscape mirror needed.

---

## Steps

### Step 07.1 - "Optional permissions" header string (EN/RU/UK lockstep)

**Files:** `app_v2/src/main/res/values/strings.xml` (+ `-ru`, `-uk`)
**Depends on:** - start of phase

**Prompt for developer:**

> Add `perm_group_optional` ("Optional permissions" / RU / UK) via `set-android-string.ps1 -Action add`. Tone per COMMUNICATION_POLICY; intent is reassurance ("these are optional and explained"), not alarm.

**Verification:**

- `Grep` - `perm_group_optional` in all three `strings.xml` (3 hits).
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "perm_group_optional"` exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

---

### Step 07.2 - Partition rows by optional in buildRows()

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PermissionsManagementFragment.kt`
**Depends on:** Step 07.1

**Prompt for developer:**

> Change `buildRows()` (~line 192-202): emit required entries under their existing per-group headers first (current behaviour, filtered to `!entry.optional`), then emit a single synthetic header row using `perm_group_optional` followed by all `entry.optional == true` entries (across groups), each with its status. Build the synthetic header as a `PermissionRow.Header` carrying a `PermissionGroupHeader` whose `titleRes = R.string.perm_group_optional` (the existing `Header.bind` already renders `titleRes`). Keep adapter and row model unchanged if the header type already supports an arbitrary `titleRes`; otherwise add a minimal synthetic-header case. WHY-comment: visual grouping of optional permissions to reduce install-time alarm (S0359).

**Verification:**

- `Grep` - `perm_group_optional` referenced in `PermissionsManagementFragment.kt`.
- `Grep` - `optional` referenced inside `buildRows` (partition predicate).
- `Grep -n "Log\.d\("` returns zero hits.

**Status:** `[x] done`

---

### Step 07.3 - Verify required headers preserved

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PermissionsManagementFragment.kt`
**Depends on:** Step 07.2

**Prompt for developer:**

> Confirm required (`optional == false`) entries still render under their own group headers (STORAGE etc.) and only optional entries collapse under the synthetic header. No change to `PermissionRegistryRepositoryImpl.getEntries()` (the source list and gating stay intact).

**Verification:**

- `/build` standardDebug compiles.
- `Grep` - `PermissionRegistryRepositoryImpl` unchanged for `getEntries()` filter (no diff in that method). expected: no change | actual: <fill>.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 07.*` is `[x] done`.
- [ ] Project compiles - `/build` standardDebug.
- [ ] `Grep` for `TODO(phase-07)` returns zero hits.
- [ ] Dev log entry for every file in "Files Touched".

---

## Handoff Notes to Next Phase

The permissions screen shows CAMERA (and the other optional permissions) under one "Optional permissions" section with descriptions. Final phase handles docs, functionality log, and catalog.

---

## Rollback Plan

Revert phase commit(s) - `buildRows()` returns to per-group grouping. No persisted state changed.
