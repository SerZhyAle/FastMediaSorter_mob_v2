# Phase 03 - Export UI (menu item + share dialog + share sheet)

**Strategic spec:** [`../S0984_share-sftp-resource-config.md`](../S0984_share-sftp-resource-config.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 5 / 5
**Started:** 2026-07-11
**Completed:** 2026-07-11

---

## Objective

Add a "Поделиться доступом.." overflow item for SFTP resources that opens a warning dialog (plaintext-password warning + "do not include password" checkbox + private-network warning) and hands the resulting `.fmscfg` to the system share sheet.

---

## Prerequisites

- [ ] Phase 02 ✅ Done (`ExportCompanionConfigUseCase`, `SftpHostReachabilityClassifier` exist).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` (+`values-ru`, `values-uk`) | Modified | - |
| `app_v2/src/main/res/menu/resource_item_actions.xml` | Modified | ≤ 65 |
| `app_v2/src/main/res/layout/dialog_share_sftp_access.xml` | New | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/ResourceAdapter.kt` | Modified | ≤ 930 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainSftpShareManager.kt` | New | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainViewModel.kt` | Modified | ≤ 830 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainEventHandler.kt` | Modified | ≤ 200 |

> `dialog_share_sftp_access.xml`: landscape variant absent - not needed (single-column dialog content, `wrap_content`, wrap it in a `ScrollView` so it stays inside safe bounds when the keyboard is up). No `res/layout-land` counterpart to edit.
> `MainActivity.kt` is near the 1500-LOC ceiling (currently 1389) - the dialog + share logic lives in `MainSftpShareManager`; `MainActivity` only forwards the adapter callback (one lambda). Do not inline the dialog in `MainActivity`.
> **Backup step (CLAUDE.md Rule 5):** `MainActivity.kt` (1389), `MainViewModel.kt` (804), `ResourceAdapter.kt` (904) each exceed 500 LOC - take a timestamped copy under `temp/S0984/` before the first edit to each.

---

## Steps

### Step 03.1 - Add trilingual export strings

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add these keys in lockstep across EN/RU/UK via `scripts/utils/set-android-string.ps1 -Action add`: `resource_menu_share_sftp_access` (menu title, e.g. EN "Share access.."), `sftp_share_export_title`, `sftp_share_export_warning` (plaintext-password warning), `sftp_share_omit_password` (checkbox label "Do not include the password"), `sftp_share_private_network_warning` ("This server is on your local network - the recipient can only connect from the same network."), `sftp_share_export_failed`, `sftp_share_export_action` (positive button "Share"). Follow `docs/COMMUNICATION_POLICY.md` §2 (message formula per type) and §6 (tone checklist); use `..` not `...`, plain hyphen, Ё where grammatical in RU. Run `scripts/check_strings_localized.ps1 -KeyPrefix "sftp_share"` and the menu key afterwards.

**Verification:**

- `Grep` - each key present in all three `strings.xml`.
- `check_strings_localized.ps1 -KeyPrefix "sftp_share"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

---

### Step 03.2 - Add SFTP-only overflow item + adapter callback

**Files:** `res/menu/resource_item_actions.xml`, `ui/main/ResourceAdapter.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> In `resource_item_actions.xml` add `<item android:id="@+id/action_share_sftp_access" android:title="@string/resource_menu_share_sftp_access" android:icon="@drawable/ic_share" />` after `action_export_resource`. In `ResourceAdapter`, add constructor callback `onShareSftpAccessClick: (MediaResource) -> Unit`. In BOTH popup blocks (grid `GridViewHolder` ~L446-477 and list `ResourceViewHolder` ~L804-864) set `popup.menu.findItem(R.id.action_share_sftp_access)?.isVisible = resource.type == ResourceType.SFTP` and add the `R.id.action_share_sftp_access -> { onShareSftpAccessClick(resource); true }` branch. Do not touch the `layoutInlineActions` path (no export/share entry there today).

**Verification:**

- `Grep` - `action_share_sftp_access` present in the menu XML.
- `Grep` - `onShareSftpAccessClick` matches at least 3 times in `ResourceAdapter.kt` (ctor param + 2 click branches).
- `Grep` - `R.id.action_share_sftp_access)?.isVisible = resource.type == ResourceType.SFTP` appears twice.

**Status:** `[x] done`

---

### Step 03.3 - Share dialog layout + `MainSftpShareManager`

**Files:** `res/layout/dialog_share_sftp_access.xml` (New), `ui/main/helpers/MainSftpShareManager.kt` (New)
**Depends on:** Step 03.1

**Prompt for developer:**

> Create `dialog_share_sftp_access.xml`: a `ScrollView` > vertical `LinearLayout` with a warning `TextView` (`@string/sftp_share_export_warning`), a `com.google.android.material.checkbox.MaterialCheckBox` id `checkboxOmitPassword` (`@string/sftp_share_omit_password`), and a `TextView` id `textPrivateNetworkWarning` (`@string/sftp_share_private_network_warning`, `android:visibility="gone"`). No hardcoded hex colors - use `?attr/colorOnSurface` / theme text appearances. Create `class MainSftpShareManager @Inject constructor(...)` (or a plain helper taking the Activity) in `ui/main/helpers` exposing `fun show(activity, resource, onConfirm: (includePassword: Boolean) -> Unit)`: inflate the layout with a themed context, toggle `textPrivateNetworkWarning` visible when `SftpHostReachabilityClassifier.classify(host)` (host from `SftpPathUtils.parseSftpPath(resource.path)`) is `PRIVATE_LAN`, build a `MaterialAlertDialogBuilder` with title `sftp_share_export_title`, the custom view, positive `sftp_share_export_action` -> `onConfirm(!checkboxOmitPassword.isChecked)`, negative `android.R.string.cancel`. Standard builder buttons (matches existing `onExportClick`; the named DialogConfirm/Cancel styles apply to custom-layout button pairs, not builder buttons).

**Verification:**

- `Glob` - `dialog_share_sftp_access.xml` and `MainSftpShareManager.kt` exist.
- `Grep` - `checkboxOmitPassword` and `textPrivateNetworkWarning` in the layout.
- `Grep` - `SftpHostReachabilityClassifier.classify` and `onConfirm(!` in `MainSftpShareManager.kt`.
- `Grep` - no `="#` hex color in `dialog_share_sftp_access.xml`.

**Status:** `[x] done`

---

### Step 03.4 - ViewModel export method + share event

**Files:** `ui/main/MainViewModel.kt`, `ui/main/helpers/MainEventHandler.kt`
**Depends on:** Phase 02, Step 03.3

**Prompt for developer:**

> In `MainViewModel` inject `ExportCompanionConfigUseCase` and add `fun shareSftpResourceConfig(resource: MediaResource, includePassword: Boolean)`: `viewModelScope.launch { exportCompanionConfigUseCase(resource, includePassword).fold(onSuccess = { file -> sendEvent(MainEvent.ShareCompanionConfigFile(file.absolutePath)) }, onFailure = { e -> Timber.e(e, "SFTP config export failed"); sendEvent(MainEvent.ShowResourceMessage(R.string.sftp_share_export_failed)) }) }`. Add `data class ShareCompanionConfigFile(val filePath: String) : MainEvent()` to the sealed class. In the `when (event)` dispatch in `MainEventHandler` (next to the `is MainEvent.ShareResourceFile ->` branch), add `is MainEvent.ShareCompanionConfigFile -> shareCompanionConfigFile(event.filePath)` and a private `shareCompanionConfigFile(filePath)` mirroring `shareResourceFile` but with `type = "application/vnd.fms.companion-config+json"` (vendor MIME - reliable app-to-app SEND) and chooser title `sftp_share_export_title`.

**Verification:**

- `Grep` - `fun shareSftpResourceConfig(` and `MainEvent.ShareCompanionConfigFile(` in `MainViewModel.kt`.
- `Grep` - `ShareCompanionConfigFile -> shareCompanionConfigFile` and `application/vnd.fms.companion-config+json` in `MainEventHandler.kt`.

**Status:** `[x] done`

---

### Step 03.5 - Wire `MainActivity` callback to the manager

**Files:** `ui/main/MainActivity.kt`
**Depends on:** Step 03.2, Step 03.3, Step 03.4

**Prompt for developer:**

> In the `ResourceAdapter(...)` construction in `MainActivity`, add `onShareSftpAccessClick = { resource -> mainSftpShareManager.show(this, resource) { includePassword -> viewModel.shareSftpResourceConfig(resource, includePassword) } }`. Obtain `mainSftpShareManager` the same way sibling helpers are held (field/inject). Keep the lambda a one-liner - no dialog logic in `MainActivity`.

**Verification:**

- `Grep` - `onShareSftpAccessClick =` present in `MainActivity.kt`.
- `Grep` - `mainSftpShareManager` referenced in `MainActivity.kt`.
- Build predicate covered by Phase Done Criteria.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (standard debug).
- [ ] `check_strings_localized.ps1 -KeyPrefix "sftp_share"` exits 0.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] `MainActivity.kt` line count ≤ 1500 (`(Get-Content ... ).Count`).
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (`MainSftpShareManager`).

---

## Handoff Notes to Next Phase

Export path is complete: SFTP resource -> overflow "Share access.." -> dialog -> `.fmscfg` in `cacheDir/share_temp` -> share sheet with vendor MIME. Phase 04 builds the receiving trampoline that consumes such a file.

---

## Rollback Plan

Revert the phase commit(s). New menu item, dialog, and manager are additive; the ViewModel/event/handler additions are new branches - reverting removes the surface with no migration.
