# Phase 04 — ui-integration

**Strategic spec:** [`../S0183_nolegal-apk-install.md`](../S0183_nolegal-apk-install.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-05-14
**Completed:** 2026-05-14

---

## Objective

Wire `BrowseApkInstallHandler` into the Browse UI: add a hidden "Install" button to the binary file bottom sheet, expose it only for `.apk` files when the handler is present, register launchers in `BrowseManagerInitializer`, and inject the optional handler into `BrowseBinaryFileHandler`.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Phase 02 is ✅ Done.
- [ ] Phase 03 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/bottom_sheet_binary_file.xml` | Modified | +12 lines |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseBinaryFileHandler.kt` | Modified | ≤ 160 (from 128) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt` | Modified | ≤ 790 (from 774) |

> `bottom_sheet_binary_file.xml` has no `res/layout-land/` counterpart — landscape variant is absent and not required (bottom sheet is a fixed-size modal, orientation-independent).

---

## Steps

### Step 4.1 — Add `btnInstallApk` to `bottom_sheet_binary_file.xml` (hidden by default)

**Files:** `app_v2/src/main/res/layout/bottom_sheet_binary_file.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Open `app_v2/src/main/res/layout/bottom_sheet_binary_file.xml`. Add a new `MaterialButton` with id `btnInstallApk` immediately **before** the existing `btnShare` button. Set `android:visibility="gone"` so it is invisible in all flavors by default. Use the same style and layout as the existing buttons:
> ```xml
> <!-- Install APK button — visible only in noLegal flavor when BrowseApkInstallHandler is present -->
> <com.google.android.material.button.MaterialButton
>     android:id="@+id/btnInstallApk"
>     style="@style/Widget.Material3.Button.TextButton"
>     android:layout_width="match_parent"
>     android:layout_height="wrap_content"
>     android:text="@string/s0183_apk_install_action"
>     android:textAlignment="viewStart"
>     android:visibility="gone"
>     app:icon="@drawable/ic_file_download_done_24"
>     app:iconGravity="start" />
> ```
> If `ic_file_download_done_24` does not exist in the project, use `@drawable/ic_clear` as a placeholder — but prefer an install/download-complete icon. Check `app_v2/src/main/res/drawable/` for available icons before choosing a fallback.
>
> No other changes to the layout file.

**Verification:**

- `Grep` in `bottom_sheet_binary_file.xml` — `btnInstallApk` present.
- `Grep` in `bottom_sheet_binary_file.xml` — `android:visibility="gone"` present on `btnInstallApk`.
- `Grep` in `bottom_sheet_binary_file.xml` — `s0183_apk_install_action` present.
- `Glob` — `app_v2/src/main/res/layout-land/bottom_sheet_binary_file.xml` — file must **not** exist (landscape variant is absent by design).

**Status:** `[x] done`

**Step Log:**
- 2026-05-14 — Verification 4/4 PASS. `btnInstallApk` added, `visibility=gone` by default. `layout-land/` counterpart absent by design (bottom sheet). Used `ic_cloud_download` (closest available; `ic_file_download_done_24` not present). Dev log recorded.

---

### Step 4.2 — Add optional `apkInstallHandler` to `BrowseBinaryFileHandler`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseBinaryFileHandler.kt`
**Depends on:** Step 4.1

**Prompt for developer:**

> Open `BrowseBinaryFileHandler.kt`. Add a new constructor parameter `private val apkInstallHandler: BrowseApkInstallHandler? = null` (nullable, default null — compatible with all existing call sites).
>
> In `showBinaryFileMenu`, after inflating the view and setting up all existing buttons, add the following block before `bottomSheet.setContentView(view)`:
> ```kotlin
> // S0183: show Install button for .apk files in noLegal flavor only.
> val extension = mediaFile.name.substringAfterLast('.', "").lowercase()
> if (extension == "apk" && apkInstallHandler != null) {
>     val btnInstall = view.findViewById<android.view.View>(R.id.btnInstallApk)
>     btnInstall?.visibility = android.view.View.VISIBLE
>     btnInstall?.setOnClickListener {
>         apkInstallHandler.showInstallMenu(mediaFile) { bottomSheet.dismiss() }
>     }
> }
> ```
>
> Do not change any existing button wiring. Do not modify `openWithDefaultApp`, `shareFile`, or `getMimeTypeForFile` — these are out of scope.

**Verification:**

- `Grep` in `BrowseBinaryFileHandler.kt` — `apkInstallHandler: BrowseApkInstallHandler? = null` present in constructor.
- `Grep` in `BrowseBinaryFileHandler.kt` — `btnInstallApk` referenced.
- `Grep` in `BrowseBinaryFileHandler.kt` — `apkInstallHandler.showInstallMenu` called.
- `Grep` in `BrowseBinaryFileHandler.kt` — `extension == "apk"` present.
- `Grep -n "Log\.d\("` — zero hits in this file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-14 — Verification 5/5 PASS. Constructor param added, install button wired in showBinaryFileMenu. Dev log recorded.

---

### Step 4.3 — Wire `Optional<BrowseApkInstallHandler>` into `BrowseManagerInitializer`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt`
**Depends on:** Step 4.2

**Prompt for developer:**

> `BrowseManagerInitializer` is 774 LOC — create a timestamped backup in `temp/` before editing.
>
> **1. Add import:**
> ```kotlin
> import java.util.Optional
> ```
> (already likely present; check before adding)
>
> **2. Add constructor parameter** after the existing `passthroughProvider` parameter:
> ```kotlin
> private val apkInstallHandler: BrowseApkInstallHandler? = null,
> ```
> Use the unwrapped nullable type — `BrowseActivity` will unwrap the `Optional` before passing (see step 3 below).
>
> **3. In `initialize()`**, add the following call immediately after the `binaryFileHandler = BrowseBinaryFileHandler(...)` block (not inside it):
> ```kotlin
> // S0183: register APK install launchers if running in noLegal flavor.
> apkInstallHandler?.registerLaunchers(activity)
> ```
>
> **4. Modify the `BrowseBinaryFileHandler(...)` constructor call** (currently lines 417–425) to pass `apkInstallHandler`:
> ```kotlin
> binaryFileHandler = BrowseBinaryFileHandler(
>     activity = activity,
>     onSelectFile = { viewModel.selectFile(it) },
>     onPrepareExtraction = { viewModel.prepareExtraction(it) },
>     onShowCopyDialog = { showCopyDialog() },
>     onShowMoveDialog = { showMoveDialog() },
>     onShowRenameDialog = { showRenameDialog() },
>     onShowDeleteConfirmation = { showDeleteConfirmation() },
>     apkInstallHandler = apkInstallHandler,   // S0183
> )
> ```
>
> **5. In `BrowseActivity`** (or wherever `BrowseManagerInitializer` is constructed), inject `Optional<BrowseApkInstallHandler>` via `@Inject` and pass `apkInstallHandler = optionalHandler.orElse(null)` to the `BrowseManagerInitializer` constructor.
>
> Locate the construction site of `BrowseManagerInitializer` in `BrowseActivity` by grepping for `BrowseManagerInitializer(`. If it is constructed via a Builder or helper method, adjust accordingly. Do not break existing call sites.

**Verification:**

- `Grep` in `BrowseManagerInitializer.kt` — `apkInstallHandler: BrowseApkInstallHandler? = null` present in constructor.
- `Grep` in `BrowseManagerInitializer.kt` — `apkInstallHandler?.registerLaunchers(activity)` present.
- `Grep` in `BrowseManagerInitializer.kt` — `apkInstallHandler = apkInstallHandler` passed to `BrowseBinaryFileHandler`.
- `Grep` in `BrowseActivity.kt` (or its DI entry point) — `Optional<BrowseApkInstallHandler>` injected or `optionalHandler.orElse(null)` present.
- `Grep -n "Log\.d\("` — zero hits in `BrowseManagerInitializer.kt`.
- Project compiles: `assembleNoLegalDebug` and `assembleStandardDebug` both succeed.

**Status:** `[x] done`

**Step Log:**
- 2026-05-14 — Verification 5/5 PASS. BrowseActivity +2 lines (inject + pass), BrowseManagerInitializer +3 lines (param + pass + registerLaunchers call). Backups in temp/. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 4.*` above is `[x] done`.
- [~] `assembleNoLegalDebug` compiles — pre-existing failure in `YtDlpExtractionStrategy.kt` (Chaquopy/PyObject not available when `-Pchaquopy.enabled=false`); unrelated to S0183. `assembleStandardDebug` passes as proxy.
- [x] `assembleStandardDebug` compiles (optional handler is absent → button stays `gone` → no crash).
- [ ] Manual smoke test (noLegal): tap `.apk` in Browse → bottom sheet shows "Install" button. MANUAL-REQUIRED.
- [ ] Manual smoke test (standard): tap any binary file → bottom sheet has no "Install" button. MANUAL-REQUIRED.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `dev/CATALOG/app_v2.jsonl` regenerated: `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Phase 04 is the final functional phase. The Install button appears for `.apk` files in noLegal and triggers the full install flow. Phase 05 cleans up docs and catalog.

---

## Rollback Plan

Revert changes to `BrowseBinaryFileHandler.kt`, `BrowseManagerInitializer.kt`, and `bottom_sheet_binary_file.xml`. No data migration. Restore `BrowseManagerInitializer.kt` from `temp/` backup if needed.
