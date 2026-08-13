# Phase 01 — foundation-di

**Strategic spec:** [`../S0183_nolegal-apk-install.md`](../S0183_nolegal-apk-install.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 4 / 4
**Started:** 2026-05-13
**Completed:** 2026-05-13

---

## Objective

Introduce `BrowseApkInstallHandler` abstract class (main sourceSet), its Hilt optional binding module (main), the concrete `BrowseApkInstallHandlerImpl` (noLegal sourceSet) with full install flow, and the noLegal Hilt binding module. Project compiles on all flavors; no UI or manifest changes yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] `src/noLegal/java/com/sza/fastmediasorter/` directory exists (already present per S0174/S0177).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseApkInstallHandler.kt` | **New** | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/BrowseApkInstallOptionalModule.kt` | **New** | ≤ 15 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/BrowseApkInstallHandlerImpl.kt` | **New** | ≤ 200 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/di/BrowseApkInstallModule.kt` | **New** | ≤ 25 |

---

## Steps

### Step 1.1 — Create `BrowseApkInstallHandler` abstract class (main sourceSet)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseApkInstallHandler.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create abstract class `BrowseApkInstallHandler` in package `com.sza.fastmediasorter.ui.browse.managers` in the **main** sourceSet (`app_v2/src/main/java/`). Declare two abstract methods:
> - `abstract fun registerLaunchers(activity: androidx.activity.ComponentActivity)` — called once from `BrowseManagerInitializer.initialize()` to register `ActivityResultLauncher` instances against the activity's lifecycle.
> - `abstract fun showInstallMenu(file: com.sza.fastmediasorter.domain.model.MediaFile, onDismiss: () -> Unit)` — triggers the install flow for a given APK file; `onDismiss` must be called after the bottom sheet is dismissed regardless of outcome.
>
> No imports from `android.content.pm.PackageInstaller`, no `@Inject`, no Hilt annotations — this is a plain abstract class visible to main sourceSet.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseApkInstallHandler.kt` exists.
- `Grep` — `abstract class BrowseApkInstallHandler` matches exactly once.
- `Grep` — `abstract fun registerLaunchers` present.
- `Grep` — `abstract fun showInstallMenu` present.
- `Grep -n "Log\.d\("` — zero hits in this file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-13 — Verification 5/5 PASS. File: `ui/browse/managers/BrowseApkInstallHandler.kt` (+29 LOC). Dev log recorded.

---

### Step 1.2 — Create `BrowseApkInstallOptionalModule` (main sourceSet, `@BindsOptionalOf`)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/di/BrowseApkInstallOptionalModule.kt`
**Depends on:** Step 1.1

**Prompt for developer:**

> Create a Hilt module `BrowseApkInstallOptionalModule` in package `com.sza.fastmediasorter.di` in the **main** sourceSet. Mirror the structure of `BrowsePassthroughOptionalModule`:
> ```kotlin
> @Module
> @InstallIn(SingletonComponent::class)
> interface BrowseApkInstallOptionalModule {
>     @BindsOptionalOf
>     fun optionalApkInstallHandler(): BrowseApkInstallHandler
> }
> ```
> Import `BrowseApkInstallHandler` from the `ui.browse.managers` package.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/di/BrowseApkInstallOptionalModule.kt` exists.
- `Grep` — `interface BrowseApkInstallOptionalModule` matches exactly once.
- `Grep` — `@BindsOptionalOf` present in this file.
- `Grep` — `fun optionalApkInstallHandler(): BrowseApkInstallHandler` present.

**Status:** `[x] done`

**Step Log:**
- 2026-05-13 — Verification 4/4 PASS. File: `di/BrowseApkInstallOptionalModule.kt` (+14 LOC). Dev log recorded.

---

### Step 1.3 — Create `BrowseApkInstallHandlerImpl` (noLegal sourceSet, full implementation)

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/BrowseApkInstallHandlerImpl.kt`
**Depends on:** Step 1.1

**Prompt for developer:**

