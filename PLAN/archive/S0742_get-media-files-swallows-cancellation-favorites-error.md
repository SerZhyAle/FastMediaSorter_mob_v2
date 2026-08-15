# Strategic spec: S0742 - get-media-files swallows CancellationException + ERROR-spams "Failed to fetch favorites"

**Status:** Archived
**Priority:** 45
**Date:** 2026-06-27
**Tier:** 3 - Moderate
**Roadmap entry:** Parked finding - device log analysis 2026-06-27 (owner saw error toasts while bulk-uploading music)

> Draft inbox entry. Captured from a real-device observation. No research/approval yet.

---

## 0. Raw capture (verbatim, evidence)

Owner observed, while uploading music to the device, a red error toast: `ERROR: [fcfe2bb2|get-media-files] Failed to fetch favorites…` with a COPY action, and the All Music list briefly flickering 6 -> 3 -> 6 files.

Investigation (2026-06-27, real Galaxy S21+, debug build):

1. The toast is DEBUG-ONLY. It is produced by `UiNotificationTree : Timber.Tree()` in `src/debug/.../core/debug/DebugNotificationCenter.kt` (snackbar + `setAction(R.string.debug_action_copy)`), planted via `Timber.plant(UiNotificationTree())` in `DebugToolsBootstrap` - which lives only in `src/debug`. A RELEASE build does not plant it, so end users never see this toast.

2. Root cause of the logged error: `GetMediaFilesUseCase` (`domain/usecase/GetMediaFilesUseCase.kt:358-364`):
```
val favoriteUris = try {
    favoritesRepository.getAllFavorites().first().map { it.uri }.toSet()
} catch (e: Exception) {
    StructuredLogger.e(e, "Failed to fetch favorites")
    emptySet<String>()
}
```
During a bulk music upload, each new file is a MediaStore change -> `BrowseRefreshManager` reload -> a new `get-media-files` flow. Rapid changes CANCEL the in-flight flow; the favorites `.first()` then throws `CancellationException`, which (being an `Exception`) is caught here, logged at ERROR, and the load falls back to `emptySet()`. The list itself loads correctly (the 6->3->6 flicker is reload churn that self-recovers); only the favorite-star marking is skipped for that cancelled pass.

## 1. Problem

Two defects, both independent of the (debug-only) toast:

- **Swallowed CancellationException** - `catch (e: Exception)` at `GetMediaFilesUseCase.kt:361` catches `CancellationException`, breaking structured-concurrency cancellation semantics (a cancelled coroutine should propagate, not be logged + swallowed). The file has no `CancellationException` rethrow anywhere; audit whether other `catch (e: Exception)` blocks in this use case share the bug.
- **Wrong log level for a non-fatal fallback** - a transient favorites-fetch failure that gracefully degrades to "no favorite marks this pass" is logged at ERROR (`StructuredLogger.e`). ERROR should be reserved for things a developer must act on (project log-level policy). At W/I it would not spam the debug error surface during routine bulk operations.

## 2. Goals (provisional)

1. `get-media-files` (and sibling catches in the same use case) rethrow `CancellationException` instead of swallowing it.
2. The non-fatal favorites-fetch fallback logs at an appropriate non-ERROR level (so bulk uploads do not generate ERROR noise / debug toasts).
3. No behavioural regression: files still load; favorite marks still applied on non-cancelled passes.

## 3. Open questions / research

- Enumerate every `catch (e: Exception)` in `GetMediaFilesUseCase` (and adjacent loaders) that can run inside a cancellable flow and may swallow `CancellationException`; decide a consistent rethrow pattern (e.g. `if (e is CancellationException) throw e`).
- Confirm whether `favoritesRepository.getAllFavorites().first()` can ALSO throw a genuine (non-cancellation) error on a fresh/clearing DB, and whether that path deserves a one-time user-facing message in release.
- Decide the right log level (W vs I) per the project log-level policy.

## 4. Evidence

- `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GetMediaFilesUseCase.kt:358-364`.
- `app_v2/src/debug/java/com/sza/fastmediasorter/core/debug/DebugNotificationCenter.kt` (`UiNotificationTree`, debug-only toast surface).
- Live log (clean pass): `[cad539ff|get-media-files] ... Scanner returned files {count=6} ... COMPLETE` (no error) - the error pass had scrolled out of the buffer.

## 5. Notes

- NOT a release blocker: the visible toast is debug-only; the media load is correct. This is code-correctness + log-hygiene, plus a structured-concurrency anti-pattern worth fixing.

---

## Implementation - 2026-06-27

Owner directive: "if it's not an error - wrap it; even in debug, only what we intend to fix should pop up." Two layers:

1. Source (`GetMediaFilesUseCase.kt`): both `catch (e: Exception)` blocks that run inside the cancellable flow now rethrow `CancellationException` first:
   - favorites fetch (was logging ERROR -> the reported toast) - rethrow, so cancellation is no longer logged/surfaced.
   - progressive partial-scan catch (was logging W + "continuing with full scan") - rethrow, so a superseded reload cancels instead of pressing on.
   - Added `import kotlinx.coroutines.CancellationException`.
2. Debug surface (`src/debug/.../DebugNotificationCenter.kt`, `UiNotificationTree`): the tree now drops any ERROR whose throwable is (or is caused by) a `java.util.concurrent.CancellationException` via a bounded cause-chain walk - a repo-wide guard so cancellation never reaches the debug snackbar even if another catch logs it before being fixed at source.

Validation: `.\a.ps1 fk` exit 0. Device re-verification: rescan/bulk-reload churn produces no "Failed to fetch favorites" ERROR surface.

---

## Last Audit

Manual (device RFCR110NBQJ, Galaxy S21+ Android 15, 2026-06-27): PASS - expected: no 'Failed to fetch favorites' ERROR during bulk churn | actual: browsed the "All Files" (/storage/emulated/0) MediaStore-backed list and induced heavy churn (multiple file bursts + repeated `scan_volume` + in-app refresh hammer); the list reloaded live and rendered correctly throughout (file count climbed 63 -> 79 -> 91 -> 92 -> 111), 12 `get-media-files` flows fired and all reached COMPLETE; zero `Failed to fetch favorites` lines, zero app-level ERROR (`E/SLog`/`E/StructuredLogger`), zero debug snackbar / `UiNotificationTree` / COPY surface. Source fix confirmed in build: `catch (e: CancellationException) { throw e }` precedes the favorites error catch at `GetMediaFilesUseCase.kt:366-374`, and the debug `UiNotificationTree` drops any ERROR caused by a CancellationException via cause-chain walk (`DebugNotificationCenter.kt:114-126`).

Caveat: under the local scanner (~20ms/scan) the rapid reloads completed before they could cancel each other, so no flow was observed abandoned strictly mid-favorites-`.first()` in the captured logcat; the reported symptom did not recur even under aggressive bulk churn, and the source guarantees a mid-`.first()` cancellation now rethrows rather than logging ERROR.

Evidence: `temp/S0742_devtest/` (01_baseline_allfiles_list.png, 02_after_churn_list_rendered.png, logcat_excerpt_get-media-files.txt, logcat_full.txt).
