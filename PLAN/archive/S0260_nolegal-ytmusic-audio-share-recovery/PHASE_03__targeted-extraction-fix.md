# Phase 03 - Targeted Extraction Fix (branch selected by triage)

**Strategic spec:** [`../S0260_nolegal-ytmusic-audio-share-recovery.md`](../S0260_nolegal-ytmusic-audio-share-recovery.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⛔ Blocked
**Depends on:** Phase 01 + INDEX Pre-Implementation Blockers (triage result must be recorded)
**Blocks:** Phase 04
**Steps done:** 0 / 4
**Started:** -
**Completed:** -

---

## Objective

Apply the single targeted fix that satisfies strategic §11 acceptance criterion #1 (`music.youtube.com/watch?v=<id>` produces a playable audio file in Downloads). Four mutually-exclusive branches map to the four hypotheses (H1 / H2 / H3 / PoToken). Triage from Phase 01 device-log selects exactly one branch via `/spec-update S0260`. The skill `/spec-dev` HALTS at the top of this phase if the selection is unset.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done AND device-log captured.
- [ ] `/spec-update S0260` has recorded `Phase03Branch=A` or `=B` or `=C` or `=D-out-of-scope` in this file (the developer running this phase reads the marker before proceeding). Without an explicit branch tag, `/spec-dev` must stop and surface `S0260: Phase 03 branch unselected - run /spec-update`.
- [ ] Working tree clean.

---

## Branch Selection Marker

**Selected branch:** _<unset - to be filled by `/spec-update` after triage>_

Valid values:
- `A` - hypothesis H1 (session-context `audioOnly` lost when cookies absent).
- `B` - hypothesis H2 (Python format selector picks video when only manifest formats available).
- `C` - hypothesis H3 (`NewPipeSiteExtractionStrategy` fallthrough returns non-audio artifact).
- `D-out-of-scope` - PoToken truly required; close this phase as `⏭️ Skipped` and create a new spec for the `PoTokenProvider` work (ADR-2).

Only the step block matching the selected branch executes. All other branch blocks remain in this file as historical record - they are not deleted on phase completion.

---

## Branch A - H1: Propagate `audioOnly` hint when cookies are absent

**Hypothesis evidence:** Phase 01 log shows `S0260: session context skipped reason=no_cookies audioOnly=true` followed by `S0260: ytdlp route=python-googlevideo audioOnly=false`. The hint was set at canonicalization but never reached `LinkDownloadSessionContext` because `applySessionContext()` returned early when no cookies were found, dropping the audioOnly flag.

### Files Touched (branch A)

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/cookie/LinkDownloadSessionContext.kt` | Modified | ≤ 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt` | Modified | ≤ 650 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinatorTest.kt` | Modified | ≤ 350 |

### Step 03.A.1 - Decouple `audioOnly` from cookie presence in `LinkDownloadSessionContext`

**Prompt for developer:**

> Add a separate function `fun setAudioOnly(host: String, audioOnly: Boolean)` on `LinkDownloadSessionContext` that updates only the `audioOnly` flag, creating an `Active` entry with empty `cookies` and `null` userAgent when no entry exists. This allows the coordinator to push the hint even when there are no cookies. Update `audioOnlyFor(requestHost)` to remain working - the hostMatches check still applies. Existing `set(host, cookies, ua, audioOnly)` continues to set everything atomically (used by the auth-resolved path).

**Verification:**

- `Grep -n 'fun setAudioOnly\(' LinkDownloadSessionContext.kt` returns one hit.
- Existing test `LinkAutoDownloadCoordinatorTest` still passes.
- `assembleNoLegalDebug` compiles.

### Step 03.A.2 - Call `setAudioOnly` unconditionally when `canonical.audioOnly == true`

**Prompt for developer:**

> In `LinkAutoDownloadCoordinator.handle()` after the existing `applySessionContext(host, accountId, canonical.audioOnly)` call: if `canonical.audioOnly == true` AND `appliedSessionHost == null` (cookies were missing), explicitly call `sessionContext.setAudioOnly(host, true)` so `YtDlpExtractionStrategy` sees the hint regardless of cookie state. Log `Timber.i("S0260: hint propagated without session host=%s audioOnly=true", host)`. Update the existing `S0260: session context skipped` line to also note `audioOnlyForcedThrough=true` when this branch fires.

**Verification:**

- `Grep -n 'sessionContext\.setAudioOnly' LinkAutoDownloadCoordinator.kt` returns one hit.
- `Grep -n 'S0260: hint propagated without session'` returns one hit.
- `assembleNoLegalDebug` compiles.

### Step 03.A.3 - Test coverage for hint-without-cookies path

**Prompt for developer:**

> Add a new test in `LinkAutoDownloadCoordinatorTest`: `handle_youTubeMusic_propagatesAudioOnly_whenNoCookies()`. Given URL `https://music.youtube.com/watch?v=test123`, `cookieStore.loadForAccount(any, any)` returns empty list, `sessionContext.setAudioOnly` is captured and asserted called with `("youtube.com" or whatever resolveSessionHost falls back to)` and `true`. Use the existing MockK setup pattern from the file.

**Verification:**

- `Grep -n 'handle_youTubeMusic_propagatesAudioOnly_whenNoCookies' LinkAutoDownloadCoordinatorTest.kt` returns one hit.
- The targeted test passes - run `.\gradlew testStandardDebugUnitTest --tests "*handle_youTubeMusic_propagatesAudioOnly_whenNoCookies*"` (per memory `feedback_build_pre_existing_test_failures.md` - use per-test target, not full suite).
- The XML report under `app_v2/build/test-results/testStandardDebugUnitTest/` shows the test as `success`.

### Step 03.A.4 - Catalog sync for `LinkDownloadSessionContext` API surface change

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Verify the `LinkDownloadSessionContext` row in `dev/CATALOG/app_v2.md` reflects the new `setAudioOnly` method. No `set.ps1` call needed - method-level scanning is automatic.

**Verification:**

- `Grep -n 'setAudioOnly' dev/CATALOG/app_v2.jsonl` returns one hit (in the `LinkDownloadSessionContext` record).

---

## Branch B - H2: Harden Python format selector

**Hypothesis evidence:** Phase 01 log shows `S0260: session context state audioOnly=true` (hint propagated correctly) but `S0260: python download done format_id=<video-format>` reveals that yt-dlp returned only manifest formats and the Python `bestaudio[ext=m4a]/...` cascade did not match - falling through to the video cascade or to nothing.

### Files Touched (branch B)

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/noLegal/python/ytdlp_utils.py` | Modified | ≤ 250 |

### Step 03.B.1 - Extend audio-only format cascade in `ytdlp_utils.py`

**Prompt for developer:**

> In `download_to_file()`, replace the audio-only branch (`'bestaudio[ext=m4a]/bestaudio[ext=opus]/bestaudio[ext=mp3]/bestaudio'`) with a wider cascade that includes manifest-audio formats and explicit format IDs: `'bestaudio[ext=m4a]/bestaudio[ext=opus]/bestaudio[ext=mp3]/140/251/250/249/bestaudio'`. Format IDs 140 / 251 / 250 / 249 are YouTube's standard audio-only m4a / opus tiers - the original cascade only covered extension-based selectors. Add `'allow_unplayable_formats': False` is already set; also add `'youtube_include_dash_manifest': True` and `'youtube_include_hls_manifest': True` so audio-only DASH/HLS tiers are visible to the selector.

**Verification:**

- `Grep -n 'bestaudio\[ext=m4a\].+/140/251/250/249/' ytdlp_utils.py` returns one hit.
- `Grep -n 'youtube_include_dash_manifest' ytdlp_utils.py` returns one hit.
- `python -c "import py_compile; py_compile.compile('app_v2/src/noLegal/python/ytdlp_utils.py')"` exits 0.
- `assembleNoLegalDebug` compiles.

### Step 03.B.2 - Surface a no-audio-format diagnostic

**Prompt for developer:**

> Wrap the `ydl.extract_info(url, download=True)` call inside a try block. On `yt_dlp.utils.DownloadError` whose message contains `Requested format is not available` AND `audio_only=True` - re-raise with a wrapped message `f"S0260: ytmusic_no_audio_format_available original={e}"` so the Kotlin side can pattern-match this case in the existing catch block (`YtDlpExtractionStrategy.kt` line 397-413). Add an `ytmusic_no_audio_format_available` branch in that Kotlin catch that returns `OpenResult.NotFound("ytmusic_no_audio_format_available")` (not `Error`) so the registry falls through cleanly to NewPipe-site or to the Phase 02 guard.

**Verification:**

- `Grep -n 'ytmusic_no_audio_format_available' ytdlp_utils.py` returns at least one hit.
- `Grep -n 'ytmusic_no_audio_format_available' YtDlpExtractionStrategy.kt` returns at least one hit.
- `assembleNoLegalDebug` compiles.

### Step 03.B.3 - Test: the Python script compiles and the new format string is parseable

**Prompt for developer:**

> Add a JUnit test `YtDlpFormatStringSanityTest` (Kotlin under `app_v2/src/test/`) that reads `app_v2/src/noLegal/python/ytdlp_utils.py` as text and asserts the audio-only format cascade contains all four numeric IDs (140, 251, 250, 249). This is a structural check - we cannot run yt-dlp inside JVM unit tests.

**Verification:**

- `Grep -n 'class YtDlpFormatStringSanityTest' app_v2/src/test/java` returns one hit.
- The test passes - `.\gradlew testStandardDebugUnitTest --tests "*YtDlpFormatStringSanityTest*"`.

### Step 03.B.4 - Catalog sync

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

**Verification:**

- `scripts/catalog_sync.ps1` exits 0.

---

## Branch C - H3: Reject thumbnail/preview artifacts at the NewPipe-site strategy

**Hypothesis evidence:** Phase 01 log shows yt-dlp returns `OpenResult.NotFound("ytdlp_not_applicable")` or similar, registry falls through to `NewPipeSiteExtractionStrategy`, which returns a stream whose final `S0260: ytdlp python result` (no, that line is yt-dlp-side) - in this branch the smoking gun is in `LinkDownloadWriter` or in NewPipe-site's own logging where the saved file has `ext=jpg` / `mime=image/*`.

### Files Touched (branch C)

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/NewPipeSiteExtractionStrategy.kt` | Modified | ≤ 280 |

### Step 03.C.1 - Add audio-only gate to NewPipe-site strategy

**Prompt for developer:**

> In `NewPipeSiteExtractionStrategy.open()`, read `sessionContext.audioOnlyFor(targetHost)` early. If `audioOnly == true`: the strategy must either (a) return an audio-only stream URL extracted from NewPipe's `StreamInfo.audioStreams` (lowest bitrate >= 96 kbps; m4a preferred), or (b) return `OpenResult.NotFound("ytmusic_newpipe_no_audio_stream")`. It must NEVER return a thumbnail URL, preview URL, or video-only stream URL when `audioOnly == true`. Add `Timber.i("S0260: newpipe-site audioOnlyMode audioStreams=%d picked=%s", audioStreams.size, pickedUrl?.take(60))` for diagnostic continuity with Phase 01.

**Verification:**

- `Grep -n 'audioOnlyFor\(' NewPipeSiteExtractionStrategy.kt` returns one hit.
- `Grep -n 'ytmusic_newpipe_no_audio_stream' NewPipeSiteExtractionStrategy.kt` returns one hit.
- `Grep -n 'S0260: newpipe-site audioOnlyMode' NewPipeSiteExtractionStrategy.kt` returns one hit.
- `assembleNoLegalDebug` compiles.

### Step 03.C.2 - Test: NewPipe-site audio-only behavior

**Prompt for developer:**

> Add unit test `NewPipeSiteExtractionStrategyAudioOnlyTest` under `app_v2/src/test/` (or a noLegal-specific test source set if one exists - check `app_v2/src/noLegalTest/` first). Mock `sessionContext.audioOnlyFor` to return true; mock a `StreamInfo`-equivalent fixture with both video and audio streams. Assert: the returned `OpenResult.Stream` references an audio stream URL, NOT the thumbnail or any video-only URL. Second test: empty audioStreams → `OpenResult.NotFound`.

**Verification:**

- `Grep -n 'class NewPipeSiteExtractionStrategyAudioOnlyTest'` returns one hit.
- The test passes via `.\gradlew testNoLegalDebugUnitTest --tests "*NewPipeSiteExtractionStrategyAudioOnlyTest*"` (noLegal flavor explicitly - the class is flavor-isolated under `src/noLegal/java/`).

### Step 03.C.3 - Catalog sync

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

**Verification:**

- `scripts/catalog_sync.ps1` exits 0.

### Step 03.C.4 - Update no-flavors hint for the audio-only-gated NewPipe-site code path

**Prompt for developer:**

> Since `NewPipeSiteExtractionStrategy` already lives under `src/noLegal/java/`, the catalog already records its flavor scope. No `-NoFlavors` change needed. Verify via `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -ClassMatches "NewPipeSiteExtractionStrategy"` that the path column shows `src/noLegal/java/` and not `src/main/java/`.

**Verification:**

- `query.ps1` row for `NewPipeSiteExtractionStrategy` shows path under `src/noLegal/java/`.

---

## Branch D-out-of-scope - PoToken truly required

**Hypothesis evidence:** Phase 01 log shows yt-dlp fails on every `player_client` attempt with PoToken-related errors AND NewPipe-site also fails AND no other strategy can produce audio. ADR-2 in strategic spec keeps `PoTokenProvider` work out of the first S0260 iteration.

### Action

Mark this phase `⏭️ Skipped` in INDEX. Open a new strategic spec via `pwsh -NoProfile -File scripts/spec_catalog/next-id.ps1` and write the PoTokenProvider strategic spec. Reference S0260 §6 Q2 in the new spec's §10. Do NOT modify any source files in this phase.

### Verification

- No code files changed.
- New spec exists at `PLAN/S<new-id>_nolegal-newpipe-potoken-provider.md`.
- `S0260` strategic §10 (Связи) gains a line referencing the new spec.

---

## Phase Done Criteria

- [ ] Exactly one branch (A / B / C / D-out-of-scope) was executed; the marker at the top of this file names that branch.
- [ ] All steps of the selected branch are `[x] done`.
- [ ] `assembleNoLegalDebug` compiles.
- [ ] Per-test runs from the selected branch pass (XML report under `app_v2/build/test-results/`).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

Phase 04 confirms criteria #3 (Shorts regression) and #4 (regular watch regression) are green and adds the regression net for the YTMusic path itself. Phase 04 also exercises the Phase 02 guard with a synthetic test that forces a non-audio MIME and asserts the guard rejects it.

---

## Rollback Plan

Revert the Phase 03 commit. The branch-A / branch-B / branch-C changes are independent of Phase 02 (the guard) - rolling back Phase 03 alone restores the prior failure mode but the guard from Phase 02 still prevents JPEG/preview artifacts.
