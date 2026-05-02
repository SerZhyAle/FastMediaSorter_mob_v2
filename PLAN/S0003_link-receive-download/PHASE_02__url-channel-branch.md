# Phase 02 — URL Channel Branch

**Strategic spec:** [`../S0003_link-receive-download.md`](../S0003_link-receive-download.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 5 / 5
**Started:** 2026-04-29
**Completed:** 2026-04-29

---

## Objective

Detect a single `http(s)` URL inside `EXTRA_TEXT`, branch the share-receive pipeline through the new auto-download channel when the master toggle is on, and preserve the legacy `.txt` path verbatim when it is off or no URL is present. No download/extraction logic yet — this phase only adds the branching point and a placeholder coordinator that emits a stub error.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (settings fields available).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/UrlInTextDetector.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/LinkDownloadModule.kt` | New | ≤ 80 |

---

## Steps

### Step 02.1 — Add `UrlInTextDetector`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/UrlInTextDetector.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a small object/class that exposes `fun firstHttpUrl(input: String?): String?`. It returns the first `http://` or `https://` URL found in the text using `android.util.Patterns.WEB_URL` filtered to only `http(s)` schemes (reject `ftp:`, `file:`, `content:`). Return `null` for blank input or when no match is `http(s)`. Strip trailing punctuation that is not part of a URL: `.`, `,`, `;`, `!`, `?`, closing brackets/quotes. Logs at `Timber.v` only.

**Verification:**

- `Glob` — file `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/UrlInTextDetector.kt` exists.
- `Grep -n "fun firstHttpUrl"` in the file matches exactly once.
- `Grep -n "android\\.util\\.Patterns"` in the file matches at least once.

**Status:** `[ ]` not done

---

### Step 02.2 — Add `LinkAutoDownloadCoordinator` skeleton

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create the orchestrator class with the following shape (suspending API, Hilt-injectable singleton via `@Inject constructor`):
>
> ```kotlin
> class LinkAutoDownloadCoordinator @Inject constructor(
>     private val settingsRepository: SettingsRepository,
>     // extractors injected in Phase 03/04; ignore for now
> ) {
>     suspend fun handle(url: String, callbacks: Callbacks): Result
>     interface Callbacks {
>         fun onProgress(state: ProgressState)
>     }
>     sealed interface Result {
>         data class Saved(val resourceLabel: String, val fileName: String, val mime: String, val openInPlayerUri: android.net.Uri?) : Result
>         data class FellBackToDownloads(val fileName: String, val reason: FallbackReason, val openInPlayerUri: android.net.Uri?) : Result
>         sealed interface Failed : Result { object NoNetwork: Failed; object Timeout: Failed; object NoMediaFound: Failed; object MimeBlocked: Failed; data class Other(val cause: Throwable): Failed }
>     }
>     enum class FallbackReason { NoResourceConfigured, ResourceUnavailable, ResourceWriteFailed }
>     sealed interface ProgressState { object Probing: ProgressState; data class Downloading(val bytesRead: Long, val total: Long?): ProgressState }
> }
> ```
>
> For this phase the body of `handle` returns `Result.Failed.NoMediaFound` unconditionally — actual implementation lands in Phase 05 once Phase 03/04 strategies exist. Comment the placeholder with `// TODO(phase-05): wire extractor pipeline + writer`. No other production-side TODO markers.

**Verification:**

- `Glob` — file exists.
- `Grep -n "class LinkAutoDownloadCoordinator"` matches exactly once.
- `Grep -n "TODO(phase-05)"` in the same file matches exactly once.
- `Grep -n "sealed interface Result"` matches exactly once.

**Status:** `[ ]` not done

---

### Step 02.3 — Provide Hilt bindings module

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/di/LinkDownloadModule.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Create a Hilt `@Module @InstallIn(SingletonComponent::class)` named `LinkDownloadModule`. It declares no `@Provides` yet (constructor injection is sufficient for the coordinator), but exists as the home module for Phase 03/04/05 bindings. Add a single `@Provides` returning a configured `okhttp3.OkHttpClient` named `LinkDownloadHttpClient` (call timeout 30 s, connect timeout 15 s, read timeout 30 s, follow redirects only when target is `http(s)` via an Interceptor that aborts otherwise). Annotate with `@Named("linkDownload")`.

**Verification:**

- `Glob` — file exists.
- `Grep -n "object LinkDownloadModule"` (or `class LinkDownloadModule`) matches exactly once.
- `Grep -n "@Named(\"linkDownload\")"` in the file matches at least once.
- `Grep -n "OkHttpClient"` in the file matches at least once.

**Status:** `[ ]` not done

---

### Step 02.4 — Branch `ReceiveShareActivity.extractAndCacheFiles`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt`
**Depends on:** Steps 02.1, 02.2

**Prompt for developer:**

> Inject `SettingsRepository` and `LinkAutoDownloadCoordinator` via `@Inject lateinit var`. In `processIntent`, after extracting the `EXTRA_TEXT` string but before the legacy `createTextFile` path, call `UrlInTextDetector.firstHttpUrl(text)`. If the result is non-null AND `settings.linkAutoDownloadEnabled` is true, route through a new `processLinkAutoDownload(url)` method instead of the legacy text branch. When the toggle is off, or no URL is found, keep the existing `.txt` path **byte-for-byte unchanged**. The new method delegates to the coordinator and dismisses the loading dialog when the coordinator emits a terminal state. Wire `Callbacks.onProgress` to update the existing loading dialog message via `setMessage(..)` and re-show.
>
> If the intent has `EXTRA_STREAM` (file share, not text), keep the existing behaviour — the new branch must only trigger for text-only intents.

**Verification:**

- `Grep -n "UrlInTextDetector.firstHttpUrl"` in `ReceiveShareActivity.kt` matches exactly once.
- `Grep -n "processLinkAutoDownload"` in `ReceiveShareActivity.kt` matches at least twice (declaration + call site).
- `Grep -n "linkAutoDownloadEnabled"` in `ReceiveShareActivity.kt` matches at least once.
- `Grep -n "createTextFile"` in `ReceiveShareActivity.kt` still matches at least once (legacy path preserved).

**Status:** `[ ]` not done

---

### Step 02.5 — Add Internet permission entry & manifest verification

**Files:** `app_v2/src/main/AndroidManifest.xml`
**Depends on:** Step 02.4

**Prompt for developer:**

> Verify `<uses-permission android:name="android.permission.INTERNET"/>` is declared. Add it if absent. No other manifest changes — `ReceiveShareActivity` is already registered.

**Verification:**

- `Grep -n "android.permission.INTERNET"` in `app_v2/src/main/AndroidManifest.xml` matches exactly once.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

After this phase, sharing a text URL with the master toggle ON results in a Toast/Snackbar whose user-facing message is `link_autodownload_error_no_media` (via the coordinator's stub `Failed.NoMediaFound`). Sharing the same text with the toggle OFF still produces a `.txt` file via the legacy path. Phase 03 replaces the stub with the direct-file extractor.

---

## Rollback Plan

Revert phase commit(s). The legacy text branch is untouched, so a revert restores prior behaviour without data loss.
