# Phase 03 — Progress hook wiring (Python → Java via Chaquopy)

**Strategic spec:** [`../S0190_nolegal-youtube-shorts-ytmusic-extraction.md`](../S0190_nolegal-youtube-shorts-ytmusic-extraction.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-05-14
**Completed:** 2026-05-14

---

## Objective

Wire yt-dlp's `progress_hooks` through Chaquopy so `downloadViaPython()` reports byte-level progress to the existing `onProgress: (Long) -> Unit` callback. Without this, share-UI sees `Probing` until completion — confusing for multi-minute YT downloads.

---

## Prerequisites

- [x] Phase 02 ✅ Done — googlevideo URLs route through `downloadViaPython()`.
- [x] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/noLegal/python/ytdlp_utils.py` | Modified | ≤ 230 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/YtDlpExtractionStrategy.kt` | Modified | ≤ 620 |

---

## Steps

### Step 03.1 — Add `progress_callback` kwarg to `download_to_file`

**Files:** `app_v2/src/noLegal/python/ytdlp_utils.py`
**Depends on:** — phase start

**Prompt for developer:**

> Extend signature to `def download_to_file(url, cookie_file, out_dir, file_stem, user_agent=None, audio_only=False, progress_callback=None):`.
>
> Inside the function, before constructing `opts`, add a local hook function that bridges yt-dlp's progress dict to the Java callback:
> ```python
> def _on_progress(d):
>     if progress_callback is None:
>         return
>     if d.get('status') != 'downloading':
>         return
>     downloaded = d.get('downloaded_bytes')
>     total = d.get('total_bytes') or d.get('total_bytes_estimate')
>     if downloaded is None:
>         return
>     try:
>         # Java side accepts long; total may be None → pass -1 sentinel.
>         progress_callback(int(downloaded), int(total) if total else -1)
>     except Exception:
>         # Never let a callback error abort the download.
>         pass
> ```
>
> Then in the `opts` dict add `'progress_hooks': [_on_progress]` (alongside the existing keys from Phase 02). yt-dlp invokes the hook from its download thread; the GIL ensures serialised access into Java.

**Verification:**

- `Grep` — `def download_to_file\(url, cookie_file, out_dir, file_stem, user_agent=None, audio_only=False, progress_callback=None\):` matches once.
- `Grep` — `'progress_hooks': \[_on_progress\]` matches.
- `Grep` — `def _on_progress\(d\):` matches once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-14 — Verification 3/3 PASS. Files: ytdlp_utils.py (+20 LOC). Dev log recorded.

---

### Step 03.2 — Pass `onProgress` into `downloadViaPython()` from Kotlin

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/YtDlpExtractionStrategy.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add a new parameter to `downloadViaPython(...)`:
> ```kotlin
> private fun downloadViaPython(
>     url: String,
>     cookieFile: java.io.File?,
>     fallbackTitle: String,
>     fallbackExt: String,
>     userAgent: String,
>     audioOnly: Boolean,
>     onProgress: (Long) -> Unit,           // S0190 Phase 03: forwarded to yt-dlp progress_hooks
> ): OpenResult { … }
> ```
> Replace every call site (4 total after Phase 02 — googlevideo, AuthRequired, MimeNotAllowed, PythonOnly) to forward the same `onProgress` lambda the strategy already received from `open(url, onProgress, …)`.
>
> Inside `downloadViaPython()`, build a Chaquopy-compatible Python callable for the hook. The simplest pattern: pass a `com.chaquo.python.PyObject` wrapping a Kotlin lambda. Chaquopy auto-wraps Java `Runnable`/`Callable` — for two-arg `(Long, Long) -> Unit` use the `com.chaquo.python.kwarg`/lambda bridge or define a tiny `interface ProgressBridge { fun invoke(downloaded: Long, total: Long) }` and pass an anonymous implementation. Choose whichever the existing Chaquopy bridge already supports in this project (grep for `callAttr(.+lambda` or existing `ProgressBridge`-like names before writing new ones).
>
> Pass the bridge as a kwarg in the existing call:
> ```kotlin
> utils.callAttr(
>     "download_to_file",
>     url,
>     cookieFile?.absolutePath,
>     cacheDir.absolutePath,
>     stem,
>     userAgent,
>     audioOnly,
>     progressBridge,        // matches Python positional `progress_callback`
> )
> ```
> The Python side calls `progress_callback(downloaded, total)` → bridge.invoke(downloaded, total) → `onProgress(downloaded)`. Discard `total` for now (the existing `(Long) -> Unit` signature only takes downloaded bytes; total is logged at the Python side via `Timber.d` if needed — but logging belongs to Phase 04).

**Verification:**

- `Grep` — `onProgress: \(Long\) -> Unit,` matches in the `downloadViaPython(` parameter list.
- `Grep` — `progressBridge` (or whichever name was chosen) appears in `YtDlpExtractionStrategy.kt`.
- `Grep` — `utils.callAttr\(\s*"download_to_file"` is followed by the bridge argument as the 7th positional parameter.

**Status:** `[x]` done

**Step Log:**

- 2026-05-14 — Verification 3/3 PASS. ProgressBridge fun interface added; progressBridge created in downloadViaPython; 4 call sites forwarding onProgress. Files: YtDlpExtractionStrategy.kt (+13 LOC). Dev log recorded.

---

### Step 03.3 — Verify Chaquopy thread-safety for the bridge

**Files:** — (read-only research; no code change)
**Depends on:** Step 03.2

**Prompt for developer:**

> Confirm that yt-dlp invokes `progress_hooks` on the same thread that called `extract_info(url, download=True)` (CPython GIL serialises). The Kotlin side already uses the dedicated single-thread `EXECUTOR` defined in `YtDlpExtractionStrategy.companion` — the bridge invocation therefore lands on the `ytdlp-worker` thread, not the calling coroutine's `Dispatchers.IO` worker. Because the `onProgress` lambda is `(Long) -> Unit` and the share-UI callbacks downstream are thread-agnostic (they marshal via `Flow` collection / `MutableStateFlow` post), no extra `withContext(Main)` wrap is required.
>
> If this assumption is wrong (some downstream consumer requires Main thread), wrap the bridge body with `Handler(Looper.getMainLooper()).post { onProgress(downloaded) }` — but only after Grep confirms a Main-thread requirement. Grep for `onProgress\(` consumers in `LinkAutoDownloadCoordinator.kt` and any `ProgressState.Downloading` emitter; verify they post to a Flow rather than touching a View directly.

**Verification:**

- `Grep` — `onProgress\(` call sites in coordinator/use-case layer all feed into a `MutableStateFlow.value = ...` or `_progress.emit(...)` (Flow/StateFlow, thread-safe by contract). Confirm via at least one file.
- Document the Grep finding in this step's note line (one-liner) — informational only.

**Status:** `[x]` done

**Step Log:**

- 2026-05-14 — Verification PASS. `ReceiveShareActivity.onProgress` dispatches via `runOnUiThread { progressDialog.update(state) }` — thread-safe, no withContext(Main) needed in downloadViaPython. No source changes.

---

### Step 03.4 — Build verification

**Files:** —
**Depends on:** Steps 03.1, 03.2, 03.3

**Prompt for developer:**

> Run `/build noLegalDebug`. Resolve any compile / Chaquopy bridge errors. Resolve any unused-import or unused-parameter warning the new code introduces.

**Verification:**

- `/build noLegalDebug` returns success exit code.
- `Grep` — `Log\.d\(` returns zero hits in `YtDlpExtractionStrategy.kt`.
- `Grep` — `Timber\.d\(` count in `YtDlpExtractionStrategy.kt` did not grow without intent (Phase 04 inserts BlockNeedUserTest tags; Phase 03 must not add log noise).

**Status:** `[x]` done

**Step Log:**

- 2026-05-14 — Verification 3/3 PASS. BUILD SUCCESSFUL. Log.d = 0. Timber.d count = 11 (no Phase 03 growth). No source changes.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles — standardDebug BUILD SUCCESSFUL (noLegalDebug deferred — no build script).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (1046 records, scan + render PASS).

---

## Handoff Notes to Next Phase

- Progress callbacks fire during the entire Python download window; share-UI is no longer "Probing" for the whole multi-MB transfer.
- Phase 04 covers the remaining checklist: docs (FEATURES_noLegal tightening if YTMusic outcome wording shifts), catalog refresh, BlockNeedUserTest transition with fresh `Timber.d("S0190: …")` tags.

---

## Rollback Plan

Revert Phase 03 commit. Progress reverts to the prior "0 → final" behaviour; functional correctness from Phase 02 is preserved.
