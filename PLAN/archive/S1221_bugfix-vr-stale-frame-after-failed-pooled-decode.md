# S1221 - VR immersive shows the previous image with the new file's banner after a failed pooled decode

**Status:** Archived
**Priority:** 80

## 0. Raw capture

Found during the 2026-07-27 Quest 3 VR device-test session while verifying S0771 / S1112. Owner reported it as "ошибки определения стереокартинки" - stereo detection looked broken in the headset.

Detection is **not** broken. Every file in the diagnostic playlist resolved to the correct projection/layout in the log. What is broken is that the picture on the quad does not change while the banner does, so the HUD describes a file the user is not looking at.

## 1. Evidence

Screenshot `temp/scratch/quest_shots/com.sza.fastmediasorter.debug-20260727-223856.jpg`, taken 22:38:56:

- Banner reads `moraine_lake_flat_sbs.jpg  FLAT SBS`.
- The quad shows two vertically stacked copies of the scene, carrying the test asset's own eye labels: green **LEFT** on the upper copy, red **RIGHT** on the lower one. That is `moraine_lake_flat_tb.jpg` - the *previous* playlist item, still resident in the texture.

Log, `temp/scratch/vr_session_20260727-2224.log`:

```
22:38:48.996 VrStereoConfigResolver: moraine_lake_flat_tb.jpg  -> proj=FLAT layout=TOP_BOTTOM   (decoded OK)
22:38:53.560 VrTextureDecoder$decodeFilePooled: moraine_lake_flat_sbs.jpg bounds 15484x5327
             exceeds 96 MB budget; preflight inSampleSize=2 -> 7742x2663
22:38:53.565 DiagnosticXrActivity$loadCurrentMediaItem: Failed to decode image
             moraine_lake_flat_sbs.jpg; keeping previous frame
```

and the decode rejection itself, from the earlier item in the same run:

```
22:38:42.706 W BitmapFactory: bitmap marked for reuse (41233892 bytes) can't fit new bitmap (41249376 bytes)
22:38:42.706 E BitmapFactory: Unable to decode stream:
             java.lang.IllegalArgumentException: Problem decoding into existing bitmap
22:38:42.707 W DiagnosticXrActivity$loadCurrentMediaItem: Failed to decode image
             moraine_lake_flat_mono.jpg; keeping previous frame
```

## 2. Two defects, stacked

**2.1 The reuse pool rejects a bitmap that is 15 KB too small.** `inBitmap` requires the pooled allocation to be at least as large as the new decode. The pool offered 41 233 892 bytes for a decode needing 41 249 376 - short by 15 484 bytes, i.e. 0.04%. Files affected in this run: `moraine_lake_flat_mono.jpg` (7742x5327) and `moraine_lake_flat_sbs.jpg` (15484x5327, failed on both visits). `moraine_lake_flat_tb.jpg` happened to fit and decoded fine, which is why the failure looks random from the outside.

**2.2 A failed decode silently keeps the previous frame while the rest of the UI advances.** `loadCurrentMediaItem` logs at W level and returns; the banner, the resolved layout and the playlist index have all already moved to the new file. The user sees the old picture labelled with the new filename and the new layout - which reads as a stereo-detection bug, and cost this session a wrong diagnosis before the log was read.

## 3. Why the existing recovery never runs

The second candidate for 2.1 - "retry once without `inBitmap`" - **is already written**. `VrTextureDecoder.decodeFilePooled` lines 185-197 catch `IllegalArgumentException`, clear `inBitmap` and decode again. It has never executed once.

`BitmapFactory.decodeFile` does not let that exception out. It wraps the decode in `catch (Exception e) { Log.e("BitmapFactory", "Unable to decode stream: " + e); }` and returns `null`. The ticket's own log is the proof - the exception is reported by **BitmapFactory's** tag, not ours:

```
E BitmapFactory: Unable to decode stream: java.lang.IllegalArgumentException: Problem decoding into existing bitmap
W DiagnosticXrActivity$loadCurrentMediaItem: Failed to decode image moraine_lake_flat_mono.jpg; keeping previous frame
```

If the exception had propagated, the line between those two would be our own `"inBitmap incompatible; retry without pool reuse"`. It is absent, on every failure in the session.

The neighbouring `catch (OutOfMemoryError)` **does** work, and that is the tell: `OutOfMemoryError` is an `Error`, not an `Exception`, so `decodeFile`'s blanket catch lets it through. One recovery path in this function is live and the one next to it is dead, for a reason invisible at the call site.

