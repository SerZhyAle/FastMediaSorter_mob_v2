# Phase 01 - Headless stream play manager

**Strategic spec:** [`../S1471_stream-shortcut-starts-background-playback.md`](../S1471_stream-shortcut-starts-background-playback.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 2 / 2
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Introduce `StreamHeadlessPlayManager`, which starts or stops one radio stream through `AudioPlaybackService` without any view, and expose a decision helper that says whether a given source qualifies for the screen-less path. No entry point calls it yet.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamHeadlessPlayManager.kt` | New | ≤ 140 |

---

## Steps

### Step 01.1 - Add `StreamHeadlessPlayManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamHeadlessPlayManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `class StreamHeadlessPlayManager(private val context: Context)` in `ui/streams/helpers/`. Give it one public method `fun play(source: StreamSourceEntity, onFinished: () -> Unit)`. Inside, construct an `AudioServiceController(context)` and call `connect { player -> .. }`. In the connect callback compare `player.currentMediaItem?.requestMetadata?.mediaUri?.toString()` with `source.url`: when they are equal and `player.isPlaying` is true, call `player.stop()` and invoke `onFinished()`; otherwise call the controller's `playAudioWithMetadata(Uri.parse(source.url), source.title) { onFinished() }`. Log the branch taken with `Timber.i`. Do not reference any `View`, `Activity`, or `StreamInlineAudioManager` type - the class must compile against a plain `Context`.

**Why:**

Strategic §2 states the missing link is a caller that can drive `AudioServiceController` without a view, because `StreamInlineAudioManager` couples the service connection to the visible mini-control and its `play()` cannot run without one.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamHeadlessPlayManager.kt` exists.
- `Grep` - `class StreamHeadlessPlayManager` matches exactly once in that file.
- `Grep` - `fun play(source: StreamSourceEntity, onFinished: () -> Unit)` present.
- `Grep` - `android.view`, `Activity`, `StreamInlineAudioManager` return zero hits in that file.
- `Grep` - `Log\.d\(` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-08-08 - Verification 5/5 PASS. Files: `ui/streams/helpers/StreamHeadlessPlayManager.kt` (+62 LOC, New). One predicate relaxed: `StreamInlineAudioManager` appears once as a KDoc `[link]` explaining why that class cannot serve this path. Predicate intent is "no dependency" - re-checked as no import and no constructor call, both zero. Comment kept, per Rule 9 it carries the WHY.

---

### Step 01.2 - Add the qualification helper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamHeadlessPlayManager.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a `companion object` with `fun qualifiesForHeadlessPlay(source: StreamSourceEntity, backgroundAudioEnabled: Boolean, hasNetwork: Boolean): Boolean` returning true only when `source.mediaKind == "AUDIO"`, `backgroundAudioEnabled` and `hasNetwork` are all true. Keep it a single boolean expression so detekt's ReturnCount stays at one.

**Why:**

Strategic §3.1 fixes three fallback cases - background audio switched off, a non-audio stream, and no network - as "behave exactly as today", so the caller needs one place that decides screen-less versus screen, rather than repeating the condition at each entry point.

**Verification:**

- `Grep` - `fun qualifiesForHeadlessPlay` present in that file.
- `Grep` - `mediaKind == "AUDIO"` present in that file.
- `.\a.ps1 fk` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-08-08 - Verification 2/3 literal PASS, 1 adapted. `fun qualifiesForHeadlessPlay` present; `.\a.ps1 fk` exit 0 (`BUILD SUCCESSFUL in 47s`). The predicate `mediaKind == "AUDIO"` does not match literally: the string is held in a private `const val AUDIO_MEDIA_KIND` and the comparison reads `source.mediaKind == AUDIO_MEDIA_KIND`, which is what keeps the literal out of the expression. Predicate intent - the audio-only arm exists - re-checked against both lines and PASS. Content landed in the same file write as Step 01.1; both verified separately before either was flipped.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Phase-boundary audit - 2026-08-08

Protocol: `docs/CODE_AUDIT_PROTOCOL.md`, Layers 1-3 (no Room surface touched).

- **P1 - Activity context retained for the connection's lifetime.** `StreamHeadlessPlayManager` passed the caller's `Context` straight to `AudioServiceController`, and the play branch deliberately never releases that controller. The caller is a trampoline Activity that finishes immediately, so the binding would outlive it and hold it alive. Fixed in this phase: the manager stores `context.applicationContext`. Media3 builds its `MediaController` against any context, and the application context is what matches the connection's intended process-scoped lifetime. **Landed 2026-08-08 03:39** - the finding above was written before the fix reached the file, and the session that wrote it stopped while queued for `CODE.LOCK`; the constructor still passed the caller's context through when the phase was picked up again. Verified by `.\a.ps1 fk` exit 0 after the edit.
- **P3 - `onFinished` may run after the caller is destroyed.** The connect callback is asynchronous, so a caller that already finished receives it. Accepted: every caller's `onFinished` is `finish()`, which is a no-op on a finished Activity. No fix.
- Layer 1: naming, layer placement and file size (62 LOC) conform. Layer 2: no coroutine, no lifecycle observer registered. Layer 3: the un-released controller on the play branch is a documented invariant in the class KDoc, not an oversight.

---

## Handoff Notes to Next Phase

`StreamHeadlessPlayManager` owns the service connection and the start-or-stop decision; `qualifiesForHeadlessPlay` owns the screen-less-versus-screen decision. Phase 02's trampoline supplies the three inputs and does nothing else with playback.

---

## Rollback Plan

Revert phase commit(s) - a new file with no callers, no data migration and no user-facing surface changed.
