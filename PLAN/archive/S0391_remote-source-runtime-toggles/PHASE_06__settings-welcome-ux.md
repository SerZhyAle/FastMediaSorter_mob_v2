# Phase 06 - Settings & Welcome UX

**Strategic spec:** [`../S0391_remote-source-runtime-toggles.md`](../S0391_remote-source-runtime-toggles.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 04
**Blocks:** Phase 07
**Steps done:** 5 / 5
**Started:** 2026-06-14
**Completed:** 2026-06-14

---

## Objective

Build the user-facing toggles: a new collapsible "Remote sources" section in the General settings tab with THREE group toggles (SMB, (S)FTP, Cloud), the welcome networks page converted from three decorative tiles to the same three group toggles, and the disable-confirmation dialog that reassures the user that hidden folders are not deleted. Each group toggle has an explanation line and a "?" help button. Storage stays the six per-source flags (Phase 01) - each group toggle mass-writes its members.

Grouping (display = ON if any member ON; toggle writes all members):
- SMB -> `smbEnabled`
- (S)FTP -> `sftpEnabled` + `ftpEnabled`
- Cloud -> `googleDriveEnabled` + `oneDriveEnabled` + `dropboxEnabled`

---

## Prerequisites

- [ ] Phase 02 ✅ Done (gate available for visibility).
- [ ] Phase 04 ✅ Done (disable side-effects exist so the UI write triggers cancellation).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings*.xml` (EN/RU/UK) | Modified | n/a |
| `app_v2/src/main/res/layout/fragment_settings_general.xml` | Modified | n/a |
| `app_v2/src/main/res/layout-land/fragment_settings_general.xml` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsSectionsHelper.kt` | Modified | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt` | Modified | ≤ 420 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsObserversHelper.kt` | Modified | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomePagerAdapter.kt` | Modified | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt` | Modified | ≤ 760 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomeRemoteSourcesController.kt` | New | ≤ 220 |
| `app_v2/src/main/res/layout/page_welcome_networks.xml` | Modified | n/a |
| `app_v2/src/main/res/layout-land/page_welcome_networks.xml` | Modified | n/a |

> Landscape parity is MANDATORY: both `res/layout-land/fragment_settings_general.xml` (step 06.2) and `res/layout-land/page_welcome_networks.xml` (step 06.5) are edited in lockstep with their portrait variants.

---

## Steps

### Step 06.1 - Add trilingual strings

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add, in EN/RU/UK lockstep via `scripts/utils/set-android-string.ps1 -Action add`, the keys for the three group toggles, all prefixed `settings_remote_source`: section title (`settings_remote_sources_title`); per-toggle title, explanation subtitle, help-dialog title, and help-dialog message for each of SMB, (S)FTP, Cloud (e.g. `settings_remote_source_smb_title` / `_smb_subtitle` / `_smb_help_title` / `_smb_help_message`, same for `_ftp_*` labelled "(S)FTP" and `_cloud_*`); and the disable-confirmation dialog title + body (`settings_remote_source_disable_confirm_title` / `_disable_confirm_message`, body = folders are not deleted, only hidden). The welcome page reuses the three `_title` keys. All user-visible strings must pass `docs/COMMUNICATION_POLICY.md` §2 (message formula) and §6 (tone checklist). Russian text uses `ё` where correct.

**Verification:**

- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_remote_source"` exits 0.
- `Grep` - `settings_remote_source_cloud_help_message` and `settings_remote_source_disable_confirm_message` present in all three `strings*.xml`.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-14 - Verification PASS. Added 15 keys (section title + per-toggle title/subtitle/help_title/help_message ×3 + disable_confirm title/message) across EN/RU/UK via a UTF-8 `.ps1` calling `set-android-string.ps1 -Action add` (avoids bash->pwsh Cyrillic mojibake). `check_strings_localized.ps1 -KeyPrefix settings_remote_source` exit 0 (15/15 parity). RU verified via Grep: correct ё + em-dash, no mojibake. Copy explains consequence + reassures no deletion + re-enable CTA (COMMUNICATION_POLICY §2/§6).

---

### Step 06.2 - Add the Remote sources settings section

**Files:** `res/layout/fragment_settings_general.xml`, `res/layout-land/fragment_settings_general.xml`, `ui/settings/helpers/GeneralSettingsSectionsHelper.kt`
**Depends on:** Step 06.1

**Prompt for developer:**

> Add a `CollapsibleSectionHeader` ("Remote sources") + container card with THREE `SettingsToggleRow` rows - `rowSourceSmb`, `rowSourceFtp` (labelled "(S)FTP"), `rowSourceCloud` - to BOTH `res/layout/fragment_settings_general.xml` AND its landscape counterpart `res/layout-land/fragment_settings_general.xml`, keeping ids in sync. Position the new section between the File Browser section (`headerFileBrowser`/`containerFileBrowser`) and the Authorization section (`headerAuthorization`/`containerAuthorization`). Each row sets its title + explanation subtitle and `app:str_showHelp="true"` with the help title/message keys from step 06.1 (mirror an existing row that uses the "?" help, e.g. in `containerSystem`). Use `@string`/`?attr` only - no hardcoded hex. Register the section as an `ExpandableSection` with a `KEY_REMOTE_SOURCES_EXPANDED` constant in `GeneralSettingsSectionsHelper.setup()` (inserted after the File Browser entry, before Authorization) and its saved-state map; collapsed by default.

**Verification:**

- `Grep` - `rowSourceSmb`, `rowSourceFtp`, `rowSourceCloud` present in BOTH `layout/` and `layout-land/` `fragment_settings_general.xml`.
- `Grep` - `KEY_REMOTE_SOURCES_EXPANDED` present in `GeneralSettingsSectionsHelper.kt`, and the new `ExpandableSection(...)` line is ordered after `containerFileBrowser` and before `containerAuthorization`.
- `Grep` - `str_showHelp` set on the three new rows in both layout files.
- `Grep -nE '="#[0-9a-fA-F]{3,8}"'` - no new hardcoded hex in either layout file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-14 - Verification PASS. New collapsible "Remote sources" card (`headerRemoteSources`/`containerRemoteSources`) with `rowSourceSmb`/`rowSourceFtp`/`rowSourceCloud` in BOTH portrait + landscape (ids identical; landscape uses the 2-column SMB+(S)FTP / full-width Cloud pattern). Section registered in `GeneralSettingsSectionsHelper` between File Browser and Authorization with `KEY_REMOTE_SOURCES_EXPANDED` (collapsed default) + saved-state map. Rows mirror `rowEnableBackgroundSync` help attrs (`str_showHelp`/`str_helpTitle`/`str_helpMessage`). No hex. `.\a.ps1 fc` SUCCESSFUL.

---

### Step 06.3 - Wire settings group-toggle listeners, observers, and confirmation dialog

**Files:** `ui/settings/helpers/GeneralSettingsViewSetupHelper.kt`, `ui/settings/helpers/GeneralSettingsObserversHelper.kt`
**Depends on:** Step 06.2

**Prompt for developer:**

> In the setup helper, register `setOnCheckedChangeListener` on each of the three group rows. Each writes ALL its members at once via `viewModel.updateSettings(viewModel.settings.value.copy(...))`: `rowSourceSmb` -> `smbEnabled`; `rowSourceFtp` -> `sftpEnabled` and `ftpEnabled`; `rowSourceCloud` -> `googleDriveEnabled`, `oneDriveEnabled`, `dropboxEnabled`. The cloud row is visible only when `gate.isCloudGroupSupported()`. When a group is turned OFF and any of its sources has existing resources (query the resource count for the group's `ResourceType`s), show the confirmation dialog from step 06.1 before persisting; on cancel, revert the switch with `setCheckedSilently(true)`. In the observers helper, mirror each group's displayed state on the settings flow with `setCheckedSilently(...)`: SMB = `smbEnabled`, (S)FTP = `sftpEnabled || ftpEnabled`, Cloud = `googleDriveEnabled || oneDriveEnabled || dropboxEnabled`. Use `collectOnLifecycle`/`repeatOnLifecycle` for any new view-bound collection.

**Verification:**

- `Grep` - `rowSourceSmb`, `rowSourceFtp`, `rowSourceCloud` listeners present in `GeneralSettingsViewSetupHelper.kt`.
- `Grep` - the cloud-row write copies `googleDriveEnabled`, `oneDriveEnabled`, `dropboxEnabled` together.
- `Grep` - `setCheckedSilently` for the three group rows in `GeneralSettingsObserversHelper.kt`.
- `Grep -n "lifecycleScope.launch"` - no bare view-bound `collect` introduced.

**Status:** `[x]` done

**Step Log:**

- 2026-06-14 - Verification PASS. `GeneralSettingsViewSetupHelper.setupRemoteSources()`: 3 listeners mass-write members (SMB→smbEnabled; (S)FTP→sftp+ftp; Cloud→3 cloud flags); cloud row visible only when `gate.isCloudGroupSupported()`; disable-confirmation dialog shown when turning a group OFF with existing resources (counts `viewModel.resources`), revert via `setCheckedSilently(true)` on cancel. `GeneralSettingsObserversHelper` mirrors group state (ON if any member ON) inside the existing `collectOnLifecycle(settings)`. Gate injected into `GeneralSettingsFragment`. File backed up. No bare view-bound collect.

---

### Step 06.4 - Add the welcome onBind seam and remote-sources controller

**Files:** `ui/welcome/WelcomePagerAdapter.kt`, `ui/welcome/WelcomeActivity.kt`, `ui/welcome/helpers/WelcomeRemoteSourcesController.kt` (New)
**Depends on:** Step 06.1

**Prompt for developer:**

> Add `onBindNetworks: ((PageWelcomeNetworksBinding) -> Unit)?` to `WelcomePage` and invoke it in `NetworksViewHolder.bind()` (mirror the S0400 `onBindFunctionality` pattern). Create `WelcomeRemoteSourcesController` (injected into `WelcomeActivity`): one-shot `getSettings().first()` for initial state, THREE group toggles (SMB -> `smbEnabled`; (S)FTP -> `sftpEnabled`+`ftpEnabled`; Cloud -> the three cloud flags), each displayed ON if any member is enabled, each `setOnCheckedChangeListener` mass-writing its members via `persist { it.copy(...) }` on app scope. Hide the cloud toggle when `!gate.isCloudGroupSupported()`. Wire `onBindNetworks` in `WelcomeActivity` to `controller.bind(binding, this)`.

**Verification:**

- `Glob` - `ui/welcome/helpers/WelcomeRemoteSourcesController.kt` exists.
- `Grep` - `onBindNetworks` present in `WelcomePagerAdapter.kt` (field + invoke) and `WelcomeActivity.kt` (assignment).
- `Grep` - the cloud toggle write copies all three cloud flags together, and `isCloudGroupSupported` referenced in `WelcomeRemoteSourcesController.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-14 - Verification PASS. `onBindNetworks` seam added to `WelcomePage` + invoked in `NetworksViewHolder.bind()` (mirrors S0400 `onBindFunctionality`). New `WelcomeRemoteSourcesController` (@Inject SettingsRepository/gate/@ApplicationScope scope): one-shot `getSettings().first()` init, 3 group toggles mass-writing members via `persist { it.copy(...) }`, cloud toggle hidden when `!isCloudGroupSupported()`. Wired in `WelcomeActivity`. Removed the now-dead `showCloudNetworkTile`/`tileNetworkCloud` decorative field.

---

### Step 06.5 - Convert welcome network tiles to toggles (portrait + landscape)

**Files:** `res/layout/page_welcome_networks.xml`, `res/layout-land/page_welcome_networks.xml`
**Depends on:** Step 06.4

**Prompt for developer:**

> In BOTH layout variants (ids kept in sync), replace the three decorative tiles (SMB, (S)FTP, Cloud) with three group-toggle rows holding a `SwitchMaterial`/`SettingsToggleRow` - one per tile, same SMB / (S)FTP / Cloud split as settings. Set `clickable`/`focusable` true and `nextFocus*` for D-pad; provide `contentDescription`; the enabled/disabled state must be distinguishable beyond colour. Use `@string`/`?attr`/`@color` only - no hardcoded hex.

**Verification:**

- `Grep` - the SMB, (S)FTP and Cloud toggle ids present in BOTH `layout/` and `layout-land/` `page_welcome_networks.xml`.
- `Grep -nE '="#[0-9a-fA-F]{3,8}"'` - no new hardcoded hex in either file.
- `Grep` - `android:focusable="true"` on the new toggle rows in both variants.

**Status:** `[x]` done

**Step Log:**

- 2026-06-14 - Verification PASS. Both `page_welcome_networks.xml` variants: 3 decorative tiles replaced with `rowSourceSmb`/`rowSourceFtp`/`rowSourceCloud` `SettingsToggleRow`s (vertical), `clickable`/`focusable=true` + `nextFocus*` D-pad chain + `contentDescription`; state shown by the row switch (beyond colour). Landscape mirrors portrait (same ids/focus). No hex. Orphaned `welcome_network_tile_{smb,ftp,cloud}` strings removed from EN/RU/UK `strings_setup.xml` (Rule 21).

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc` BUILD SUCCESSFUL (layouts + ViewBinding + Hilt graph + string removal).
- [x] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_remote_source"` exits 0 (15/15 parity).
- [x] `Grep` for `TODO(phase-06)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (new `WelcomeRemoteSourcesController`).

---

## Handoff Notes to Next Phase

Three group toggles (SMB / (S)FTP / Cloud) are writable from settings and onboarding, both bound to the same six `AppSettings` flags via mass-write; disabling a group with existing resources confirms "not deleted, just hidden". Phase 07 finalizes docs, FEATURES, and the catalog.

---

## Rollback Plan

Revert phase commit(s). Removes the settings section, welcome toggles, and dialog - underlying flags (Phase 01) and gate (Phase 02) remain; default-all-enabled keeps behavior unchanged.
