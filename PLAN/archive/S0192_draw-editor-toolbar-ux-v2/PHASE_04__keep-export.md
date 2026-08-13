# Phase 04 — Google Keep export helper

**Strategic spec:** [`../S0192_draw-editor-toolbar-ux-v2.md`](../S0192_draw-editor-toolbar-ux-v2.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — independent of Phases 01–03
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-05-16
**Completed:** 2026-05-16

**Notes:** `<cache-path>` already present in `res/xml/file_provider_paths.xml` → Step 04.2 is a no-op (verification only). All Grep predicates + build PASS.

---

## Objective

Ship a self-contained `DrawKeepExportHelper` that merges the current drawing, stages bytes to `cacheDir`, builds a `FileProvider` URI, and fires `ACTION_SEND` targeted at Google Keep with graceful fallback to the generic share chooser when Keep is absent. The helper is invoked from the overflow menu in Phase 05.

---

## Prerequisites

- [ ] FileProvider authority `${applicationId}.fileprovider` is declared in `app_v2/src/main/AndroidManifest.xml` (verified: line 252 at spec authoring time — confirm before starting).
- [ ] `MergeDrawOverlayUseCase` exists at `domain/usecase/MergeDrawOverlayUseCase.kt` (verified at spec authoring time).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DrawKeepExportHelper.kt` | New | ≤ 180 |
| `app_v2/src/main/res/values/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | — |

---

## Steps

### Step 04.1 — Create `DrawKeepExportHelper.kt`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DrawKeepExportHelper.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `class DrawKeepExportHelper @Inject constructor(private val mergeDrawOverlayUseCase: MergeDrawOverlayUseCase)`. Public suspend method:
>
> ```kotlin
> suspend fun export(
>     activity: Activity,
>     baseBitmap: Bitmap,
>     overlayBitmap: Bitmap
> ): Result<Unit>
> ```
>
> Implementation:
> 1. `val bytes = mergeDrawOverlayUseCase.execute(baseBitmap, overlayBitmap, Bitmap.CompressFormat.JPEG, 95).getOrElse { return Result.failure(it) }`.
> 2. On `Dispatchers.IO`: write `bytes` to `File(activity.cacheDir, "draw_keep_export_${System.currentTimeMillis()}.jpg")` via `FileOutputStream.use { it.write(bytes) }`. Overwrite if exists.
> 3. Compute `val authority = "${activity.packageName}.fileprovider"` and obtain `val uri = FileProvider.getUriForFile(activity, authority, file)`.
> 4. Switch back to `Dispatchers.Main`. Build the intent:
>    ```kotlin
>    val intent = Intent(Intent.ACTION_SEND).apply {
>        type = "image/jpeg"
>        putExtra(Intent.EXTRA_STREAM, uri)
>        putExtra(Intent.EXTRA_TEXT, "")
>        clipData = ClipData.newRawUri("", uri)   // Antigravity §9.2 — Android 11+ URI grant compat
>        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
>    }
>    ```
> 5. Try Keep-targeted send first:
>    ```kotlin
>    val keepIntent = Intent(intent).setPackage("com.google.android.keep")
>    if (keepIntent.resolveActivity(activity.packageManager) != null) {
>        activity.startActivity(keepIntent)
>    } else {
>        activity.startActivity(Intent.createChooser(intent, null))
>    }
>    ```
> 6. Return `Result.success(Unit)`. Cache file cleanup is left to Android's automatic cache trim — do not delete manually.
>
> Wrap the body in `runCatching { … }` so I/O exceptions surface as `Result.failure`. Failure path is consumed by the caller in Phase 05; this phase does not show toasts.
>
> Hilt: no module needed — constructor injection works because `MergeDrawOverlayUseCase` is already `@Inject`-able.

**Verification:**

- `Glob` — file exists.
- `Grep` — `class DrawKeepExportHelper @Inject constructor` matches exactly once.
- `Grep` — `setPackage("com.google.android.keep")` matches exactly once.
- `Grep` — `ClipData.newRawUri` matches exactly once (Antigravity §9.2 invariant).
- `Grep` — `Intent.FLAG_GRANT_READ_URI_PERMISSION` matches exactly once.
- `Grep` — `FileProvider.getUriForFile` matches exactly once.
- `Grep` — `${activity.packageName}.fileprovider` or `"\${activity.packageName}.fileprovider"` matches exactly once.
- Build: `/build` returns 0 exit. expected: BUILD SUCCESSFUL | actual: <fill in after run>.

**Status:** `[x] done`

---

### Step 04.2 — Verify FileProvider `cache_path` mapping for `cacheDir`

**Files:** `app_v2/src/main/res/xml/file_paths.xml` (or whichever filename the manifest's `<meta-data>` references)
**Depends on:** — independent

**Prompt for developer:**

> Inspect the FileProvider `<meta-data android:name="android.support.FILE_PROVIDER_PATHS">` resource referenced by the `<provider>` declaration in `AndroidManifest.xml`. Confirm there is a `<cache-path>` entry mapping `cacheDir` (root). Typical content:
>
> ```xml
> <cache-path name="cache" path="." />
> ```
>
> If the entry is missing, add it. Without this path, `FileProvider.getUriForFile` for a `cacheDir` file throws `IllegalArgumentException` at runtime.

**Verification:**

- `Grep` — `<cache-path` matches exactly once in the file-paths XML.
- If file-paths XML was edited: build `/build` returns 0 exit. expected: BUILD SUCCESSFUL | actual: <fill in after run>.

**Status:** `[x] done`

---

### Step 04.3 — Add new strings (EN / RU / UK)

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** — independent

**Prompt for developer:**

> Add the following keys to all three `strings.xml` files. Strings pass `docs/COMMUNICATION_POLICY.md` §2 (formula) and §6 tone checklist — error message is informational, not blaming.
>
> | Key | EN | RU | UK |
> |-----|----|----|-----|
> | `draw_overflow_keep` | Send to Google Keep | Отправить в Google Keep | Надіслати до Google Keep |
> | `draw_keep_not_installed` | Google Keep is not installed | Google Keep не установлен | Google Keep не встановлено |
>
> Russian text uses `ё`/`Ё` where applicable.

**Verification:**

- `Grep` (target: `values/strings.xml`) — both keys present exactly once.
- `Grep` (target: `values-ru/strings.xml`) — both keys present exactly once.
- `Grep` (target: `values-uk/strings.xml`) — both keys present exactly once.
- Strings pass COMMUNICATION_POLICY §6 checklist (developer self-check).
- Locale audit: `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "draw_overflow_keep"` returns exit 0. expected: 0 missing | actual: <fill in after run>.
- Locale audit: `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "draw_keep_"` returns exit 0. expected: 0 missing | actual: <fill in after run>.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles — BUILD SUCCESSFUL (standardDebug).
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] String locale audits both return 0.
- [x] Catalog regenerated.

---

## Handoff Notes to Next Phase

`DrawKeepExportHelper` is `@Inject`-ready. Phase 05 wires it into the overflow popup menu and shows toast/snackbar on `Result.failure` using `draw_save_failed_toast` (added in Phase 06) — Phase 05 may stub the failure path until Phase 06 lands.

---

## Rollback Plan

Revert the phase commit — the helper class disappears, strings remain unreferenced. No data migration. The `<cache-path>` entry (if newly added) is harmless to leave in place; revert it only if no other code uses `cacheDir` URIs.
