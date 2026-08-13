# S1220 - Atlas slicers dereference a recycled decoder outside their guard (P0 crash)

**Status:** Archived
**Priority:** 85

## 0. Raw capture

Parked during the 2026-07-27 Quest 3 VR device-test session, out of scope of the VR backlog under test.

Owner-reported crash, verbatim:

```
FastMediaSorter DEBUG CRASH
Timestamp: 2026-07-27 22:31:56.870
Thread: main (2)
Exception: java.lang.IllegalStateException
Message: getWidth called on recycled region decoder
Version: 2.60.7272.219-NoLegal-DEBUG / noLegal / debug
Device: Oculus Quest 3, Android 14, SDK 34

java.lang.IllegalStateException: getWidth called on recycled region decoder
	at android.graphics.BitmapRegionDecoder.checkRecycled(BitmapRegionDecoder.java:303)
	at android.graphics.BitmapRegionDecoder.getWidth(BitmapRegionDecoder.java:255)
	at com.sza.fastmediasorter.ui.streams.ChannelPreviewAtlasSlicer$tileFor$2.invokeSuspend(ChannelPreviewAtlasSlicer.kt:63)
	at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:104)
	...
	Suppressed: DiagnosticCoroutineContextException: [StandaloneCoroutine{Cancelling}@88fdf41, Dispatchers.Main.immediate]
```

Repro context: the Streams channel grid was open in the headset; the owner navigated away. P0 - the app process dies.

## 1. Root cause

All three slicers in `ui/streams/` share one shape: a cached, recyclable graphics resource is fetched **under** a mutex and then dereferenced **outside** it. `invalidate()` holds the same mutex and recycles the resource, so between the fetch and the dereference the reference can go dead.

`ChannelPreviewAtlasSlicer.tileFor`:

```kotlin
val activeDecoder = decoder() ?: return@withContext null   // mutex released here
if (!isInBounds(index, activeDecoder.width, activeDecoder.height)) return@withContext null
try {
    activeDecoder.decodeRegion(rectFor(index), BitmapFactory.Options())
} catch (e: IllegalStateException) {
    // "A decoder recycled mid-read (post-invalidate race) yields no tile rather than a crash."
}
```

The author anticipated exactly this race - the `IllegalStateException` catch says so - but **the guard starts one line too low**: the bounds check reads `activeDecoder.width` before the `try`, so it throws uncaught.

The session log proves both halves. The same race fired 16+ times and was swallowed correctly at the covered call:

```
22:31:56.691 I ChannelPreviewAtlasSlicer$tileFor: IllegalStateException: decodeRegion called on recycled region decoder
   .. x16, all logged at I level, no crash ..
22:31:56.870 FATAL: getWidth called on recycled region decoder      <- the one call outside the try
22:31:57.353 ActivityManager: Process com.sza.fastmediasorter.debug (pid 13644) has died: fg TOP
```

So the feature races constantly under normal use; only the uncovered call is fatal.

## 2. The same defect exists in two more slicers

Found by sweeping `grep -rn "decoder() ?:" app_v2/src` and reading the third slicer. Fixing only the crashing one would leave two live copies of an identical P0.

- `StreamLogoAtlasSlicer.kt:69` - byte-for-byte the same mistake: `isInBounds(index, activeDecoder.width, activeDecoder.height)` above the `try`. Not yet observed crashing only because the logo sheet is invalidated less often than the preview sheet.
- `FaviconAtlasSlicer.kt:64,67` - worse in kind: it caches a whole `Bitmap` rather than a decoder, `invalidate()` calls `recycle()` on it, and `Bitmap.createBitmap(atlas, ..)` is guarded by **no try at all**. A recycled source is rejected there, so the same navigate-away race can kill the process on this path too. Which exception carries that rejection is not asserted here - it has differed across platform versions - so the fix catches both rather than betting on one.

## 3. Fix chosen, and the two rejected

**Chosen - every dereference of the escaped reference moves inside the existing guard.** One line moves in each of the two decoder slicers; the favicon slicer gains the guard it never had. Cost: nothing. It keeps the deliberate design where the mutex is released before decoding so tiles decode in parallel.

Rejected, with reasons worth keeping:

- **Whole read under `mutex.withLock`.** Removes the race by construction, but `decoder()` itself takes the same mutex and `kotlinx.coroutines.sync.Mutex` is **not reentrant** - the naive version deadlocks on the first call. It would also serialise the 16 parallel decodes visible in the log, undoing the reason the lock was released in the first place.
- **Refcounted deferred recycle** - `invalidate()` swaps the reference and defers `recycle()` until in-flight readers drain. Genuinely removes the race while keeping concurrency, but adds lifetime state to three small helper classes to protect a path whose correct answer on a lost race is simply "no tile". Over-engineered here; revisit if a slicer ever needs to return something better than null.

No mechanical gate is proposed: "a reference obtained under a lock is dereferenced outside it" needs dataflow analysis, not a grep, so `scripts/quality/assert-*.ps1` cannot express it.

## Goal

Промах по гонке при уходе с экрана каналов не должен ронять процесс. Все обращения к переработанному декодеру или битмапу обязаны попадать в тот же `catch`, который уже был написан ровно для этого случая - включая чтение ширины и высоты.

## Phase 1 - Move the bounds check inside the guard in both decoder slicers

