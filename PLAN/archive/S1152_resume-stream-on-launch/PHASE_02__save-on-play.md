# Phase 02 - save-on-play

**Strategic spec:** [`../S1152_resume-stream-on-launch.md`](../S1152_resume-stream-on-launch.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-07-22
**Completed:** 2026-07-22

---

## Objective

Persist the last active stream from the streams flow: write a `StreamResumeState` when a stream starts, clear it on explicit user stop. Both the radio (AUDIO inline) and the video (VIDEO/RTSP fullscreen) branches.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt` | Modified | ≤ 900 |

> `StreamsActivity.kt` is a large file - back it up under `temp/S1152/` before editing (Constraints: >500 LOC).

---

## Steps

### Step 02.1 - Inject the stream-resume repository

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `@Inject lateinit var streamResumeStateRepository: StreamResumeStateRepository` to `StreamsActivity` (it is already `@AndroidEntryPoint` with `@Inject lateinit` fields). Add the import.

**Verification:**

- `Grep` - `lateinit var streamResumeStateRepository` matches exactly once.
- `Grep` - `import ..domain.repository.StreamResumeStateRepository` present.

**Status:** `[x]` done

---

### Step 02.2 - Save the record on stream start

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `onPlay(source)`, after the network gate passes, persist the last active stream for both branches. In the AUDIO branch (before/after `inlineAudio.play(..)`), and in the VIDEO/RTSP branch (before `streamPlayerLauncher.launch(..)`), launch on `lifecycleScope` a `streamResumeStateRepository.save(StreamResumeState(url = source.url, title = source.title, mediaKind = source.mediaKind, wasPlaying = source.mediaKind == "AUDIO", savedAt = System.currentTimeMillis()))`. `wasPlaying` is true only for AUDIO (radio continues playing on resume); VIDEO records the stream but resume will not autostart it. Do not persist in the "tap the already-playing row toggles it off" early-return path (that is a stop, handled in Step 02.3). Keep it a single private helper `persistStreamResume(source)` called from both branches to avoid duplication.

**Verification:**

- `Grep` - `streamResumeStateRepository.save` present.
- `Grep` - `fun persistStreamResume` matches exactly once.
- `Grep` - `wasPlaying = source.mediaKind == "AUDIO"` present.

**Status:** `[x]` done

---

### Step 02.3 - Clear the record on explicit user stop

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> When the user explicitly stops inline radio - the `onPlay` early-return path where `source.mediaKind == "AUDIO" && inlineAudio.playingId == source.id` (tap the playing row to toggle off) - also clear the persisted record: `lifecycleScope.launch { streamResumeStateRepository.clear() }` alongside the existing `inlineAudio.stop()`. Do NOT clear on `onStop()`/screen-leave or on the background-continue exit - those are not "stop this stream", and the record must survive a normal exit so the next launch can resume. Use a single private helper `clearStreamResume()`.

**Verification:**

- `Grep` - `fun clearStreamResume` matches exactly once.
- `Grep` - `streamResumeStateRepository.clear()` present.
- `Grep` - `clearStreamResume()` is called from the AUDIO toggle-off early-return branch (inspect the `inlineAudio.playingId == source.id` block).

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build`.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for `StreamsActivity.kt`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (listener/lifecycle unchanged; only added coroutine writes on `lifecycleScope`).

---

## Handoff Notes to Next Phase

A fresh `StreamResumeState` now exists in prefs whenever a stream is (or was last) active, and is cleared on explicit stop. Phase 03 reads it on cold start and routes to the streams screen.

---

## Rollback Plan

Revert the phase commit(s) - additive changes to `StreamsActivity` only; no data migration.
