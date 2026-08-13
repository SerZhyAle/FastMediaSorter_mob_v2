# Phase 01 - Shared import plumbing + SFTP-header relocation (S0992)

**Strategic spec:** [`../S0991_add-resource-import-file-barcode.md`](../S0991_add-resource-import-file-barcode.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** -
**Steps done:** 4 / 4

---

## Objective

Establish the single internal action source for both import entry points, introduce the shared EN/RU/UK strings, remove the now-dead companion-specific strings, and relocate + relabel the two existing SFTP-form import buttons into the SFTP header (S0992). After this phase the SFTP section uses the shared wiring and new labels; the type-screen entries (Phase 02) reuse the same methods.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified (2 new keys, 2 removed) | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified (2 new keys, 2 removed) | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified (2 new keys, 2 removed) | - |
| `app_v2/src/main/res/layout/activity_add_resource.xml` | Modified (move SFTP import buttons to header) | 651 -> ~651 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt` | Modified (extract shared launch methods, camera gate helper) | 498 -> ~510 |

> No `res/layout-land/activity_add_resource.xml` counterpart exists (single portrait layout) - Rule 11 satisfied without a land edit.

---

## Steps

### Step 01.1 - Introduce shared strings, remove dead companion strings

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`

**Prompt for developer:**

> Add two shared string keys across EN/RU/UK:
> - `resource_import_from_file` = EN "Import from file" / RU "Импорт из файла" / UK "Імпорт із файлу"
> - `resource_import_from_barcode` = EN "Import by barcode" / RU "Импорт по штрих-коду" / UK "Імпорт за штрих-кодом"
>
> Then remove `companion_import_button` and `companion_qr_scan_button` from all three locales - they are referenced only by the two SFTP buttons being relabelled in Step 01.3 (verified: no other usage). Use `scripts/utils/set-android-string.ps1` (`-Action add -En -Ru -Uk` for the new keys; `-Action remove` for the dead keys) run from a native PowerShell context (Cyrillic through the Bash->pwsh boundary is unsafe). Keep `companion_import_success` / `companion_import_failed` / `companion_import_version_error` / `companion_import_invalid_error` - those stay in use by the coordinator.

**Verification:**

- `Grep` - `resource_import_from_file` and `resource_import_from_barcode` present in all three `strings.xml`.
- `Grep` - zero matches for `companion_import_button` / `companion_qr_scan_button` anywhere under `app_v2/src`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "resource_import"` exits 0.

**Status:** `[ ] not started`

**Step Log:**

- (pending)

---

### Step 01.2 - Extract shared launch methods + camera-gate helper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt`

**Prompt for developer:**

> Add three private members to `AddResourceActivity` (the single action source, strategic §5.1):
> - `launchCompanionFileImport()` - logs `UserActionLogger.logButtonClick("ImportCompanionConfig", "AddResource")` then `companionConfigPickerLauncher.launch(arrayOf("*/*"))`.
> - `launchCompanionQrScan()` - logs `UserActionLogger.logButtonClick("ScanCompanionQr", "AddResource")` then `companionQrScanLauncher.launch(CompanionQrScanActivity.createIntent(this))`.
> - `isBarcodeImportAvailable(): Boolean = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)`.
>
> These centralise the existing inline SFTP wiring so both placements reuse it. Do not change the launcher declarations or the ViewModel calls. Keep log/method lines <=120 chars (detekt-clean-first, Rule 19).

**Verification:**

- `Grep` - `launchCompanionFileImport`, `launchCompanionQrScan`, `isBarcodeImportAvailable` defined once each.
- Project compiles - `.\a.ps1 fk` (standard Kotlin compile) BUILD SUCCESSFUL.

**Status:** `[ ] not started`

**Step Log:**

- (pending)

---

### Step 01.3 - Relocate + relabel SFTP import buttons to the header (S0992)

**Files:** `app_v2/src/main/res/layout/activity_add_resource.xml`, `AddResourceActivity.kt`

**Prompt for developer:**

> In `layoutSftpFolder` move `btnSftpImportCompanion` and `btnSftpScanCompanionQr` from the bottom of the SFTP `LinearLayout` (currently after `btnSftpAddResource`) to a horizontal row at the **top** of that `LinearLayout`, before the "Protocol Selection" `TextView`. Keep the button ids. Repoint text: `btnSftpImportCompanion` -> `@string/resource_import_from_file`, `btnSftpScanCompanionQr` -> `@string/resource_import_from_barcode`. Keep icons (`ic_import`, `ic_qr_scan`). Lay them out as a two-up row (`0dp` width + `layout_weight="1"` each) so they read as a compact header.
>
> In `AddResourceActivity.setupViews()`, replace the two inline SFTP click bodies with `launchCompanionFileImport()` / `launchCompanionQrScan()`, and gate `btnSftpScanCompanionQr.isVisible` via `isBarcodeImportAvailable()` (unchanged behaviour, shared helper).

**Verification:**

- `Grep` - `btnSftpImportCompanion` and `btnSftpScanCompanionQr` appear before `rgProtocol` in the layout (header position), not after `btnSftpAddResource`.
- `Grep` - the two SFTP click handlers call `launchCompanionFileImport()` / `launchCompanionQrScan()`; `btnSftpScanCompanionQr.isVisible` uses `isBarcodeImportAvailable()`.
- Project compiles - `.\a.ps1 fc` (code + resources) BUILD SUCCESSFUL.

**Status:** `[ ] not started`

**Step Log:**

- (pending)

---

### Step 01.4 - Phase build gate

**Files:** (no new edits) - validation only

**Prompt for developer:**

> Confirm the standard debug variant assembles with the string + layout + activity changes.

**Verification:**

- `.\a.ps1 dq` (quiet standard debug) BUILD SUCCESSFUL.

**Status:** `[ ] not started`

**Step Log:**

- (pending)

---

## Phase Done Criteria

- [ ] Every `Step 01.*` is `[x] done`.
- [ ] New shared strings present EN/RU/UK; dead companion strings removed.
- [ ] SFTP import buttons relocated to the header and relabelled; both wired to the shared launch methods.
- [ ] Standard debug builds green.

---

## Handoff Notes to Next Phase

Phase 02 reuses `launchCompanionFileImport()` / `launchCompanionQrScan()` / `isBarcodeImportAvailable()` and the new strings for the type-screen entries - no new plumbing needed there.

---

## Rollback Plan

Revert the three files from version control. No data migration, no schema change.