- [x] `ChannelPreviewAtlasSlicer.tileFor` - bounds check moves inside the `try`; the `catch` comments become true.
- [x] `StreamLogoAtlasSlicer.tileFor` - same move.
- [x] Each `try` gains a comment naming the invariant: any member access on the escaped reference, `width`/`height` included, must sit inside it.
- **Verification:** `grep -n "activeDecoder" <each file>` shows no occurrence above its `try {` line. PASS - the only line above the `try` in each file is the `decoder() ?:` fetch itself, which cannot throw.

## Phase 2 - Give the favicon slicer the guard it never had

- [x] `FaviconAtlasSlicer.tileFor` - wrap the bounds read and `Bitmap.createBitmap` in a `try` catching `IllegalStateException` (recycled) and `IllegalArgumentException` (rect outside a shrunk sheet), returning null and logging, matching the two decoder slicers.
- **Verification (predicate corrected, see below):** `check-standard-fast.ps1 -Mode Unit -Tests "*AtlasSlicerTest"` - `ChannelPreviewAtlasSlicerTest` 3/3 green, `FaviconAtlasSlicerTest` 5/5 green, `StreamLogoAtlasSlicerTest` 3 of 4 red on a **pre-existing** stale grid contract (parked as S1245), untouched by this ticket.

The original predicate was `.\a.ps1 fu` exits 0. That is not reachable and would have been a false gate either way:

- The full suite never runs the `ui` package at all. Its worker JVM dies of `OutOfMemoryError` around `data.remote.ftp.*` and still prints `946 tests completed, 1 failed`, which reads like a completed run - parked as **S1244**. So an `fu` exit code says nothing about these three classes; they had to be run through a `--tests` filter to be observed.
- Under that filter, `StreamLogoAtlasSlicerTest` fails 3 of 4 because it asserts a 135x135 / 60-column grid that both the app and the offline packer retired in favour of 136x136 / 59 - parked as **S1245**. Not caused here: this ticket moves a bounds check inside a `try` and changes no constant.
- A third unrelated red, `CameraCaptureSaverTest`, is parked as **S1246**.

None of these are made better or worse by this change, and none are silently edited to turn the bar green.

## Phase 3 - Compile and gates

- [x] `.\a.ps1 fk` (standard Kotlin compile - all three files are in `src/main`). PASS - exit 0, `BUILD SUCCESSFUL in 29s`.
- [x] detekt gate scoped to the three changed files. PASS - exit 0, `197 file(s) with new findings project-wide, none among changed files`.
- **Verification:** both exit 0. Met.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1154, S1200, S1201
- **UI scope:** no visible change. A tile that loses the race renders as no-tile instead of killing the process.
- **Flavor scope:** all flavors - `ui/streams/` lives in `app_v2/src/main`.

## 4. Test debt

No unit test reproduces the fix. The failure needs a genuinely recycled `BitmapRegionDecoder`, and the existing suite is deliberately geometry-only ("no bitmap decode here" - `ChannelPreviewAtlasSlicerTest` KDoc) because Robolectric's shadow does not model `checkRecycled`. The guarantee here is structural - no dereference outside the guard - and is verified by reading, not by a test.

## Last Audit

2026-07-28, static. Verdict: **Verified**.

Read back all three files after the edit. In each, the only statement above the `try` is the `decoder()` / `atlas()` fetch itself, which cannot throw on a recycled reference - it returns one. Every subsequent access (`width`, `height`, `decodeRegion`, `createBitmap`) is inside the guard, so the P0 path is closed in all three.

Line evidence, re-read this session: `ChannelPreviewAtlasSlicer` fetch at 62, `try` at 67; `StreamLogoAtlasSlicer` fetch at 68, `try` at 73 (line 69 builds `options` and touches no decoder); `FaviconAtlasSlicer` fetch at 64, `try` at 70.

The section 2 sweep is complete, re-established by a different query than the one that found it. Intersecting the files that use `kotlinx.coroutines.sync.Mutex` (14) with those that call `.recycle()` (45) yields exactly these three. The one near-miss, `PdfRendererWrapper`, performs the whole render inside `mutex.withLock` and lets no reference escape, so it is a different shape and needs no change.

Tests re-run this session, `check-standard-fast.ps1 -Mode Unit -Tests "*AtlasSlicerTest"`: 12 completed, 3 failed. The three reds are all `StreamLogoAtlasSlicerTest` asserting the retired 135x135 / 60-column grid (S1245); `ChannelPreviewAtlasSlicerTest` and `FaviconAtlasSlicerTest` are green. This ticket changes no constant, so the reds are neither caused nor cured here - the same split section 3 predicted.

Behaviour on a lost race is unchanged in kind: `tileFor` already returned null for an absent atlas or an out-of-range index, so callers needed no change - the fix converts a process kill into a return value they already handle.

`invalidate()` still recycles under the mutex while a reader may be mid-decode. That is deliberate and is what the guard exists for; removing the race itself was considered and rejected in section 3.

Not verified on device: the failure is a timing race whose fixed state is the *absence* of a crash, which no single navigate-away run can demonstrate. The next Quest session with the Streams grid open is a free opportunity to confirm, but it is not treated as a gate - the guarantee here is structural.

The inventory record's flavor list was checked against the gate and is correct. `flavors: ["standard","legacy","vr","noLegal"]` is exactly the set where `SUPPORT_STREAMS` is `true` (`build.gradle.kts` 313, 386, 469, 528); `lite` and `photos` set it `false`.

## 5. Scope note

Distinct from S1154 (`channel-preview-atlas`, the feature that introduced this file, still `BlockNeedUserTest`) and from S1200 (`channel-preview-atlas-refresh`, `Verified` - the ticket that introduced `invalidate()`, i.e. the other half of the race). Fixing this does not require either of those to move first.
