# Phase 04 — noLegal Cloud APK Install

**Strategic spec:** [`../S0266_cloud-download-filename-and-progress.md`](../S0266_cloud-download-filename-and-progress.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 5 / 5
**Started:** —
**Completed:** —

---

## Objective

Make APK launch from cloud (Google Drive / Dropbox / OneDrive) work in `noLegal` flavor without showing the universal `FileOperationProgressDialog`. The existing `BrowseApkInstallHandlerImpl` lives in `src/noLegal/` and calls `File(file.path).triggerInstall()` — for cloud paths this currently produces a broken `File("cloud://google_drive/<fileId>")` reference. Extend the noLegal handler to download the APK to `context.cacheDir` first with the correct `.apk` extension, show a single Toast at start, and only then launch `PackageInstaller`.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (display-name propagation).
- [ ] Working tree compiles on `./a.ps1 dq`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/BrowseApkInstallHandlerImpl.kt` | Modified | ≤ 280 |
| `app_v2/src/main/res/values/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | — |

> Flavor placement: existing handler is already in `src/noLegal/java/...` per `dev/FLAVOR_DEVELOPMENT_RULES.md`. Hilt binding already lives in `src/noLegal/java/.../di/BrowseApkInstallModule.kt` — no new module needed.
>
> Note on string placement: the new Toast text "Подготавливается установка.." is a generic short status; placing it in `src/main/res/` keeps it discoverable for any future flavor that needs the same flow. The string is wired in only from `src/noLegal/`, so `standard` builds will not reference it.

---

## Steps

### Step 04.1 — Add APK download Toast string (trilingual)

**Files:**
- `app_v2/src/main/res/values/strings.xml`
- `app_v2/src/main/res/values-ru/strings.xml`
- `app_v2/src/main/res/values-uk/strings.xml`

**Depends on:** — start of phase

**Prompt for developer:**

> Add new key `s0266_apk_download_preparing` to all three locale files. EN: `Preparing install..`. RU: `Подготавливается установка..`. UK: `Готується встановлення..`. Use `..` (two dots). Place near existing `s0183_apk_install_*` keys. Strings pass COMMUNICATION_POLICY §6 checklist (short, neutral, no exclamation, present tense).

**Verification:**

- `Grep` — `s0266_apk_download_preparing` matches exactly 3 times across the locale files.
- `Bash` — `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "s0266"` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 — Verification 2/2 PASS. Files: values/values-ru/values-uk strings.xml (+1 key each).

---

### Step 04.2 — Inject `CloudFileOperationHandler` into `BrowseApkInstallHandlerImpl`

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/BrowseApkInstallHandlerImpl.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add a constructor parameter `private val cloudFileOperationHandler: CloudFileOperationHandler` (with `@Inject` already on the class). Add the import. The handler is already provided by Hilt — `@Inject constructor` will resolve it. This is plumbing only; Step 04.3 uses it.

**Verification:**

- `Grep` — `cloudFileOperationHandler: CloudFileOperationHandler` matches in the handler file.
- `Grep` — `import com.sza.fastmediasorter.data.cloud.CloudFileOperationHandler` present.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 — Verification 2/2 PASS. Folded into the same edit as 04.3/04.4.

---

### Step 04.3 — Branch `triggerInstall` for cloud sources

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/BrowseApkInstallHandlerImpl.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Refactor `triggerInstall(file: MediaFile)`: if `file.path.startsWith("cloud://")`, branch into a new private suspend function `downloadAndInstallFromCloud(file: MediaFile)`. Otherwise call the existing local-file path. In `downloadAndInstallFromCloud`:
>
> 1. Show `Toast` with `R.string.s0266_apk_download_preparing` on the activity.
> 2. Build `cacheApkFile = File(context.cacheDir, "apk_install/${file.name}")` — `file.name` is the display-name (e.g. `MyApp.apk`). Ensure parent dir exists.
> 3. Call `cloudFileOperationHandler.downloadFromCloudTo(file.path, cacheApkFile.parent ?: context.cacheDir.absolutePath, file.name, progressCallback = null)`.
> 4. On success → call the existing local-file `triggerInstall` logic but with `cacheApkFile` instead of `File(file.path)`. On failure → `Toast(R.string.s0183_apk_install_failed)`.
> 5. Use a `CoroutineScope(Dispatchers.Main + SupervisorJob())` field for the launch — the handler is `@Singleton`, so the scope can be a class-level `private val installScope`. Or use `activityRef.get()?.lifecycleScope` if available.
>
> Add `import` for `kotlinx.coroutines.*` as needed. Insert `Timber.d("S0266: noLegal cloud APK install entry for ${file.path}")` at the entry of `downloadAndInstallFromCloud` — this is the debug verification tag, will be present while spec is `BlockNeedUserTest`.

**Verification:**

- `Grep` — `private suspend fun downloadAndInstallFromCloud` matches in the handler.
- `Grep` — `file.path.startsWith("cloud://")` matches in the handler.
- `Grep` — `R.string.s0266_apk_download_preparing` matches in the handler.
- `Grep` — `Timber.d("S0266:` present in the handler.
- `Grep` — `cloudFileOperationHandler.downloadFromCloudToPublic` present (public wrapper added to CloudFileOperationHandler; private method stays private).

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 — Verification 5/5 PASS (Timber.d S0266 tag present; cloud branch + Toast wired; downloadFromCloudToPublic public wrapper added to CloudFileOperationHandler). Note: `downloadFromCloudTo` is private — added thin `downloadFromCloudToPublic` wrapper instead of changing visibility, to avoid widening the cloud handler's public surface beyond the noLegal need.

---

### Step 04.4 — Refactor local-file `triggerInstall` into a reusable helper

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/BrowseApkInstallHandlerImpl.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> Extract the existing body of `triggerInstall(file: MediaFile)` (everything inside the `try { val apkFile = File(file.path); ... }` block) into a private helper `private fun launchSystemInstaller(apkFile: File, fileName: String)`. `fileName` is used for the `Timber.e(... for $fileName)` log message. Update the original `triggerInstall(file: MediaFile)` to call `launchSystemInstaller(File(file.path), file.name)` for local paths. The cloud branch from Step 04.3 also calls `launchSystemInstaller(cacheApkFile, file.name)`.

**Verification:**

- `Grep` — `private fun launchSystemInstaller(apkFile: File, fileName: String)` matches in the handler.
- `Grep` — `launchSystemInstaller(` matches at least twice (called from both local and cloud paths).
- `Bash` — `./a.ps1 dq` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 — Verification 3/3 PASS. Files: `BrowseApkInstallHandlerImpl.kt` (full rewrite: cloud branch + launchSystemInstaller helper + Activity → ComponentActivity for lifecycleScope).

---

### Step 04.5 — Build gate for noLegal flavor

**Files:** —
**Depends on:** Step 04.1 .. Step 04.4

**Prompt for developer:**

> Run `./a.ps1 nlq` (noLegal quiet debug — if no shortcut alias exists, run the full command: `./gradlew :app_v2:assembleNoLegalDebug` via `./a.ps1`). Treat BUILD SUCCESSFUL as the gate. If the noLegal flavor wasn't compiled before, also run `./a.ps1 dq` to ensure standardDebug still compiles (the handler lives in noLegal source set only — standardDebug should not see it, but main code should not regress).

**Verification:**

- `Bash` — noLegalDebug variant compiles (BUILD SUCCESSFUL).
- `Bash` — standardDebug still compiles (BUILD SUCCESSFUL).

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 — Verification 2/2 PASS. standardDebug → BUILD SUCCESSFUL 44s (v2.60.5201.230). noLegalDebug → BUILD SUCCESSFUL 2m 12s (v2.60.5201.231).

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] noLegalDebug and standardDebug both build clean.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `Timber.d("S0266:` tag inserted in handler (will stay until spec leaves `BlockNeedUserTest`).

---

## Handoff Notes to Next Phase

- noLegal APK install from cloud now uses a hidden cache-dir download + standard system installer.
- No `FileOperationProgressDialog` involvement for this flow.
- Phase 05 catalog cleanup must run `set.ps1 -NoFlavors "standard,lite,photos,legacy,vr"` for the modified noLegal handler if its public surface changed (constructor signature did) — catalog hint per `/spec-tech` constraints.

---

## Rollback Plan

Revert Phase 04 commit(s). The handler is flavor-scoped — standard/vr builds are unaffected. noLegal returns to its previous broken-for-cloud state, but local APK install continues to work.
