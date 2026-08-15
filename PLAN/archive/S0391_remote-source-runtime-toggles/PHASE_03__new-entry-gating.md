# Phase 03 - New Entry Gating

**Strategic spec:** [`../S0391_remote-source-runtime-toggles.md`](../S0391_remote-source-runtime-toggles.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 06 (none hard)
**Steps done:** 5 / 5
**Started:** 2026-06-14
**Completed:** 2026-06-14

---

## Objective

Apply the gate at every "create / choose a remote source" surface: the add-resource type-selection cards (`AddResourceActivity`/`AddResourceFormManager`), filter chips, and the main resource-type tab strip - including the rule that the strip disappears entirely when no remote source is enabled. Replace the user-facing `BuildConfig.SUPPORT_CLOUD` reads with gate queries.

Plan correction (2026-06-14): the originally-targeted `ResourceTypeSelectorDialog` is dead code (zero callers, its layout even held a dangling `@string/cloud_folder_description`). Step 03.1 therefore deletes it (dead-weight hygiene, CLAUDE Rule 21) and the real type-picker gating folds into Step 03.2 (`AddResourceFormManager.applyFlavorRestrictions`, the single place the type cards' visibility is set).

---

## Prerequisites

- [ ] Phase 02 ✅ Done (`RemoteSourceAvailabilityGate` available for injection).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ResourceTypeSelectorDialog.kt` | Deleted (dead) | - |
| `app_v2/src/main/res/layout/dialog_resource_type_selector.xml` | Deleted (dead) | - |
| `app_v2/src/main/res/layout-land/dialog_resource_type_selector.xml` | Deleted (dead) | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceFormManager.kt` | Modified | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/FilterResourceDialog.kt` | Modified | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainResourceTabsManager.kt` | Modified | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` (or its tabs owner) | Modified | ≤ 1000 |

> The tab strip must rebuild when toggles change at runtime. The actual observer host is whichever class owns `MainResourceTabsManager` - confirm at impl, keep activity logic delegated to the manager.

---

## Steps

### Step 03.1 - Delete the dead add-folder type-picker dialog

**Files:** `ui/dialog/ResourceTypeSelectorDialog.kt` (Deleted), `res/layout/dialog_resource_type_selector.xml` (Deleted), `res/layout-land/dialog_resource_type_selector.xml` (Deleted)
**Depends on:** - start of phase

**Prompt for developer:**

> `ResourceTypeSelectorDialog` has zero callers anywhere in the module and its layout referenced an undefined `@string/cloud_folder_description` (it never inflated). Delete the Kotlin file and both layout variants, and remove the now-orphaned `add_smb_network_shares` string (EN/RU/UK) via `set-android-string.ps1 -Action remove`. The live type picker is the `AddResourceActivity` `layoutResourceTypes` cards, gated in Step 03.2.

**Verification:**

- `Grep` - zero hits for `ResourceTypeSelectorDialog` / `dialog_resource_type_selector` / `DialogResourceTypeSelectorBinding` across `app_v2/src`.
- `Grep` - zero hits for `add_smb_network_shares` across `app_v2/src`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-14 - Verification 2/2 PASS (0 dialog refs, 0 string refs). Owner-confirmed deletion of dead `ResourceTypeSelectorDialog` (zero callers; layout held a dangling `@string/cloud_folder_description` so it never inflated). Removed the .kt + both layout variants (portrait + land) + orphaned `add_smb_network_shares` (EN/RU/UK). Dead-weight hygiene, CLAUDE Rule 21.

---

### Step 03.2 - Gate the add-resource type-selection cards

**Files:** `ui/addresource/AddResourceActivity.kt`, `ui/addresource/AddResourceFormManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Inject `RemoteSourceAvailabilityGate` into `AddResourceActivity` (it is `@AndroidEntryPoint`) and pass it to the `AddResourceFormManager` constructor. In `applyFlavorRestrictions()` gate the three remote type cards: `cardNetworkFolder` (SMB) visible only if `isEnabled(SMB)`, `cardSftpFolder` ((S)FTP) only if `isEnabled(SFTP) || isEnabled(FTP)`, `cardCloudStorage` (Cloud) only if `anyCloudEnabled()` - replacing the `BuildConfig.SUPPORT_CLOUD` read (the gate already folds compile-time cloud support, so no second guard). Local stays always visible. Leave the media-type checkbox `BuildConfig.SUPPORT_*` reads untouched (media capability, not remote-source scope).

**Verification:**

- `Grep -n "BuildConfig.SUPPORT_CLOUD"` - zero hits in `AddResourceFormManager.kt`.
- `Grep` - `anyCloudEnabled` and `RemoteSourceId.SMB` referenced in `AddResourceFormManager.kt`.
- `Grep` - `RemoteSourceAvailabilityGate` referenced in `AddResourceActivity.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-14 - Verification 3/3 PASS. Injected `RemoteSourceAvailabilityGate` into `AddResourceActivity` (@AndroidEntryPoint), passed to `AddResourceFormManager` ctor. `applyFlavorRestrictions()` now gates `cardNetworkFolder`=isEnabled(SMB), `cardSftpFolder`=isEnabled(SFTP)||isEnabled(FTP), `cardCloudStorage`=anyCloudEnabled(); removed the `BuildConfig.SUPPORT_CLOUD` read. Media-type checkbox SUPPORT_* reads left untouched (out of remote-source scope).

---

### Step 03.3 - Gate the filter chips

**Files:** `ui/main/FilterResourceDialog.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Replace the `ResourceType.CLOUD -> BuildConfig.SUPPORT_CLOUD` chip guard with the gate. Show the SMB chip if `isEnabled(SMB)`, the FTP/SFTP chip if `isEnabled(SFTP) || isEnabled(FTP)`, the Cloud chip if `anyCloudEnabled()`.

**Verification:**

- `Grep -n "BuildConfig.SUPPORT_CLOUD"` - zero hits in `FilterResourceDialog.kt`.
- `Grep` - `RemoteSourceAvailabilityGate` referenced in `FilterResourceDialog.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-14 - Verification 2/2 PASS (0 SUPPORT_CLOUD; gate at lines 15/31/129). Made dialog `@AndroidEntryPoint`, injected gate. Resource-type chip filter: LOCAL always, CLOUD via `anyCloudEnabled()`, SMB/SFTP/FTP via `RemoteSourceId.networkFromResourceType(type)` + `isEnabled`. Media-type chip SUPPORT_* reads untouched.

---

### Step 03.4 - Gate the tab strip and add the vanish rule

**Files:** `ui/main/helpers/MainResourceTabsManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Inject/pass the gate into `MainResourceTabsManager`. Build the SMB tab only if `isEnabled(SMB)`, the FTP/SFTP tab only if `isEnabled(SFTP) || isEnabled(FTP)`, the Cloud tab only if `anyCloudEnabled()` - removing all `BuildConfig.SUPPORT_CLOUD` reads (including the favorites-index math, which should use the actual built-tab list, not a hardcoded index). When `!gate.anyRemoteEnabled()`, hide the entire `TabLayout` (set `View.GONE`) because only ALL+Local would remain. Rebuild tab index mappings from the dynamically built tab set rather than fixed positions. Do not add a debug-verification tag here (deferred to the final `BlockNeedUserTest` transition, CLAUDE Rule 2).

**Verification:**

- `Grep -n "BuildConfig.SUPPORT_CLOUD"` - zero hits in `MainResourceTabsManager.kt`.
- `Grep` - `anyRemoteEnabled` referenced and a visibility assignment on the `TabLayout`.
- `Grep` - no `Timber.d("S0391:` debug tag in this file (an explanatory `// S0391:` comment is allowed; the debug probe is deferred to the final `BlockNeedUserTest` transition).

**Status:** `[x]` done

**Step Log:**

- 2026-06-14 - Verification 3/3 PASS (0 SUPPORT_CLOUD; `anyRemoteEnabled` + `tabLayout.isVisible` at line 52; no Timber tag). Rewrote `MainResourceTabsManager` to a `builtTabs` list as the single source of truth: ALL+Local always, SMB/(S)FTP/Cloud gated; strip hidden via `tabLayout.isVisible = gate.anyRemoteEnabled()`; index<->tab mapping derived from `builtTabs` (no hardcoded indices); the dead favorites-reselect now keys off `indexOf(FAVORITES)` (== -1, never fires - Favorites is an action-only button). Gate passed via ctor from MainActivity. `.\a.ps1 fk` BUILD SUCCESSFUL.

---

### Step 03.5 - Rebuild tabs when toggles change at runtime

**Files:** `ui/main/MainActivity.kt`, `core/capability/RemoteSourceAvailabilityGate.kt`
**Depends on:** Step 03.4

**Prompt for developer:**

> Make the tab-strip host observe settings/gate changes lifecycle-safely (`collectOnLifecycle`/`repeatOnLifecycle`, never a bare `lifecycleScope.launch { collect }`) and rebuild the tab strip when remote-source enablement changes, so disabling the last remote source hides the strip without an app restart. Keep the logic in the manager; the host only triggers a rebuild. To avoid a race between the gate's internal snapshot update and the UI, give the gate a reactive surface (`enabledRemoteSources(): Flow<Set<RemoteSourceId>>`, `distinctUntilChanged`) the host collects, rather than re-reading settings separately.

**Verification:**

- `Grep` - `collectOnLifecycle` or `repeatOnLifecycle` used for the new observer in `MainActivity.kt`.
- `Grep -n "lifecycleScope.launch"` - no bare view-bound `collect` introduced by this step.
- `Grep` - `fun enabledRemoteSources` present in `RemoteSourceAvailabilityGate.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-14 - Verification 3/3 PASS. Extended the gate's internal snapshot to a `MutableStateFlow` and added `enabledRemoteSources(): Flow<Set<RemoteSourceId>>` (`distinctUntilChanged`); the hot `isEnabled` path still reads `snapshotFlow.value` (in-memory, no DataStore). `MainActivity` collects it via `collectOnLifecycle` and calls `tabsManager.createTabs()` on each change - reacting to runtime toggles and correcting the startup optimistic-all-enabled view, with no collector race. `.\a.ps1 fk` BUILD SUCCESSFUL.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` BUILD SUCCESSFUL (kapt resolves the new `@AndroidEntryPoint` on `FilterResourceDialog`).
- [x] `Grep -n "BuildConfig.SUPPORT_CLOUD"` - zero hits across the user-facing files in this phase.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" (incl. deletions + string removal).

---

## Handoff Notes to Next Phase

New-entry and selection surfaces respect the gate; the tab strip vanishes when no remote source is enabled. Phase 04 gates the listing/scan/background flows for resources that already exist.

---

## Rollback Plan

Revert phase commit(s). Reintroduces `BuildConfig.SUPPORT_CLOUD` guards - no data migration involved.
