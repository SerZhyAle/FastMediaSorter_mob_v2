# Phase 01 — Settings Foundation

**Strategic spec:** [`../S0003_link-receive-download.md`](../S0003_link-receive-download.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03, Phase 05
**Steps done:** 6 / 6
**Started:** 2026-04-29
**Completed:** 2026-04-29

---

## Objective

Add three persisted settings (top toggle, optional destination resource, auto-open-in-player toggle), expose them via `AppSettings` + `SettingsRepositoryImpl`, plumb through the existing backup/restore flow, ship trilingual labels, and surface the controls in `OperationsSettingsFragment` (Share/Receive section). No URL-handling code yet.

---

## Prerequisites

- [ ] Strategic spec `Status:` is `Approved` or `Tactical`.
- [ ] Working tree is clean or on a feature branch.
- [ ] Existing pattern visible: `videoSnapshotResourceId` in `AppSettings` and `SaveVideoFrameManager` (reference only, no edits required).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ 750 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupData.kt` | Modified | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupMapper.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt` | Modified | ≤ 500 |
| `app_v2/src/main/res/layout/fragment_operations_settings.xml` | Modified | — |
| `app_v2/src/main/res/values/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | — |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). `SettingsRepositoryImpl.kt` is already >700 LOC — backup it before editing.

---

## Steps

### Step 01.1 — Backup oversized files before editing

**Files:** `temp/`
**Depends on:** — start of phase

**Prompt for developer:**

> Per CLAUDE.md rule 5, copy `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` and any other touched file projected past 500 LOC into `temp/` with a `YYYYMMDD_HHmm` suffix before editing. The backup is read-only safety, not a long-term artefact.

**Verification:**

- `Glob` — at least one `temp/SettingsRepositoryImpl.kt.*.backup` (or equivalent suffix) exists after this step runs.

**Status:** `[ ]` not done

---

### Step 01.2 — Extend `AppSettings` with three new fields

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add three constructor properties to the `AppSettings` data class, grouped together with a section comment `// Link auto-download (S0003)`:
>
> - `linkAutoDownloadEnabled: Boolean = true`
> - `linkAutoDownloadResourceId: Long? = null`
> - `linkAutoDownloadOpenInPlayer: Boolean = true`
>
> Defaults match strategic §2.1/§2.3. Keep the file under its line budget; do not introduce other behavioural changes.

**Verification:**

- `Grep -n "linkAutoDownloadEnabled: Boolean = true"` in `AppSettings.kt` matches exactly once.
- `Grep -n "linkAutoDownloadResourceId: Long\?"` in `AppSettings.kt` matches exactly once.
- `Grep -n "linkAutoDownloadOpenInPlayer: Boolean = true"` in `AppSettings.kt` matches exactly once.

**Status:** `[ ]` not done

---

### Step 01.3 — Persist new fields in `SettingsRepositoryImpl`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add three preference keys to the `companion object` (mirroring the `KEY_VIDEO_SNAPSHOT_RESOURCE_ID` group):
>
> - `private val KEY_LINK_AUTO_DOWNLOAD_ENABLED = booleanPreferencesKey("link_auto_download_enabled")`
> - `private val KEY_LINK_AUTO_DOWNLOAD_RESOURCE_ID = longPreferencesKey("link_auto_download_resource_id")`
> - `private val KEY_LINK_AUTO_DOWNLOAD_OPEN_IN_PLAYER = booleanPreferencesKey("link_auto_download_open_in_player")`
>
> In the `getSettings()` mapping, populate each new field from the matching key (defaults: `true`, `null`, `true`). In `updateSettings()` (or whichever writer the file uses to persist `videoSnapshotResourceId`), write each new value; for the resource id, mirror the nullable handling already used for `KEY_VIDEO_SNAPSHOT_RESOURCE_ID` (write only when non-null, otherwise `remove`).

**Verification:**

- `Grep -n "KEY_LINK_AUTO_DOWNLOAD_ENABLED"` in `SettingsRepositoryImpl.kt` returns ≥ 3 hits (declaration + read + write).
- `Grep -n "KEY_LINK_AUTO_DOWNLOAD_RESOURCE_ID"` in `SettingsRepositoryImpl.kt` returns ≥ 3 hits.
- `Grep -n "KEY_LINK_AUTO_DOWNLOAD_OPEN_IN_PLAYER"` in `SettingsRepositoryImpl.kt` returns ≥ 3 hits.
- `Grep -n "linkAutoDownloadEnabled = preferences"` in `SettingsRepositoryImpl.kt` matches exactly once.

**Status:** `[ ]` not done

---

### Step 01.4 — Extend backup/restore data model

**Files:**
`app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupData.kt`,
`app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupMapper.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Backup serialization lives in `domain/usecase/BackupData.kt` (the data class persisted to JSON) and `domain/usecase/BackupMapper.kt` (the `AppSettings` ↔ `BackupData` projection). `GeneralSettingsBackupHelper.kt` is a UI helper, not the persistence layer.
>
> 1. In `BackupData.kt` add three sibling fields next to `videoSnapshotResourceId`, all nullable with default `null`:
>    - `val linkAutoDownloadEnabled: Boolean? = null`
>    - `val linkAutoDownloadResourceId: Long? = null`
>    - `val linkAutoDownloadOpenInPlayer: Boolean? = null`
> 2. In `BackupMapper.kt` mirror them in the `AppSettings → BackupData` projection (write current value, no transform) and in the `BackupData → AppSettings` projection (fall back to `current.<field>` for the booleans, take `null` directly for the resource id since `AppSettings.linkAutoDownloadResourceId` itself is nullable).
>
> Forward-compat: nullable backup fields ensure older bundles missing the keys keep loading without throwing.

**Verification:**

- `Grep -n "linkAutoDownloadEnabled"` in `BackupData.kt` matches at least once.
- `Grep -n "linkAutoDownloadResourceId"` in `BackupData.kt` matches at least once.
- `Grep -n "linkAutoDownloadOpenInPlayer"` in `BackupData.kt` matches at least once.
- `Grep -n "linkAutoDownloadEnabled"` in `BackupMapper.kt` returns ≥ 2 hits (project + apply).
- `Grep -n "linkAutoDownloadResourceId"` in `BackupMapper.kt` returns ≥ 2 hits.
- `Grep -n "linkAutoDownloadOpenInPlayer"` in `BackupMapper.kt` returns ≥ 2 hits.

**Status:** `[x]` done

---

### Step 01.5 — Add trilingual string resources

**Files:**
`app_v2/src/main/res/values/strings.xml`,
`app_v2/src/main/res/values-ru/strings.xml`,
`app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add the following keys with EN/RU/UK translations (Russian and Ukrainian use `ё`/`ї` correctly per author style; ellipses `..` not `...`):
>
> - `link_autodownload_section_title`
> - `link_autodownload_master_label`
> - `link_autodownload_master_summary`
> - `link_autodownload_resource_label`
> - `link_autodownload_resource_not_set`
> - `link_autodownload_open_in_player_label`
> - `link_autodownload_open_in_player_summary`
> - `link_autodownload_progress_starting`
> - `link_autodownload_progress_downloading`
> - `link_autodownload_cancel`
> - `link_autodownload_done_resource`
> - `link_autodownload_done_downloads`
> - `link_autodownload_fallback_downloads`
> - `link_autodownload_error_no_network`
> - `link_autodownload_error_resource_unavailable`
> - `link_autodownload_error_no_media`
> - `link_autodownload_error_timeout`
> - `link_autodownload_error_mime_blocked`
>
> Russian/Ukrainian author style: use `ё`/`Ё` and `ї`/`Ї`/`є`/`Є` where grammar requires. No `...` ellipses; use `..`.

**Verification:**

- `Grep -n "link_autodownload_master_label"` in each of the three locale `strings.xml` matches exactly once.
- `Grep -n "link_autodownload_resource_not_set"` in each locale matches exactly once.
- `Grep -n "link_autodownload_open_in_player_label"` in each locale matches exactly once.
- `Grep "\.\.\."` (literal three dots) in the new keys returns zero hits across the three files (use `grep -F '...'` filtered to changed lines).

**Status:** `[ ]` not done

---

### Step 01.6 — Surface controls in Share/Receive settings UI

**Files:**
`app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt`,
`app_v2/src/main/res/layout/fragment_operations_settings.xml`
**Depends on:** Steps 01.3, 01.5

**Prompt for developer:**

> Inside the existing Share/Receive section of `fragment_operations_settings.xml` (or the layout currently used by `OperationsSettingsFragment`), add a "Поведение" sub-block holding:
>
> 1. A `SwitchMaterial` bound to `linkAutoDownloadEnabled` (master toggle).
> 2. A clickable row showing the currently selected destination resource for `linkAutoDownloadResourceId`; tapping it opens the same destination picker used for `videoSnapshotResourceId` (delegate to the existing helper used by Save Frame settings — do not invent a new picker). Empty state shows `link_autodownload_resource_not_set`.
> 3. A `SwitchMaterial` bound to `linkAutoDownloadOpenInPlayer`.
>
> The two child controls (resource picker + auto-open switch) must be `isEnabled = settings.linkAutoDownloadEnabled` (visually disabled, not just dimmed). Persist changes through `viewModel.updateSettings(current.copy(..))`. No business logic beyond plumbing.
>
> If `OperationsSettingsFragment` does not currently host the Share/Receive controls, place the block in whichever fragment hosts `acceptSharedFiles` (currently `PlaybackSettingsFragment`) — keep the master toggle and its two child controls together in one logical section.

**Verification:**

- `Grep -n "linkAutoDownloadEnabled"` in the chosen fragment returns ≥ 2 hits (read + write).
- `Grep -n "linkAutoDownloadResourceId"` in the chosen fragment returns ≥ 2 hits.
- `Grep -n "linkAutoDownloadOpenInPlayer"` in the chosen fragment returns ≥ 2 hits.
- `Grep -n "android:id=\"@+id/switch_link_autodownload_enabled\""` in the matching layout xml matches exactly once.
- `Grep -n "isEnabled = .*linkAutoDownloadEnabled"` (or equivalent `binding.<view>.isEnabled = ..`) in the fragment matches at least twice (one per dependent control).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` (no public API changed, but auto-fields refresh).

---

## Handoff Notes to Next Phase

`AppSettings.linkAutoDownloadEnabled` is the canonical gate for Phase 02. `linkAutoDownloadResourceId` and `linkAutoDownloadOpenInPlayer` will be consumed by Phase 05. No other code reads or writes the new fields yet.

---

## Rollback Plan

Revert phase commit(s). Removing the new keys from DataStore via uninstall+reinstall in dev is acceptable; production rollback simply ignores the keys, which keeps existing user state intact.
