# Phase 03 - resume-on-launch

**Strategic spec:** [`../S1152_resume-stream-on-launch.md`](../S1152_resume-stream-on-launch.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-07-22
**Completed:** 2026-07-22

---

## Objective

On cold start, resume the last active stream when it is the most recent session: read the persisted `StreamResumeState` in the startup helper, apply last-activity-wins against the media resume state plus TTL plus the resume setting gate, and route to the streams screen (auto-play for radio, plain list for video).

---

## Prerequisites

- [ ] Phase 01 ✅ Done, Phase 02 ✅ Done.
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainResumePlaybackHelper.kt` | Modified | ≤ 280 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` | Modified | ≤ 1500 |

---

## Steps

### Step 03.1 - Inject the repository into the startup helper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`, `MainResumePlaybackHelper.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> `MainResumePlaybackHelper` is constructed manually in `MainActivity`. Add a constructor parameter `private val streamResumeStateRepository: StreamResumeStateRepository` to the helper, and in `MainActivity` add `@Inject lateinit var streamResumeStateRepository: StreamResumeStateRepository` and pass it into the `MainResumePlaybackHelper(...)` constructor call. Add imports in both files.

**Verification:**

- `Grep` (MainResumePlaybackHelper.kt) - `streamResumeStateRepository: StreamResumeStateRepository` present in the constructor.
- `Grep` (MainActivity.kt) - `lateinit var streamResumeStateRepository` present and passed to `MainResumePlaybackHelper(`.

**Status:** `[x]` done

---

### Step 03.2 - Resume the stream when it is the newest session

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainResumePlaybackHelper.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> In `attemptResumePlayback`, after the `resumeOnNextLaunch` gate passes and the loading overlay is shown, read `val streamState = streamResumeStateRepository.get()`. Read the media `state` as today. Decide last-activity-wins: the stream branch runs when `streamState != null`, its `savedAt` is within `StreamResumeStateRepository.RESUME_TTL_MS`, and (`state == null || streamState.savedAt >= state.savedAt`). When the stream branch wins: dismiss the loading overlay, then start `StreamsActivity`. For a radio record that was playing (`mediaKind == "AUDIO" && wasPlaying`) build the intent via `StreamsActivity.createPlayShortcutIntent(activity, streamState.url)` so it auto-plays; otherwise (video, or a non-playing record) start a plain `Intent(activity, StreamsActivity::class.java)` so the list opens without autostart. Apply the same `overridePendingTransition` as the existing branches, then `return@launch`. If the stream branch does not win, fall through to the existing media-resume logic unchanged. Expired stream records (`savedAt` beyond TTL) must be cleared via `streamResumeStateRepository.clear()` before falling through. Keep each log line ≤ 120 chars; `Timber.d` only.

**Verification:**

- `Grep` - `streamResumeStateRepository.get()` present.
- `Grep` - `createPlayShortcutIntent` present in this file.
- `Grep` - `streamResumeStateRepository.clear()` present (expiry path).
- `Grep -n "Log\.d\("` - zero hits in this file.

**Status:** `[x]` done

---

### Step 03.3 - Insert the on-device verification probe

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainResumePlaybackHelper.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> At the entry of the stream-resume branch (once it is decided the stream wins), add exactly one probe line `Timber.d("S1152: resume stream branch - kind=%s playing=%b", streamState.mediaKind, streamState.wasPlaying)`. This is the temporary device-verification tag (ticket enters `BlockNeedUserTest`); it is removed by `/spec-check` on the verdict flip. One tag only, at the changed-flow entry.

**Verification:**

- `Grep` - `Timber.d("S1152:` matches exactly once across `app_v2/src`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - run `/build` (this build validates the code + the S1152 probe tag in one pass).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for both files.
- [x] Phase-boundary audit run - startup-path change reviewed: no main-thread I/O added (prefs read is on `Dispatchers.IO` inside the repo); no listener/lifecycle change.

---

## Handoff Notes to Next Phase

Cold-start routing is complete. Phase 04 regenerates the catalog for the new classes and writes dev-log/feature-inventory. The `Timber.d("S1152: ..` probe stays until `/spec-check` flips the verdict out of `BlockNeedUserTest`.

---

## Rollback Plan

Revert the phase commit(s) - additive branch in the startup helper plus one injected field; no data migration, no user-facing surface removed.
