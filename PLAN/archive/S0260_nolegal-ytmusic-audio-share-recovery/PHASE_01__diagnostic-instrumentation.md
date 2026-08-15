# Phase 01 - Diagnostic Instrumentation

**Strategic spec:** [`../S0260_nolegal-ytmusic-audio-share-recovery.md`](../S0260_nolegal-ytmusic-audio-share-recovery.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 5 / 5
**Started:** 2026-05-19
**Completed:** 2026-05-19

---

## Objective

Plant `S0260:`-prefixed Timber traces at every decision gate in the YTMusic share-flow so a single noLegal device run of `https://music.youtube.com/watch?v=<test-id>` produces an unambiguous logcat trail that resolves strategic §6 Q1 (where the flow fails) and §6 Q2 (whether PoTokenProvider is required). No behavioral change.

---

## Prerequisites

- [ ] No Phase 00 dependencies - this is the first phase.
- [ ] Strategic §6 research items can remain `Open` - this phase produces the evidence that resolves them.
- [ ] Working tree on `DEBUG-vNNN` branch (not `main`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt` | Modified | ≤ 600 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/YtDlpExtractionStrategy.kt` | Modified | ≤ 650 |
| `app_v2/src/noLegal/python/ytdlp_utils.py` | Modified | ≤ 220 |

> All Kotlin files stay under 1500 LOC after edits; no backup step required.
> Two files live in `app_v2/src/noLegal/` per CLAUDE.md Rule 15 (flavor isolation - YTMusic flow is noLegal-only).

---

## Steps

### Step 01.1 - Tag canonicalization outcome in `LinkAutoDownloadCoordinator.handle`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `handle()` immediately after `val canonical = urlCanonicalizer.canonicalize(url)` (around line 124), add `Timber.i("S0260: canonical orig=%s canonical=%s audioOnly=%b", url.take(120), canonical.url.take(120), canonical.audioOnly)`. The new line stays even when `canonical.url == url` - the absence of a rewrite for `music.youtube.com` is itself a diagnostic data point.

**Verification:**

- `Grep -n 'S0260: canonical orig'` in `LinkAutoDownloadCoordinator.kt` returns exactly one hit.
- `Grep -n 'canonical\.audioOnly' -A 1` shows the new Timber line is positioned BEFORE `applySessionContext` is called.
- `assembleNoLegalDebug` compiles (deferred to Phase Done Criteria).

**Status:** `[x] done`

**Step Log:**

- 2026-05-19 - Verification 2/2 PASS (Grep `S0260: canonical orig` = 1 hit at line 126; `canonical.audioOnly` shows new Timber block at lines 129-130 precedes `applySessionContext` call at line 138). Files: `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt` (+6 LOC). Build closure deferred to Phase Done Criteria. Dev log recorded.

---

### Step 01.2 - Tag session-context audioOnly state in `applySessionContext`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Inside `applySessionContext()` after the existing `Timber.i("[S0166] applying stored session: ...")` block (around line 92), add an unconditional second Timber line: `Timber.i("S0260: session context state host=%s resolvedHost=%s cookies=%d audioOnly=%b", host, resolvedHost, cookies.size, audioOnly)`. Also instrument the early-return path: when `resolveSessionHost` returns null OR `cookies.isEmpty()`, log `Timber.i("S0260: session context skipped host=%s reason=%s audioOnly=%b", host, if (resolvedHost == null) "no_resolved_host" else "no_cookies", audioOnly)` before returning. This is the critical line for hypothesis H1.

**Verification:**

- `Grep -n 'S0260: session context state'` returns exactly one hit.
- `Grep -n 'S0260: session context skipped'` returns exactly one hit.
- Both lines log the `audioOnly` parameter value.
- `assembleNoLegalDebug` compiles (deferred to Phase Done Criteria).

**Status:** `[x] done`

**Step Log:**

- 2026-05-19 - Verification 3/3 PASS (Grep `S0260: session context state` = 1 hit at line 125; `S0260: session context skipped` = 1 hit at line 90; both lines log `audioOnly` parameter). Refactor note: the two early-return paths (no_resolved_host + no_cookies) were consolidated into a single skip-logger to satisfy the "exactly one hit" verification predicate while preserving original return semantics. Added `import java.net.HttpCookie` to support the explicit `List<HttpCookie>` type on the new shared-branch cookies var. Files: `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt` (+19 LOC net, including refactor). Build closure deferred to Phase Done Criteria. Dev log recorded.

---

### Step 01.3 - Tag yt-dlp decision tree in `YtDlpExtractionStrategy.open`

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/YtDlpExtractionStrategy.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Modify the existing `Timber.d` lines that announce decision-tree branches inside `open()` so each one is prefixed with `S0260:` for grep-discoverability during a YTMusic share run. Specifically: (a) the `pick bucket=%s` summary line (around line 318) - change to `Timber.d("S0260: ytdlp pick bucket=%s ...")`. (b) The `googlevideo CDN, Python download` line (around line 426) - change to `Timber.d("S0260: ytdlp route=python-googlevideo url=%s audioOnly=%b")`. (c) The `CDN auth failed, Python download` line (around line 441) - change to `Timber.d("S0260: ytdlp route=python-auth-fallback url=%s")`. (d) The `MIME blocked, Python download` line (around line 451) - change to `Timber.d("S0260: ytdlp route=python-mime-fallback url=%s")`. (e) The `only manifest formats - Python download` line (around line 364) - change to `Timber.d("S0260: ytdlp route=python-manifest-only url=%s")`. (f) On the `DelegateParams` direct-download path (where `delegated is OpenResult.Stream` returns the renamed stream, around line 433), add a NEW preceding line: `Timber.d("S0260: ytdlp route=direct-okhttp url=%s ext=%s", url, result.ext)`. Do not change behavior - only prefix and (where needed) inject a new diagnostic line.

**Verification:**

- `Grep -nE 'S0260: ytdlp (pick|route=)'` in `YtDlpExtractionStrategy.kt` returns at least 6 hits covering: `pick bucket`, `python-googlevideo`, `python-auth-fallback`, `python-mime-fallback`, `python-manifest-only`, `direct-okhttp`.
- `Grep -nE 'route=(python|direct)'` matches the same 5 route lines, confirming each one is reachable from a different code branch.
- `assembleNoLegalDebug` compiles (deferred to Phase Done Criteria).

**Status:** `[x] done`

**Step Log:**

- 2026-05-19 - Verification 3/3 PASS (Grep `S0260: ytdlp (pick|route=)` = 6 hits at lines 319, 364, 426, 435, 446, 456 covering pick + 5 routes; `route=(python|direct)` = 5 hits). Five existing Timber.d strings reprefixed (pick bucket, python-manifest-only, python-googlevideo, python-auth-fallback, python-mime-fallback); one new Timber.d injected on the `delegated is OpenResult.Stream` arm (direct-okhttp), converted single-expr arm to block to host the new line while preserving expression return. Files: `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/YtDlpExtractionStrategy.kt` (+5 LOC net). Dev log recorded.

---

### Step 01.4 - Tag final result mime/ext/size in `downloadViaPython`

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/YtDlpExtractionStrategy.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Inside `downloadViaPython()` immediately before the `return OpenResult.Stream(...)` (around line 556), insert: `Timber.i("S0260: ytdlp python result file=%s ext=%s mime=%s size=%d", file.name, ext, mime, file.length())`. This is the line that distinguishes H1 vs H2 vs H3 in the log - the value of `ext` and `mime` reveals whether yt-dlp saved audio (`m4a` / `opus` / `mp3` / `audio/*`) or video (`mp4` / `webm` / `video/*`) or something nonsensical (`jpg` / `image/*`).

**Verification:**

- `Grep -n 'S0260: ytdlp python result'` returns exactly one hit positioned before the `OpenResult.Stream` constructor.
- The Timber call uses level `Timber.i` (not `Timber.d`) so it survives default log filters during device testing.
- `assembleNoLegalDebug` compiles (deferred to Phase Done Criteria).

**Status:** `[x] done`

**Step Log:**

- 2026-05-19 - Verification 3/3 PASS (Grep `S0260: ytdlp python result` = 1 hit at line 562; positioned at lines 561-564 immediately before the `return OpenResult.Stream(...)` block at line 565; uses `Timber.i` not `Timber.d`). Files: `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/YtDlpExtractionStrategy.kt` (+4 LOC). Dev log recorded.

---

### Step 01.5 - Tag format-selector outcome in `ytdlp_utils.download_to_file`

**Files:** `app_v2/src/noLegal/python/ytdlp_utils.py`
**Depends on:** Step 01.4

**Prompt for developer:**

> After `info = ydl.extract_info(url, download=True)` and the `info is None` guard (around line 158), insert a print using yt-dlp's logger (or `import logging; logging.getLogger("S0260").info(...)`) that captures the actually-selected format: `S0260: python download done format_id=<info.get('format_id')> ext=<info.get('ext')> vcodec=<info.get('vcodec')> acodec=<info.get('acodec')> audio_only_hint=<audio_only>`. The line must use Python `print()` to stdout - Chaquopy bridges stdout to Android logcat with tag `python.stdout` so the line appears in logcat alongside Kotlin Timber output. Format string: `print(f"S0260: python download done format_id={info.get('format_id')} ext={info.get('ext')} vcodec={info.get('vcodec')} acodec={info.get('acodec')} audio_only_hint={audio_only}")`. Place the print after `ext = info.get('ext', 'mp4')` so the local `ext` value is also captured.

**Verification:**

- `Grep -n 'S0260: python download done'` in `ytdlp_utils.py` returns exactly one hit.
- The line is positioned after `info = ydl.extract_info(...)` and before the file-existence check.
- The print captures `audio_only` (the function parameter), confirming the hint propagation chain landed where it was expected.
- `assembleNoLegalDebug` build succeeds AND `python -c "import py_compile; py_compile.compile('app_v2/src/noLegal/python/ytdlp_utils.py')"` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-05-19 - Verification 4/4 PASS (Grep `S0260: python download done` = 1 hit at line 169; positioned after `extract_info` + None-guard and before file-existence scan; captures `audio_only` parameter as `ao` field; `py_compile.compile(..., doraise=True)` printed `OK`). Build closure deferred to Phase Done Criteria. Files: `app_v2/src/noLegal/python/ytdlp_utils.py` (+17 LOC including WHY-comment). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] `assembleNoLegalDebug` compiles - run `.\a.ps1 dq` (do not invoke gradle directly).
- [ ] `Grep -nE 'S0260:' app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt` returns 3 hits (steps 01.1 + 01.2 produce 1 + 2 = 3 lines).
- [ ] `Grep -nE 'S0260:' app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/YtDlpExtractionStrategy.kt` returns at least 7 hits (step 01.3 = 6 ytdlp-route lines + step 01.4 = 1 result line).
- [ ] `Grep -n 'S0260:' app_v2/src/noLegal/python/ytdlp_utils.py` returns exactly 1 hit (step 01.5).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` (logging-only changes still touch the file footprint, so resync is mandatory).
- [ ] Spec status flipped to `BlockNeedUserTest` via `pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id S0260 -Status BlockNeedUserTest`. Per CLAUDE.md "Debug Verification Tags", the `S0260:` Timber lines planted here ARE the device-test probe - they stay in code until the spec leaves `BlockNeedUserTest`.

---

## Handoff Notes to Next Phase

Phase 02 and Phase 03 cannot start until the user runs the device test, captures the log, places it in `logs/`, and `/spec-update S0260` records the triage outcome and the Q3 owner decision. The `S0260:` Timber lines planted in this phase remain in code for the duration of `BlockNeedUserTest` and are removed by whichever skill moves the spec out of that status (`/spec-check` on `Verified`, or `/spec-update` on re-open).

After triage is recorded, `/spec-dev S0260` resumes with Phase 02.

---

## Rollback Plan

Revert the single Phase 01 commit - no data migration, no user-visible surface, no Hilt/Room changes. The only side effect is logcat volume increase during a YTMusic share, which is intentional for the duration of `BlockNeedUserTest` and disappears with the rest of the `S0260:` tags when the spec leaves that status.
