# Phase 05 — Writer, Progress, Auto-Open

**Strategic spec:** [`../S0003_link-receive-download.md`](../S0003_link-receive-download.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03, Phase 04
**Blocks:** Phase 06
**Steps done:** 6 / 6
**Started:** 2026-04-29
**Completed:** 2026-04-29

---

## Objective

Wire the coordinator end-to-end: walk strategies, stream the chosen body to a temp cache file with progress callbacks, copy to the configured destination via `FileOperationUseCase` (or fall back to MediaStore Downloads), surface a non-modal progress UI with cancel, post a result Snackbar/Toast, and — when `linkAutoDownloadOpenInPlayer` is on — launch `StandalonePlayerActivity` with the resulting `Uri`.

---

## Prerequisites

- [ ] Phase 02 ✅ Done (coordinator + share branch).
- [ ] Phase 03 ✅ Done (direct strategy + registry).
- [ ] Phase 04 ✅ Done (html strategy).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt` | Modified | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/LinkDownloadWriter.kt` | New | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/LinkAutoDownloadProgressDialog.kt` | New | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt` | Modified | ≤ 500 |
| `app_v2/src/main/res/layout/dialog_link_autodownload_progress.xml` | New | — |

---

## Steps

### Step 05.1 — Implement `LinkDownloadWriter`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/LinkDownloadWriter.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> `@Singleton class LinkDownloadWriter @Inject constructor(@ApplicationContext private val context: Context, private val fileOperationUseCase: FileOperationUseCase, private val getDestinationsUseCase: GetDestinationsUseCase)`.
>
> Public API:
>
> ```kotlin
> sealed interface WriteResult {
>     data class Saved(val resourceLabel: String, val fileName: String, val destinationUri: android.net.Uri?) : WriteResult
>     data class FellBackToDownloads(val fileName: String, val reason: FallbackReason, val destinationUri: android.net.Uri?) : WriteResult
>     data class Failed(val cause: Throwable) : WriteResult
> }
>
> suspend fun writeFromStream(
>     stream: java.io.InputStream,
>     mime: String,
>     suggestedFileName: String,
>     resourceId: Long?,
>     onBytesCopied: (Long) -> Unit,
> ): WriteResult
> ```
>
> Implementation:
>
> 1. Stream into `cacheDir/link_downloads/<sanitised>` with collision suffix `_<epoch>` if the target name exists. Honour cancellation via `coroutineContext.ensureActive()` inside the copy loop. Update `onBytesCopied` after each chunk.
> 2. If `resourceId != null` → look up via `getDestinationsUseCase().first()`. When found → call `fileOperationUseCase.execute(FileOperation.Copy(sources = listOf(tempFile), destination = File(resource.path), overwrite = false))`. On `Success` return `Saved(resource.name, fileName, null)`. On `AuthenticationRequired`/`Failure` return `FellBackToDownloads(fileName, ResourceWriteFailed, downloadsUri)` after running step 4. When the resource is not found → `FellBackToDownloads(.., ResourceUnavailable, ..)`.
> 3. If `resourceId == null` → run step 4 directly, return `FellBackToDownloads(.., NoResourceConfigured, ..)`.
> 4. Downloads write: copy the temp file via `MediaStore.Downloads` (Q+) or `Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS)` (≤ P). Reuse the structure from `SaveVideoFrameManager.saveToDownloads` — extract the shared logic if both manager and writer end up duplicating it; if extraction is risky, copy and document with `// duplicated logic from SaveVideoFrameManager.saveToDownloads — see S0003 phase 05`.
> 5. Always delete the temp cache file in `finally`.
>
> Use `Timber` only.

**Verification:**

- `Glob` — file exists.
- `Grep -n "class LinkDownloadWriter"` matches exactly once.
- `Grep -n "FileOperation.Copy"` in the file matches at least once.
- `Grep -n "MediaStore.Downloads"` in the file matches at least once.
- `Grep -n "Log\\.d\\("` returns zero hits.

**Status:** `[x] done`

---

### Step 05.2 — Wire `LinkAutoDownloadCoordinator.handle`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt`
**Depends on:** Steps 05.1; Phase 03/04 strategies.

**Prompt for developer:**

> Replace the placeholder body. Constructor now also takes `LinkExtractionRegistry` and `LinkDownloadWriter`.
>
> Algorithm:
>
> 1. Read settings; if `linkAutoDownloadEnabled == false` → `Result.Failed.Other(IllegalStateException("auto_download_disabled"))`. (The caller should never invoke when disabled, but defend.)
> 2. For each strategy in `registry.ordered()`:
>    - `callbacks.onProgress(ProgressState.Probing)`
>    - When `probe(url)` returns `Applicable(..)` → `callbacks.onProgress(ProgressState.Downloading(0, tentativeSize))` and call `open(url) { bytesRead, total -> callbacks.onProgress(Downloading(bytesRead, total)) }`.
>    - On `OpenResult.Stream` → break the loop and proceed to write.
>    - On `OpenResult.NotFound` → break with `Result.Failed.NoMediaFound`.
>    - On `OpenResult.Blocked(MimeNotAllowed | RedirectToNonHttp | NonHttpScheme)` → break with `Result.Failed.MimeBlocked`.
>    - On `OpenResult.Error(IOException)` → break with `Result.Failed.NoNetwork` for `UnknownHostException`/`ConnectException`, `Result.Failed.Timeout` for `SocketTimeoutException`, otherwise `Failed.Other(cause)`.
>    - On `ProbeResult.NotApplicable` → continue to next strategy.
>    - On `ProbeResult.TransientError` → continue (next strategy may succeed).
> 3. If no strategy returned `Applicable` → `Result.Failed.NoMediaFound`.
> 4. With the `Stream` in hand, call `writer.writeFromStream(..)` passing `settings.linkAutoDownloadResourceId` and a progress lambda that forwards bytes to the dialog.
> 5. Map `WriteResult` to `Result`:
>    - `Saved(label, fileName, uri)` → `Result.Saved(label, fileName, mime, openInPlayerUri = uri.takeIf { settings.linkAutoDownloadOpenInPlayer })`
>    - `FellBackToDownloads(fileName, reason, uri)` → `Result.FellBackToDownloads(fileName, reason, openInPlayerUri = uri.takeIf { settings.linkAutoDownloadOpenInPlayer })`
>    - `Failed(cause)` → `Result.Failed.Other(cause)`
> 6. Always close the strategy stream via the `close` lambda inside `try { .. } finally { close() }`.
>
> Single TODO marker is now removed (Phase 02 placeholder gone).

**Verification:**

- `Grep -n "TODO(phase-05)"` in `LinkAutoDownloadCoordinator.kt` returns zero hits.
- `Grep -n "registry.ordered"` in the file matches at least once.
- `Grep -n "writer.writeFromStream"` in the file matches exactly once.
- `Grep -n "Result.Failed.NoMediaFound"` in the file matches at least twice (no-strategy-applicable + NotFound branch).
- `Grep -n "linkAutoDownloadOpenInPlayer"` in the file matches at least once.

**Status:** `[x] done`

---

### Step 05.3 — Build progress dialog with cancel

**Files:**
`app_v2/src/main/res/layout/dialog_link_autodownload_progress.xml`,
`app_v2/src/main/java/com/sza/fastmediasorter/ui/share/LinkAutoDownloadProgressDialog.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> `dialog_link_autodownload_progress.xml`: a Material dialog body containing a title `link_autodownload_progress_starting`, a horizontal `LinearProgressIndicator` (`indeterminate` toggleable), a numeric line "X / Y MB", and a `MaterialButton` with text `link_autodownload_cancel`. Do not use a modal full-screen overlay — the dialog must dismissable.
>
> `LinkAutoDownloadProgressDialog.kt`: thin wrapper exposing
>
> ```kotlin
> class LinkAutoDownloadProgressDialog(activity: AppCompatActivity, private val onCancel: () -> Unit) {
>     fun show()
>     fun update(state: LinkAutoDownloadCoordinator.ProgressState)
>     fun dismiss()
> }
> ```
>
> `update`:
>
> - `Probing` → switch indicator to indeterminate, hide bytes line.
> - `Downloading(read, total)` when `total != null` → switch to determinate, set `progress = (read * 100 / total).toInt()`, render bytes as `formatBytes(read) + " / " + formatBytes(total)`.
> - `Downloading(read, null)` → keep indeterminate, show only `formatBytes(read)`.
>
> The cancel button calls `onCancel()` exactly once and dismisses the dialog.

**Verification:**

- `Glob` — both files exist.
- `Grep -n "class LinkAutoDownloadProgressDialog"` matches exactly once.
- `Grep -n "LinearProgressIndicator"` in `dialog_link_autodownload_progress.xml` matches at least once.
- `Grep -n "link_autodownload_cancel"` in `dialog_link_autodownload_progress.xml` matches at least once.
- `Grep -n "fun update"` in the dialog Kotlin file matches exactly once.

**Status:** `[x] done`

---

### Step 05.4 — Replace stub progress in `ReceiveShareActivity` with real coordinator + dialog

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt`
**Depends on:** Steps 05.2, 05.3

**Prompt for developer:**

> Inject `LinkAutoDownloadCoordinator` and a way to launch `StandalonePlayerActivity`. In the `processLinkAutoDownload(url)` method introduced in Phase 02:
>
> 1. Show `LinkAutoDownloadProgressDialog` with `onCancel = { coordinationJob?.cancel() }`.
> 2. Launch `lifecycleScope.launch { ... }` keeping the resulting `Job` on `coordinationJob`. Pass `Callbacks.onProgress` that posts to the main thread and calls `dialog.update(state)`.
> 3. Map terminal `Result` to user-facing feedback:
>    - `Saved(label, fileName, mime, openUri)` → Snackbar/Toast `link_autodownload_done_resource` (with `label`); if `openUri != null` → `startActivity(Intent(this, StandalonePlayerActivity::class.java).setData(openUri).addFlags(FLAG_GRANT_READ_URI_PERMISSION))`.
>    - `FellBackToDownloads(fileName, reason, openUri)` → Snackbar/Toast that combines `link_autodownload_done_downloads` with the reason via `link_autodownload_fallback_downloads`. Open behaviour identical.
>    - `Failed.NoNetwork` → `link_autodownload_error_no_network`. No auto-open.
>    - `Failed.Timeout` → `link_autodownload_error_timeout`.
>    - `Failed.NoMediaFound` → `link_autodownload_error_no_media`.
>    - `Failed.MimeBlocked` → `link_autodownload_error_mime_blocked`.
>    - `Failed.Other(cause)` → re-use existing `R.string.receive_share_cache_failed`.
> 4. Always dismiss the progress dialog and call `cleanupAndFinish()` from the activity-side scope.
>
> Verify the legacy non-URL text path still calls `createTextFile` unchanged.

**Verification:**

- `Grep -n "LinkAutoDownloadProgressDialog"` in `ReceiveShareActivity.kt` matches at least twice (instantiation + dismiss).
- `Grep -n "StandalonePlayerActivity::class.java"` in `ReceiveShareActivity.kt` matches at least once.
- `Grep -n "coordinator.handle"` in `ReceiveShareActivity.kt` matches exactly once.
- `Grep -n "createTextFile"` in `ReceiveShareActivity.kt` still matches at least once (legacy path preserved).

**Status:** `[x] done`

---

### Step 05.5 — Honour `disableShareReceiver` runtime gate

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt`
**Depends on:** Step 05.4

**Prompt for developer:**

> The activity is enabled/disabled at runtime via `DefaultPlayerManager.applyShareReceiverState`. Confirm the existing logic still works: if a user disabled `acceptSharedFiles`, the activity is component-disabled and never reached; the new branch lives entirely after `onCreate`. No code change is required unless inspection shows a regression — in that case, restore the existing guard. Either way, this step is a deliberate inspection checkpoint.

**Verification:**

- `Grep -n "DefaultPlayerManager.applyShareReceiverState"` in the project (any file) matches at least once (the existing call site is preserved).
- `Grep -n "ReceiveShareActivity"` in `app_v2/src/main/AndroidManifest.xml` matches at least once.

**Status:** `[x] done`

---

### Step 05.6 — Manual smoke list (deferred to human)

**Files:** none (manual)
**Depends on:** Steps 05.1–05.5

**Prompt for developer:**

> Manual on-device verification list (must be run by a human; record results in `## Last Audit` after `/spec-check`):
>
> 1. Master toggle OFF → share an `https://example.com/foo` text → `.txt` is created (legacy path).
> 2. Master toggle ON, no resource selected → share `https://images.example.com/sample.jpg` → file lands in Downloads, Snackbar shows fallback reason.
> 3. Master toggle ON, resource selected → share the same URL → file lands in the resource.
> 4. Master toggle ON, auto-open ON → after a successful image download, the player opens with the saved file.
> 5. Master toggle ON, auto-open OFF → after a successful download, only the Snackbar appears.
> 6. Share a URL that 302s to `file:///` → blocked with `link_autodownload_error_mime_blocked`.
> 7. Share a non-media URL (e.g. plain HTML page with no `<video>`/`<img>` larger than 1 MiB) → `link_autodownload_error_no_media`.
> 8. Cancel mid-download via the dialog → no partial file remains in cache or destination.
> 9. Confirm trilingual labels render correctly in EN/RU/UK.
>
> Until the human has executed and logged this checklist, the spec status is `BlockNeedUserTest` once the rest of the audit passes.

**Verification:**

- This step has no static predicate; it is permanently `[manual — deferred to human]` and counts toward MANUAL items in the spec-all final report.

**Status:** `[manual — deferred to human]`

---

## Phase Done Criteria

- [ ] Every static `Step 05.*` above is `[x] done`.
- [ ] Step 05.6 is `[manual — deferred to human]` (not a blocker).
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

The feature is functionally complete. Phase 06 is documentation, FEATURES trilingual update, catalog regen, and the final dev-log batch.

---

## Rollback Plan

Revert phase commit(s). Phases 01–04 leave the codebase in a state where the master toggle is visible but routing the URL still falls back to the placeholder error — acceptable as a partial revert.
