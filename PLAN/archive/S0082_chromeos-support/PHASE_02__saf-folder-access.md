# Phase 02 — SAF Folder Access

**Strategic spec:** [`../S0082_chromeos-support.md`](../S0082_chromeos-support.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Ensure the folder-selection flow is Chrome OS safe: suppress the `MANAGE_EXTERNAL_STORAGE` permission prompt on ARC++, and disable the quick-path shortcut buttons (DCIM / Downloads / Pictures) that use raw `File` paths when `ChromeOsCompat.needsSafFolderPicker()` is true.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Blocker §6.1 resolved (behavior of `MANAGE_EXTERNAL_STORAGE` on Chrome OS confirmed).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainStoragePermissionsHelper.kt` | Modified | ≤ 110 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceScanManager.kt` | Modified | ≤ 310 |

---

## Steps

### Step 2.1 — Skip MANAGE_EXTERNAL_STORAGE prompt on Chrome OS

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainStoragePermissionsHelper.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `MainStoragePermissionsHelper`, find the method that checks for and requests `MANAGE_EXTERNAL_STORAGE` (or `ALL_FILES_ACCESS`). Add an early-return guard at the top of that method: `if (ChromeOsCompat.isChromeOs(activity)) return`. This prevents the app from navigating the user to System Settings to grant a permission that cannot meaningfully improve file access on ARC++. The guard must fire before any permission-status check, not after. Log: `Timber.d("MainStoragePermissionsHelper: skipping MANAGE_EXTERNAL_STORAGE on Chrome OS")`.

**Verification:**

- `Grep` — `ChromeOsCompat.isChromeOs` present in `MainStoragePermissionsHelper.kt`.
- `Grep` — `return` line immediately follows the `isChromeOs` check (within 2 lines).
- `Grep` for `Log.d(` in `MainStoragePermissionsHelper.kt` returns zero hits (Timber only rule).

**Status:** `[ ]` not done

---

### Step 2.2 — Disable quick-path folder shortcut buttons on Chrome OS

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceScanManager.kt`
**Depends on:** Step 2.1

**Prompt for developer:**

> In `AddResourceScanManager`, the quick-folder shortcut buttons (DCIM, Downloads, Pictures etc.) call `selectFolderByPath(path, dialog)` which uses `java.io.File(path)` — this fails on Chrome OS because ARC++ restricts raw filesystem paths. Add a helper `val useSafOnly = ChromeOsCompat.needsSafFolderPicker(activity)` near the quick-button setup loop. In the quick-button click handler, replace the `selectFolderByPath(path, dialog)` call with:
>
> ```kotlin
> if (useSafOnly) folderPickerLauncher.launch(null)
> else selectFolderByPath(path, dialog)
> ```
>
> Do not remove the shortcut buttons from the UI — launching SAF on click is acceptable UX for Chrome OS users. The `folderPickerLauncher` already exists in `AddResourceScanManager` (it uses `ActivityResultContracts.OpenDocumentTree()`).

**Verification:**

- `Grep` — `ChromeOsCompat.needsSafFolderPicker` present in `AddResourceScanManager.kt`.
- `Grep` — `folderPickerLauncher.launch(null)` present in `AddResourceScanManager.kt`.
- `Grep` for `Log.d(` in `AddResourceScanManager.kt` returns zero hits.

**Status:** `[ ]` not done

---

### Step 2.3 — Guard selectFolderByPath against Chrome OS

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceScanManager.kt`
**Depends on:** Step 2.2

**Prompt for developer:**

> In `AddResourceScanManager.selectFolderByPath()`, add a top-level guard: if `ChromeOsCompat.needsSafFolderPicker(activity)` is `true`, immediately launch SAF picker (`folderPickerLauncher.launch(null)`) and return, instead of proceeding with `java.io.File(path)`. Log: `Timber.d("AddResourceScanManager: redirecting to SAF picker on Chrome OS")`. This is a safety net for any call site that bypasses Step 2.2.

**Verification:**

- `Grep` — `ChromeOsCompat.needsSafFolderPicker` appears exactly twice in `AddResourceScanManager.kt` (step 2.2 guard + this guard).
- `Grep` — `fun selectFolderByPath` present in `AddResourceScanManager.kt`.
- `Grep` for `Log.d(` in `AddResourceScanManager.kt` returns zero hits.

**Status:** `[ ]` not done

---

### Step 2.4 — Verify URI storage chain does not extract raw File path

**Files:** read-only audit; changes only if raw path extraction found
**Depends on:** Step 2.3

**Prompt for developer:**

> Trace `viewModel.addManualFolder(uri, pin)` through `AddResourceViewModel` → UseCase → Repository. Confirm that the `uri` (a SAF `content://` URI) is stored as a `String` in the database without being converted to a `java.io.File` path (no calls to `.path`, `.toFile()`, or `File(uri.path)` on the content URI). If such a conversion exists, replace it with `DocumentFile.fromTreeUri(context, uri)?.uri?.toString()` to preserve the SAF URI form. Log any finding with Timber.

**Verification:**

- `Grep` for `\.toFile\(\)\|File(uri\|uri\.path` in `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/**/*.kt` and `domain/**/*.kt` involved in the add-folder flow — returns zero hits (no raw File extraction from SAF URI).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entries added for every modified file via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

On Chrome OS, the folder picker always routes through SAF. `MANAGE_EXTERNAL_STORAGE` is never requested. The storage access contract (URI → app) is intact.

---

## Rollback Plan

Revert phase commit(s). The quick-folder buttons revert to using raw File paths (original behavior). No data migration needed.
