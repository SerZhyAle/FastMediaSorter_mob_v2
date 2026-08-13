# Phase 02 - Format probe

**Strategic spec:** [`../S1474_stream-about-channel.md`](../S1474_stream-about-channel.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Produce the measured half: a cancellable, deadline-bounded probe that opens a channel in a surfaceless engine and reports its formats, a read path that takes the same values off an engine already playing, and the accessor that makes the inline radio engine reachable. No UI.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Research artifacts 01, 02, 03 and 04 read - they decide what this phase reads and when.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/helpers/StreamFormatProbeManager.kt` | New | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamInlineAudioManager.kt` | Modified | ≤ 20 added |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamHealthProbeManager.kt` | Modified | ≤ 10 added |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/dialog/helpers/StreamFormatProbeManagerTest.kt` | New | ≤ 160 |

---

## Steps

### Step 02.1 - Add the probe manager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/helpers/StreamFormatProbeManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `StreamFormatProbeManager`, modelled on `StreamHealthProbeManager` - a muted, surfaceless `ExoPlayer` with `playWhenReady = false`, a `DefaultBandwidthMeter` handed to the builder, an eight-second deadline, and `player.release()` in a `finally` block that runs on cancellation too. Unlike the health probe it returns a `StreamMeasuredFormats`: collect the description on `STATE_READY` and again on `onTracksChanged`, keeping the last non-empty value per field, and take picture size from `player.videoSize` when the format's width or height is `NO_VALUE`, per research artifact 01. Read the bandwidth estimate once at the end and report a non-positive estimate as null, per research artifact 02. A field the engine never supplied stays null. Expose one suspend function that measures a url and one cancel entry; own exactly one job, per research artifact 04.

**Why:**

Strategic §3.2 and §7 require that the window never outlives its measurement or leaks it, and research artifact 03 records that the existing health probe already encodes the deadline, cancellation and off-thread teardown these failures need - reusing that shape rather than inventing a second lifecycle is ADR-2.

**Verification:**

- `Glob` - `.../helpers/StreamFormatProbeManager.kt` exists.
- `Grep` - `class StreamFormatProbeManager` matches once.
- `Grep` - `finally` and `release()` both present.
- `Grep` - `withTimeoutOrNull` present.
- `Grep` - `videoSize` present.
- `Grep` - `Log\.d\(` returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-08-08 - Verification 6\6 PASS. `StreamFormatProbeManager.kt` created (208 LOC, budget 260): muted surfaceless `ExoPlayer` with `playWhenReady = false`, a `DefaultBandwidthMeter` handed to the builder, the same 8 s deadline the health sweep uses, and `player.release()` in `finally` so a cancelled window still closes its engine. Readings are merged on `STATE_READY` and on `onTracksChanged` through `mergedWith`, which keeps whichever side has an answer, and picture size falls back to `player.videoSize` when the format reports `NO_VALUE`. A non-positive bandwidth estimate becomes null. `.\a.ps1 fk` exit 0, `Log.d` 0.
- One job only, held as a `Deferred`, and `cancel()` supersedes rather than races - research artifact 04's rule that two decoders must not run at once.

---

### Step 02.2 - Read the formats off an already-playing engine

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/helpers/StreamFormatProbeManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add a second entry point that takes an already-running `Player` and returns the same `StreamMeasuredFormats` from it without opening anything - reading the video and audio formats, the video size and, where the engine exposes it, its bandwidth estimate. It must not prepare, start, stop, seek or release the passed engine. Share the field-extraction code with step 02.1 so the two paths cannot drift.

**Why:**

Strategic §11 criterion 6 requires that a channel already playing is reported without a second connection to the server, because a server allowing one session per client would break the picture the user is watching and the second connection would measure a different one.

**Verification:**

- `Grep` - a function taking `Player` and returning `StreamMeasuredFormats` present in the file.
- `Grep` - `\.release\(\)|\.prepare\(\)|\.stop\(\)` do not appear inside that function's body.
- `Grep` - the shared extraction function is called from both entry points.

**Status:** `[x] done`

**Step Log:**

- 2026-08-08 - Verification 3\3 PASS. `fun readFrom(player: Player): StreamMeasuredFormats` is a single expression delegating to `formatsFrom`; its body contains no `release()`, `prepare()`, `stop()`, seek or start, so an engine it is handed is only read. The probe path reaches the same extraction by calling `readFrom(built)` on its own engine at both listener callbacks (lines 97 and 103), so the two paths cannot drift - there is one extraction, not two that agree today.
- The already-playing path passes `bitrateEstimate = null` deliberately: the common `Player` interface exposes no meter, and inventing one would report a number this manager did not measure.

---

### Step 02.3 - Expose the inline radio engine

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamInlineAudioManager.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add one read-only accessor returning the engine currently playing - the in-app player when that path owns playback, the service player when that one does, null when nothing plays - alongside the id of the channel it is playing. Change nothing else: no lifecycle, no ownership, no new state. Back up the file first if it exceeds 500 LOC.

**Why:**

Research artifact 03 records that the in-app radio engine is private today, so without this accessor a card-menu readout for the station playing two centimetres above the menu would open a second connection to it - exactly what strategic §11 criterion 6 forbids.

**Verification:**

- `Grep` - the new accessor declared once, returning a nullable `Player`.
- `Grep` - no new `var` field added to the class.
- `Grep` - `Log\.d\(` returns zero hits in the file.
- Backup file present under `temp/S1474/` if the file exceeds 500 LOC.

**Status:** `[x] done`

**Step Log:**

- 2026-08-08 - Verification 4\4 PASS. `val playingEngine: Player?` added once beside the existing `playingId`, returning the already-held `player` field - which the class already sets to the in-app engine on the OFF path and the service engine on the ON path, so no new state was needed. `private var` count unchanged at 9, `Log.d` 0. File is 403 LOC, under the 500 threshold, so Rule 5 asks for no backup.
- The KDoc states the borrow explicitly - a reader may take values off it and must never prepare, start, stop or release it - because the accessor is the one place where that ownership rule can be read before it is broken.

---

### Step 02.4 - Cancel the catalog sweep when a measurement starts, and test the probe

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamHealthProbeManager.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/ui/dialog/helpers/StreamFormatProbeManagerTest.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Make the health sweep cancellable from the measurement path by reusing its existing `cancel` entry - the caller in Phase 04 will invoke it, so this step only confirms the entry is reachable from outside and documents the contract in the class KDoc. Then unit-test the probe's pure parts: extraction maps a format with populated fields to filled values; a format whose width and height are `NO_VALUE` falls back to the video size; a non-positive bandwidth estimate becomes null; extraction from a null format yields an all-null result rather than throwing.

**Why:**

Research artifact 04 rules that the catalog health sweep and a measurement must not decode at the same time, and strategic §3.2 puts a hard budget on competing decoders on a weak device.

**Verification:**

- `Grep` - `fun cancel` is public in `StreamHealthProbeManager`.
- `Glob` - the test file exists.
- Run `.\a.ps1 fu` and confirm `StreamFormatProbeManagerTest` reports zero failures.

**Status:** `[x] done`

**Step Log:**

- 2026-08-08 - Verification 3\3 PASS. `fun cancel()` in `StreamHealthProbeManager` was already public with no modifier (line 85) - nothing to change, which is what this step was meant to confirm rather than assume. Its class KDoc now carries the contract: a measurement opens its own decoder, so whoever starts one cancels this sweep first, and the call is idempotent and safe when idle.
- `StreamFormatProbeManagerTest.kt` created with 5 tests. Scoped run exit 0, and `TEST-..StreamFormatProbeManagerTest.xml` reads `tests="5" skipped="0" failures="0" errors="0"`. Covers all four required cases - populated format, `NO_VALUE` falling back to the video size, a non-positive estimate becoming null, a null format yielding an all-null holder - plus the merge rule, since that is what makes two readings safe.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` exit 0, 2026-08-08 02:00.
- [x] `Grep` for `TODO(phase-02)` returns zero hits in all four files.
- [x] `Grep -n "Log\.d\("` returns zero hits in all four files.
- [x] Dev log entry added via `post-change.ps1` - one row naming all four files.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - 2589 records.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

## Phase-boundary audit (2026-08-08)

This phase owns a media engine, so Layers 2 and 3 carried the weight.

- Layer 1 - architecture and budgets. `StreamFormatProbeManager.kt` 222 LOC of 260, the test 99 of 160, `StreamInlineAudioManager` grew by 10 lines against a 20 budget and `StreamHealthProbeManager` by 5 against 10. No business logic entered a view.
- Layer 2 - coroutines and cancellation. Exactly one job, held as a `Deferred`, and a second `measure` supersedes the first rather than racing it. Cancellation is re-thrown in its own `catch` arm before the broad handling, so a dismissed window does not report a failure - and the `finally` still runs, which is the property that matters. The whole probe body is confined to `Dispatchers.Main`, which is where ExoPlayer requires its calls.
- Layer 3 - listener and engine ownership. The probe's `Player.Listener` is now removed explicitly in `finally` before `release()`, paired on every path including cancellation. `readFrom(Player)` touches no state of an engine it did not open - no prepare, start, stop, seek or release anywhere in its body - and the new `playingEngine` accessor states that borrow in its own KDoc.
- Layer 4 - Room. Not applicable.
- Three gate findings were fixed in this phase rather than deferred: a swallowed `CancellationException` (S1363 shape), a listener added without a pairing, and a `TooGenericExceptionCaught` on `catch (Throwable)`. The last one was narrowed to `IllegalStateException` and `IllegalArgumentException` - the only faults construction and preparation actually raise, since an unplayable channel arrives as a `PlaybackException` on the listener and is a legitimate answer rather than an exception.
- Not a finding, recorded for the next reader: `.\a.ps1 fk` failed once mid-phase on `StreamsActivity.kt:506`, a file this ticket does not touch - a sibling session working S1473 had the streams screen half-written. It passed on the retry once that settled. A red tree in a shared checkout is not automatically your regression; check whose file it names before debugging it.

---

## Handoff Notes to Next Phase

Two measurement paths exist and produce the same holder: measure a url, or read a running engine. Phase 03 chooses between them; it never releases an engine it did not open.

---

## Rollback Plan

Revert phase commit(s). The accessor added to the inline audio manager is additive and unreferenced until Phase 04, so reverting cannot affect radio playback.