> Create `BrowseApkInstallHandlerImpl` in package `com.sza.fastmediasorter.ui.browse.managers` in the **noLegal** sourceSet (`app_v2/src/noLegal/java/`). Annotate with `@Singleton`. Constructor: `@Inject constructor(@ApplicationContext private val context: Context)`. Extend `BrowseApkInstallHandler`.
>
> **Fields:** two nullable `ActivityResultLauncher<Intent>` vars — `installLauncher` and `settingsLauncher`. Both start as `null`.
>
> **`registerLaunchers(activity: ComponentActivity):`** Register two launchers via `activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult())`:
> - `settingsLauncher` — callback: if `PackageManager.canRequestPackageInstalls()` returns true after returning from Settings, call `pendingFile?.let { triggerInstall(it) }` (see below); clear `pendingFile` after attempt.
> - `installLauncher` — callback: show a Toast based on `resultCode`:
>   - `RESULT_OK` → `R.string.s0183_apk_install_success`
>   - `RESULT_CANCELED` → `R.string.s0183_apk_install_cancelled`
>   - else → `R.string.s0183_apk_install_failed`
>
> **Private field:** `private var pendingFile: MediaFile? = null` — stores the file waiting for permission grant.
>
> **`showInstallMenu(file: MediaFile, onDismiss: () -> Unit):`** Call `onDismiss()` immediately (bottom sheet lifecycle). Then check `context.packageManager.canRequestPackageInstalls()`:
> - `true` → call `triggerInstall(file)` directly.
> - `false` → store `file` in `pendingFile`, show an `AlertDialog` with title `R.string.s0183_apk_install_rationale_title`, message `R.string.s0183_apk_install_rationale_message`, negative button `R.string.s0183_apk_install_rationale_btn_cancel` (dismiss only), positive button `R.string.s0183_apk_install_rationale_btn_settings` → launch `settingsLauncher` with `Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))`.
>
> **Private `triggerInstall(file: MediaFile):`** Build `content://` URI via `androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", java.io.File(file.path))`. Build `Intent(Intent.ACTION_INSTALL_PACKAGE)` with `data = uri`, `type = "application/vnd.android.package-archive"`, flags `FLAG_GRANT_READ_URI_PERMISSION`, extra `EXTRA_RETURN_RESULT = true`. Launch via `installLauncher?.launch(intent)`. Wrap in `try/catch`; on exception log with `Timber.e` and show `R.string.s0183_apk_install_failed` Toast.
>
> **Note on AlertDialog context:** the `AlertDialog` and any Toast inside callbacks must use `activity` context, not `@ApplicationContext`. Obtain it from a `WeakReference<Activity>` stored during `registerLaunchers`, or pass it as a parameter to the relevant methods. `@ApplicationContext` is used only for `PackageManager` and `FileProvider` — never for UI.
>
> **No silent install path:** `PackageInstaller.SessionParams` must not appear anywhere in this file. Do not call `session.commit()` or `SessionParams.setRequireUserAction`.
>
> Log all meaningful steps with `Timber.d` / `Timber.e`. No `Log.d`.

**Verification:**

- `Glob` — `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/BrowseApkInstallHandlerImpl.kt` exists.
- `Grep` — `class BrowseApkInstallHandlerImpl` present.
- `Grep` — `@Singleton` present.
- `Grep` — `@Inject constructor` present.
- `Grep` — `ACTION_INSTALL_PACKAGE` present.
- `Grep` — `EXTRA_RETURN_RESULT` present.
- `Grep` — `FileProvider.getUriForFile` present.
- `Grep` — `PackageInstaller` — zero hits (session API forbidden).
- `Grep` — `USER_ACTION_NOT_REQUIRED` — zero hits.
- `Grep -n "Log\.d\("` — zero hits in this file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-13 — Verification 10/10 PASS. File: `src/noLegal/.../BrowseApkInstallHandlerImpl.kt` (+144 LOC). Comment adjusted to remove PackageInstaller mention (predicate compliance). Dev log recorded.

---

### Step 1.4 — Create `BrowseApkInstallModule` (noLegal sourceSet, Hilt `@Binds`)

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/di/BrowseApkInstallModule.kt`
**Depends on:** Step 1.2, Step 1.3

**Prompt for developer:**

> Create Hilt module `BrowseApkInstallModule` in package `com.sza.fastmediasorter.di` in the **noLegal** sourceSet. Use `@Binds` to provide `BrowseApkInstallHandlerImpl` as `BrowseApkInstallHandler`:
> ```kotlin
> @Module
> @InstallIn(SingletonComponent::class)
> abstract class BrowseApkInstallModule {
>     @Binds
>     abstract fun bindApkInstallHandler(impl: BrowseApkInstallHandlerImpl): BrowseApkInstallHandler
> }
> ```

**Verification:**

- `Glob` — `app_v2/src/noLegal/java/com/sza/fastmediasorter/di/BrowseApkInstallModule.kt` exists.
- `Grep` — `abstract class BrowseApkInstallModule` present.
- `Grep` — `@Binds` present.
- `Grep` — `fun bindApkInstallHandler(impl: BrowseApkInstallHandlerImpl): BrowseApkInstallHandler` present.

**Status:** `[x] done`

**Step Log:**
- 2026-05-13 — Verification 4/4 PASS. File: `src/noLegal/.../di/BrowseApkInstallModule.kt` (+21 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 1.*` above is `[x] done`.
- [ ] Project compiles (`/build` — assembleNoLegalDebug AND assembleStandardDebug both succeed; the optional handler must be absent from standard build).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated: `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- `BrowseApkInstallHandler` abstract class is in main; both flavors compile with it present.
- `BrowseApkInstallHandlerImpl` is singleton-scoped in noLegal; `registerLaunchers` must be called in `BrowseActivity.onCreate` (via `BrowseManagerInitializer.initialize()`) before `onStart`.
- String resources (`R.string.s0183_*`) referenced in Impl are not yet defined — add them in Phase 03. If compiling Phase 01 triggers missing-resource errors, temporarily stub strings and replace in Phase 03.
- `pendingFile` is not thread-safe. The install flow runs exclusively on the main thread (Activity callbacks) — no coroutine dispatch needed.

---

## Rollback Plan

Revert the four new files. No migration, no schema change, no data written to disk.