So the fix is not to add a retry - it is to make the existing retry reachable by keying it off the **null return** instead of an exception that never arrives.

This also explains 2.1's "0.04% short" framing: the pool entry being 15 KB small is what *provokes* the failure, but the reason the failure is fatal is the unreachable recovery. Rounding the pool allocation up would paper over this particular file while leaving every other incompatibility - stride, config, dimensions - just as fatal.

## 3a. The invariant for 2.2

**The banner must never describe a frame that is not on the quad.** Either the frame advances or the banner does not. A visible "could not display this file" is better than a confident wrong label - this ticket exists because a wrong label cost a session's diagnosis.

`hudBanner.queueError(file.name, ..)` already exists and is already used by the video path two branches below (`"Playback Start Failed"`). The image path just does not call it.

## Goal

Провал декодирования не должен оставлять на экране прошлый кадр, подписанный именем нового файла. И восстановление после отказа пула должно реально срабатывать, а не существовать в виде недостижимого `catch`.

## Phase 1 - Make the pool-reuse recovery reachable

- [x] `VrTextureDecoder.decodeFilePooled` - when the first decode returns `null` **and** `inBitmap` was set, clear `inBitmap` and decode again, before giving up. Extracted as `retryWithoutPool`, reached by `?:` on the pooled decode.
- [x] Keep the `IllegalArgumentException` catch, but stop presenting it as the recovery path. It now routes to the same helper and its comment says it is a backstop for a future platform version that stops swallowing the exception.
- [x] The retry must not re-run the bounds preflight or change `inSampleSize`. `retryWithoutPool` clears only `inBitmap`; the KDoc records why the sample size is carried over.
- **Verification:** read back. The helper returns null immediately when `inBitmap` was already null, so the OOM branch (which clears it) cannot loop into a third attempt.

## Phase 2 - Never label a frame that is not there

- [x] `DiagnosticXrActivity.loadCurrentMediaItem` - on a null decode, surface the failure instead of only logging.
- [x] Both HUD modes covered: banner mode gets `hudBanner.queueError(file.name, DECODE_FAILED_LABEL)`; panel mode sets `hudRenderer.currentFilename` to the same label plus the filename and repaints. The panel branch hops to `Dispatchers.Main` first - the decode runs on IO, and `scheduleHudPanelRepaint` mutates an unsynchronised `hudRepaintScheduled` flag.
- **Verification:** read back - the image branch now has no path that returns without either queueing a frame or queueing an error.

## Phase 3 - Compile

- [x] `.\a.ps1 fkn` - PASS, exit 0, `BUILD SUCCESSFUL in 33s`, "4 executed" (a real recompile after the debug tags were added, not a cached pass).
- [x] detekt gate scoped to the changed files. **Reports findings, none of them from this change** - see below.
- **Verification:** compile exit 0. The detekt result is qualified rather than claimed as a pass.

The scoped gate flags `VrTextureDecoder.kt`. All eight findings sit on pre-existing lines - the OOM `System.gc()` fallback (38-41), the S0960 bundled preflight log (109), the budget log (160-161) and `pickSampleSizeForBudget` (223-229). This change added one private method and two log lines, all below line 200; it shifted their line numbers, which is why the gate noticed them now. The file has **zero** entries in `config/detekt/baseline-app_v2.xml`, so any edit to it surfaces the whole backlog.

Parked as **S1247** rather than fixed here: two of the eight are a `System.gc()` / `runFinalization()` pair that is a deliberate last-ditch reclaim before a large direct allocation on a headset, and deleting a working OOM fallback to turn a bar green is not a drive-by decision. S1247 also records that **S1198** is the same mechanism on another file, so the real fix may be a baseline sweep rather than two hand-edited files.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0960, S0771, S1112
- **UI scope:** a failed image decode now shows the existing red error banner instead of silently keeping the previous picture. No new UI surface - the banner and its style already exist and are already used by the video path.
- **Flavor scope:** `noLegal` only - `src/vr/java` is mounted by that flavor alone (`app_v2/build.gradle.kts`).

## 4. Scope note

Related to but distinct from S0960 (`bugfix-vr-diagnostic-oom-decode`, `BlockNeedUserTest`) - that ticket is about OOM under a warm heap and about the 96 MB budget/preflight sampling, both of which behaved correctly here (`diagnostic_360_mono.jpg` 8192x4096 -> 4096x2048 decoded without OOM). This is the pooled-reuse size check and the stale-frame fallback, neither of which S0960 touches.
