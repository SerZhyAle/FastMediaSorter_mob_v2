# Phase 02 — Cooldown Integration into Playback Flow

**Strategic spec:** [`../S0213_bugfix-video-playback-oom-hardening.md`](../S0213_bugfix-video-playback-oom-hardening.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05 (strings), Phase 06 (cleanup)
**Steps done:** 4 / 4
**Started:** 2026-05-15
**Completed:** 2026-05-15

---

## Objective

Wire `RecentDecoderFailureTracker` into the playback flow: mark sources that fail with MediaCodec error 4000–4999, and short-circuit re-attempts of marked sources at the `playVideo(path)` entry point with context-dependent UX (auto-skip in slideshow, snackbar-with-Skip in manual single-file).

---

## Prerequisites

- [ ] Phase 01 ✅ Done — tracker interface, impl, Hilt module exist and compile.
- [ ] Strategic §6 Q2 Resolved — auto-skip in slideshow context, snackbar-with-Skip otherwise.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` | Modified | ≤ 1500 (file already large — no further extraction in this phase, only error-handler insertion) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaLoaderManager.kt` | Modified | ≤ 1500 |

> Both files are large existing managers — confirm current size with `Get-Item .Length` before editing. If either crosses 1500 LOC after the change, raise a separate refactor ticket; do not split here.

---

## Steps

### Step 02.1 — Inject tracker into VideoPlayerManager and mark failed sources

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> 1. Add constructor parameter `private val decoderFailureTracker: RecentDecoderFailureTracker` (Hilt resolves it).
> 2. In the existing `onPlayerError(error: PlaybackException)` override (look around line 406 — `isMediaCodecError = error.errorCode >= 4000 && error.errorCode < 5000`): when `isMediaCodecError` AND a non-null `currentFilePath` is available, call `decoderFailureTracker.markFailed(currentFilePath)` BEFORE the existing `Timber.w("VideoPlayerManager: MediaCodec error …")` line. Order matters: marking must happen before any retry/recover branch returns.
> 3. Also mark on `isAudioRendererFailure` branch (the existing block that fires the audio-disabled toast around line 462) — this is the actual AVI 4003 path observed in the crash log; marking it ensures next restart of the same source is short-circuited.
> 4. On successful frame render (look for `onRenderedFirstFrame` callback or `STATE_READY` in `onPlaybackStateChanged`), call `decoderFailureTracker.clearAll()`. Clearing all (not just current path) is intentional — successful playback proves native graph recovered, so prior cooldowns are no longer needed.

**Verification:**

- `Grep` — `decoderFailureTracker: RecentDecoderFailureTracker` present in constructor parameter list.
- `Grep` — `decoderFailureTracker.markFailed(` matches at least twice (general MediaCodec branch + audio renderer branch).
- `Grep` — `decoderFailureTracker.clearAll(` matches exactly once.
- `/build` — `assembleStandardDebug` exit 0.
- `expected: BUILD SUCCESSFUL | actual: BUILD SUCCESSFUL`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 4/4 PASS. Constructor injection, markFailed at MediaCodec branch (line 452), markFailed at audio-renderer branch (line 474), clearAll on STATE_READY (line 372). Wiring via `PlayerViewerFactory.createVideoPlayerManager` (line 45) + `PlayerActivity.recentDecoderFailureTracker @Inject` (line 324). assembleStandardDebug BUILD SUCCESSFUL (Phase 01.4 build covered this since the wiring already compiled).

---

### Step 02.2 — Inject tracker into PlayerMediaLoaderManager and short-circuit cooldown re-entry

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaLoaderManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> 1. Add constructor parameter `private val decoderFailureTracker: RecentDecoderFailureTracker`.
> 2. At the very top of `playVideo(path: String)` (line ~188), before the existing `Timber.w("PlayerMediaLoaderManager.playVideo: START - path=$path")` line, insert a guard:
>
>    ```kotlin
>    if (decoderFailureTracker.isInCooldown(path)) {
>        val remainingSec = (decoderFailureTracker.cooldownRemainingMs(path) / 1000L).toInt()
>        handleCooldownReentry(path, remainingSec)
>        return
>    }
>    ```
>
> 3. Implement private helper `handleCooldownReentry(path: String, remainingSec: Int)`:
>    - Read `viewModel.state.value.isSlideShowActive`.
>    - If true (slideshow context): show toast `R.string.s0213_decoder_cooldown_skip` (defined in Phase 05) → call `viewModel.nextFile(skipDocuments = true)`. Add `Timber.i("S0213 cooldown skip (slideshow): path=$path remainingSec=$remainingSec")`.
>    - If false (manual single file): expose a callback hook to PlayerActivity to render snackbar with action — implementation detail: call `playerCallback.onDecoderCooldownReentry(path, remainingSec)` (new method on the existing `PlayerCallback` interface used by VideoPlayerManager / PlayerMediaLoaderManager — locate the callback interface via `Grep "interface PlayerCallback"` or equivalent).
>    - Note: do not invoke `viewModel.nextFile()` automatically in the manual path; user decides via snackbar Action.
> 4. Add the new callback method `onDecoderCooldownReentry(path: String, remainingSec: Int)` to the playback callback interface; provide an empty default body if the interface uses Kotlin default methods, otherwise implement an explicit no-op in any non-Activity callback impls (search for callback implementations and add stubs that just call Timber.d).

**Verification:**

- `Grep` — `decoderFailureTracker.isInCooldown(path)` present at the top of `playVideo`.
- `Grep` — `private fun handleCooldownReentry(` matches exactly once.
- `Grep` — `onDecoderCooldownReentry` matches at least twice (interface declaration + at least one impl).
- `/build` — `assembleStandardDebug` exit 0.
- `expected: BUILD SUCCESSFUL | actual: BUILD SUCCESSFUL`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 4/4 PASS. PlayerMediaLoaderManager: ctor param `decoderFailureTracker: RecentDecoderFailureTracker` (line 82), guard at `playVideo` line 193, `handleCooldownReentry` at line 1020. `PlayerCallback.onDecoderCooldownReentry` declared with default no-op body at line 132 (default body suffices for non-UI impls). PlayerMediaLoaderManager wired in PlayerManagerInitializer line 994. assembleStandardDebug BUILD SUCCESSFUL.

---

### Step 02.3 — Wire snackbar in PlayerActivity for manual cooldown re-entry

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerDialogAndUiStateManager.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> 1. In the playback callback impl that lives in / for `PlayerActivity`, implement `onDecoderCooldownReentry(path: String, remainingSec: Int)` to delegate to `playerDialogAndUiStateManager.showDecoderCooldownSnackbar(remainingSec) { viewModel.nextFile(skipDocuments = true) }`.
> 2. Add `showDecoderCooldownSnackbar(remainingSec: Int, onSkip: () -> Unit)` to `PlayerDialogAndUiStateManager`:
>    - Use `Snackbar.make(<root view>, getString(R.string.s0213_decoder_cooldown_manual, remainingSec), Snackbar.LENGTH_LONG)`.
>    - Set action via `setAction(R.string.s0213_action_skip) { onSkip() }`.
>    - Anchor to bottom of player content; do not block command panel.
>    - Add `Timber.i("S0213 cooldown snackbar shown: remainingSec=$remainingSec")`.
> 3. Strings `s0213_decoder_cooldown_manual` and `s0213_action_skip` are defined in Phase 05 — for now use literal English placeholders that compile, marked with `// TODO(phase-05): replace with localized string`.

**Verification:**

- `Grep` — `fun showDecoderCooldownSnackbar(` matches exactly once in `PlayerDialogAndUiStateManager.kt`.
- `Grep` — `Snackbar.LENGTH_LONG` present.
- `Grep` — `setAction` invoked with `onSkip` lambda.
- `Grep` — `TODO(phase-05)` matches exactly twice in this phase's diff (two string placeholders).
- `/build` — `assembleStandardDebug` exit 0.
- `expected: BUILD SUCCESSFUL | actual: BUILD SUCCESSFUL`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 5/5 PASS. `showDecoderCooldownSnackbar` at PlayerDialogAndUiStateManager line 80; `Snackbar.LENGTH_LONG` line 83; `setAction("Skip") { onSkip() }` line 85; 2× `TODO(phase-05)` placeholders within method (lines 81, 84). `PlayerPlaybackCallbackImpl.onDecoderCooldownReentry` (line 162) delegates to snackbar. assembleStandardDebug BUILD SUCCESSFUL.

---

### Step 02.4 — Compile-check end-to-end

**Files:** none (build only)
**Depends on:** Step 02.3

**Prompt for developer:**

> Run `/build` to compile `assembleStandardDebug` AND `assembleNoLegalDebug` (both flavors share the modified files in `src/main/`). Confirm Hilt graph resolves with `RecentDecoderFailureTracker` injected into both managers.

**Verification:**

- `/build` exit 0 for `assembleStandardDebug`.
- `/build` exit 0 for `assembleNoLegalDebug`.
- `expected: BUILD SUCCESSFUL ×2 | actual: BUILD SUCCESSFUL ×2`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 2/2 PASS. assembleStandardDebug + assembleNoLegalDebug both BUILD SUCCESSFUL (1m 33s for noLegalDebug, includes Chaquopy yt-dlp install).

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles for `standardDebug` AND `noLegalDebug`.
- [x] `Grep` for `TODO(phase-02)` returns zero hits (only `TODO(phase-05)` placeholders remain — see Step 02.3).
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

- Cooldown re-entry behavior is wired but uses placeholder English strings; Phase 05 swaps them to trilingual `strings.xml` keys.
- `RecentDecoderFailureTracker.clearAll()` is invoked on first frame render — a successful playback of any source resets the cooldown table for everything.

---

## Rollback Plan

Revert modifications in `VideoPlayerManager.kt`, `PlayerMediaLoaderManager.kt`, `PlayerActivity.kt`, `PlayerDialogAndUiStateManager.kt`, plus the new callback method on the playback callback interface and its no-op stubs. Tracker class itself (Phase 01) can stay — it has no effect when not consulted.
