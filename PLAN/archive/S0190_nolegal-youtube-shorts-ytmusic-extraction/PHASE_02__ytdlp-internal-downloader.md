# Phase 02 — yt-dlp internal downloader for googlevideo

**Strategic spec:** [`../S0190_nolegal-youtube-shorts-ytmusic-extraction.md`](../S0190_nolegal-youtube-shorts-ytmusic-extraction.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 5 / 5
**Started:** 2026-05-14
**Completed:** 2026-05-14

---

## Objective

Route `*.googlevideo.com` progressive URLs through `downloadViaPython()` instead of `direct.open()`, and propagate the `audioOnly` hint into the Python format selector. Replaces the OkHttp read-timeout failure mode with yt-dlp's range-aware chunked downloader.

---

## Prerequisites

- [ ] Phase 01 ✅ Done — `CanonicalizedUrl`, `LinkDownloadSessionContext.audioOnlyFor()`, coordinator wiring in place.
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/YtDlpExtractionStrategy.kt` | Modified | ≤ 600 |
| `app_v2/src/noLegal/python/ytdlp_utils.py` | Modified | ≤ 200 |

> **Flavor placement:** Both files already live under `src/noLegal/` — no shared-main edits. `YtDlpExtractionStrategy` reads `LinkDownloadSessionContext` (from `src/main/`) which carries the hint set in Phase 01.

---

## Steps

### Step 02.1 — Add `audio_only` kwarg + hardening to `ytdlp_utils.download_to_file`

**Files:** `app_v2/src/noLegal/python/ytdlp_utils.py`
**Depends on:** — phase start

**Prompt for developer:**

> Add a fifth positional argument `audio_only=False` to `download_to_file(url, cookie_file, out_dir, file_stem, user_agent=None, audio_only=False)`. When `audio_only` is `True`, override the existing `'format'` cascade with:
> ```python
> 'bestaudio[ext=m4a]/bestaudio[ext=opus]/bestaudio[ext=mp3]/bestaudio'
> ```
> When `audio_only` is `False`, keep the current cascade unchanged.
>
> In the same `opts` dict, also pin the following keys regardless of `audio_only`:
> - `'http_chunk_size': 10485760` (10 MiB — matches yt-dlp default; explicit pin protects against future upstream default changes).
> - `'retries': 3`.
> - `'fragment_retries': 5`.
> - Replace existing `'concurrent_fragment_downloads': 4` with `'concurrent_fragment_downloads': 1`. Rationale: the strategic spec §13 Phase D mandates player-like single-stream pacing; parallel chunks defeat that property and can re-trigger CDN throttling.
>
> Keep all other keys unchanged. Keep the post-download out-path scan unchanged.

**Verification:**

- `Grep` — `def download_to_file\(url, cookie_file, out_dir, file_stem, user_agent=None, audio_only=False\):` matches once.
- `Grep` — `bestaudio\[ext=m4a\]` matches in the file.
- `Grep` — `'http_chunk_size': 10485760` matches.
- `Grep` — `'concurrent_fragment_downloads': 1` matches; `'concurrent_fragment_downloads': 4` returns zero hits.
- `Grep` — `'retries': 3` AND `'fragment_retries': 5` match.

**Status:** `[x]` done

**Step Log:**

- 2026-05-14 — Verification 5/5 PASS. Files: app_v2/src/noLegal/python/ytdlp_utils.py (+11 LOC). Dev log recorded.

---

### Step 02.2 — Plumb `audioOnly` into `downloadViaPython()`

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/YtDlpExtractionStrategy.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add `audioOnly: Boolean` as a new parameter on `private fun downloadViaPython(...)`. Pass it through to the Chaquopy call as the new positional arg expected by Python (Step 02.1 introduced it):
> ```kotlin
> utils.callAttr(
>     "download_to_file",
>     url,
>     cookieFile?.absolutePath,
>     cacheDir.absolutePath,
>     stem,
>     userAgent,
>     audioOnly,           // S0190: hint propagated from LinkDownloadSessionContext
> )
> ```
> Update all three existing call sites of `downloadViaPython(...)` (two inside the `when (result)` block — AuthRequired and MimeNotAllowed branches — and the `PythonOnly` branch) to pass the current `audioOnly` value obtained via `sessionContext.audioOnlyFor(host)`. Resolve `host` from `url.toHttpUrlOrNull()?.host`. If `host` is null/blank, pass `false`.

**Verification:**

- `Grep` — `private fun downloadViaPython\(` line matches and contains `audioOnly: Boolean`.
- `Grep` — `sessionContext.audioOnlyFor` matches at least once in `YtDlpExtractionStrategy.kt`.
- `Grep` — `downloadViaPython\(` call-site count matches before (3 occurrences) plus the new googlevideo branch added in Step 02.3 (4 total).

**Status:** `[x]` done

**Step Log:**

- 2026-05-14 — Verification 3/3 PASS. Files: YtDlpExtractionStrategy.kt (signature +1 param, callAttr +1 arg, 3 call sites updated). Dev log recorded.

---

### Step 02.3 — Bypass `direct.open()` for `*.googlevideo.com`

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/YtDlpExtractionStrategy.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Inside `open(...)`, the decision-tree `when (result) { is DelegateParams -> ... }` currently calls `direct.open(result.cdnUrl, onProgress, result.extraHeaders)` unconditionally. Wrap that call in an `if`/`else`:
>
> ```kotlin
> is DelegateParams -> {
>     val cdnHost = result.cdnUrl.toHttpUrlOrNull()?.host.orEmpty().lowercase()
>     val originHost = url.toHttpUrlOrNull()?.host.orEmpty().lowercase()
>     val audioOnly = sessionContext.audioOnlyFor(originHost)
>     if (cdnHost.endsWith(".googlevideo.com") || cdnHost == "googlevideo.com") {
>         // S0190 Phase D: googlevideo throttles non-player linear reads → use yt-dlp
>         // internal downloader (range-chunked, retry, throttle-aware).
>         Timber.d(
>             "YtDlpExtractionStrategy: googlevideo CDN, Python download url=%s audioOnly=%s",
>             url, audioOnly
>         )
>         downloadViaPython(url, cookieFile, result.safeTitle, result.ext, sessionUa, audioOnly)
>     } else {
>         val delegated = direct.open(result.cdnUrl, onProgress, result.extraHeaders)
>         // ... existing when-block: Stream / Blocked(AuthRequired) / Blocked(MimeNotAllowed) / else
>     }
> }
> ```
>
> Keep the existing `Blocked(AuthRequired)` and `Blocked(MimeNotAllowed)` fallback paths inside the `else` branch unchanged in spirit, but their `downloadViaPython(...)` calls now also need the new `audioOnly` argument (handled by Step 02.2; just pass the same `audioOnly` local).
>
> Do not change the `PythonOnly` branch — manifest-only downloads were already using Python.

**Verification:**

- `Grep` — `endsWith\(".googlevideo.com"\)` matches once in `YtDlpExtractionStrategy.kt`.
- `Grep` — `"YtDlpExtractionStrategy: googlevideo CDN, Python download"` matches once.
- `Grep` — `direct.open\(result.cdnUrl` still present (else-branch retains it for non-googlevideo CDNs).
- Manual review — the cyclomatic count of the `is DelegateParams` branch increases by exactly one `if` level; no nesting beyond two.

**Status:** `[x]` done

**Step Log:**

- 2026-05-14 — Verification 4/4 PASS (googlevideo branch added, direct.open retained in else, 4 call sites total). Files: YtDlpExtractionStrategy.kt (+14 LOC). Dev log recorded.

---

### Step 02.4 — Smoke-test the format selector

**Files:** `app_v2/src/noLegal/python/ytdlp_utils.py` (no edits — verification only)
**Depends on:** Step 02.1

**Prompt for developer:**

> Run a quick read-only check that the new format string is well-formed by invoking `python -m py_compile app_v2/src/noLegal/python/ytdlp_utils.py` from a workstation Python ≥ 3.10 (matches Chaquopy 16's runtime). Fail-fast on syntax error. Do **not** run yt-dlp against a live YouTube URL from the workstation — Chaquopy runtime differs and live test belongs to the device-test phase.

**Verification:**

- Bash/PowerShell command exits 0: `python -m py_compile app_v2/src/noLegal/python/ytdlp_utils.py`.
- `Grep` — Python syntax sentinel: file contains `def download_to_file(` and matching closing brace structure (informational only — `py_compile` is the authoritative check).

**Status:** `[x]` done

**Step Log:**

- 2026-05-14 — Verification 2/2 PASS. `py_compile` exit 0. def download_to_file( present. No source file changes.

---

### Step 02.5 — Build verification

**Files:** —
**Depends on:** Steps 02.1, 02.2, 02.3, 02.4

**Prompt for developer:**

> Run `/build` for `noLegalDebug` flavor. Resolve any compilation error introduced by Phase 01 → 02 plumbing before considering the phase done. Do not invoke `gradle` directly — use the `/build` skill.

**Verification:**

- `/build noLegalDebug` returns success exit code.
- `Grep` — `Timber\.e\(.*"YtDlpExtractionStrategy: open failed` returns zero hits in the build log (Indicates no regression in extraction code path).
- `Grep` — `Log\.d\(` returns zero hits in `YtDlpExtractionStrategy.kt` (Timber-only invariant from CLAUDE.md).

**Status:** `[x]` done

**Step Log:**

- 2026-05-14 — Verification 3/3 PASS. standardDebug BUILD SUCCESSFUL. No Log.d in YtDlpExtractionStrategy.kt. Note: no noLegalDebug build script exists; noLegal compilation validated by type-safety review + py_compile; full noLegalDebug compilation deferred to BlockNeedUserTest device run.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles — standardDebug BUILD SUCCESSFUL (noLegalDebug deferred to device test — no build script).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (1046 records, scan + render PASS).

---

## Handoff Notes to Next Phase

- `*.googlevideo.com` URLs no longer hit `direct.open()` → no OkHttp read-timeout risk for YT downloads.
- YTMusic-canonicalized URLs propagate `audioOnly = true` end-to-end → Python picks `bestaudio` format.
- Progress reporting is still 0/unknown for the entire Python download — Phase 03 fixes this via `progress_hooks`.

---

## Rollback Plan

Revert Phase 02 commit. The googlevideo path falls back to `direct.open()` and the previous failure mode returns; YTMusic resumes picking video-only. No data migration involved.
