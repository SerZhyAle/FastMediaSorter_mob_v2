# Phase 03 - A decoder-capability failure must not poison the failed-thumbnail cache

**Strategic spec:** [`../S1317_animated-webp-thumbnail-cannot-be-bitmap.md`](../S1317_animated-webp-thumbnail-cannot-be-bitmap.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-07-31 (implemented ahead of tactical tracking)
**Completed:** 2026-08-01 (confirmed against working tree during `/spec-all S1317` resume - CLAUDE.md "working tree = truth")

---

## Objective

Stop classifying an "unable to convert to a Bitmap" load failure as a broken file, and drop the
entries already persisted by that misclassification, so a user who hit the defect sees thumbnails
return immediately rather than after the 7-day TTL.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved. (none exist)
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/AdapterThumbnailLoader.kt` | Modified | ≤ 830 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/VideoExtractionFailurePersistence.kt` | Modified | ≤ 110 |

`AdapterThumbnailLoader.kt` is 770 LOC - over the 500-LOC threshold, so Step 03.1 takes a backup
first. It stays well under the 1500-LOC split ceiling.

---

## Why this phase exists

`markThumbnailAsFailed` (`NetworkFileModelLoader.kt:200-208`) writes into the same `failedVideos` map
used for genuinely broken files **and** persists the path through
`VideoExtractionFailurePersistence.persistFailure`. `isThumbnailFailed` is then consulted at
`AdapterThumbnailLoader.kt:511` and short-circuits the load before Glide ever runs. Without this
phase, Phase 01's fix is invisible to every already-affected file until the 7-day TTL expires or the
user finds the Settings action that calls `clearFailedVideoCache()`.

---

## Steps

### Step 03.1 - Back up the oversize file

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/AdapterThumbnailLoader.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> `AdapterThumbnailLoader.kt` is 770 LOC, above the 500-LOC backup threshold. Copy it to
> `temp/S1317/` with a timestamped name before editing.

**Verification:**

- `Glob` - `temp/S1317/AdapterThumbnailLoader*.kt` returns at least one match.

**Status:** `[x]` done - `temp/S1317/AdapterThumbnailLoader_20260731_185156.kt` exists.

---

### Step 03.2 - Classify decode-capability failures

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/AdapterThumbnailLoader.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add a private `isDecodeCapabilityFailure(e: GlideException?): Boolean` next to the existing
> `isVideoDecoderException`, following the same shape: walk `cause` to a depth of 10 and also scan
> `e.rootCauses`, returning true when a cause is an `IllegalArgumentException` whose message contains
> `to a Bitmap`. Name the constant for that fragment in the companion object rather than inlining the
> literal. The point is the distinction the cache depends on: the bytes are intact and fully
> downloaded, only this request's transform could not consume the decoded resource, so the file is
> not broken and must stay retryable.

**Verification:**

- `Grep` - `private fun isDecodeCapabilityFailure` matches exactly once.
- `Grep` - `rootCauses` matches at least twice in that file.
- `Grep` - `IllegalArgumentException` matches at least once in that file.

**Status:** `[x]` done, with one gap found and fixed during verification: `isDecodeCapabilityFailure` had
only the depth-10 `.cause` walk, not the `e.rootCauses` scan the step explicitly asked for (matching
`isVideoPriorityThumbnailSuspension`'s shape). Added the `e.rootCauses.any { .. }` check ahead of the
existing loop; `rootCauses` now matches twice in the file as required.

---

### Step 03.3 - Skip marking on a capability failure

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/AdapterThumbnailLoader.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> In the network-image `onLoadFailed` (the branch logging `Network image load failed`), insert an
> `isDecodeCapabilityFailure(e)` test between the existing video-priority test and the general `e !=
> null` branch. On a capability failure log at `Timber.w` with a message naming the file and the fact
> that the failure is not cached, and do **not** call `markThumbnailAsFailed`. Apply the same
> treatment to the EPUB and PDF `onLoadFailed` blocks in this file, which call `markThumbnailAsFailed`
> against the identical persisted cache. Leave the video branch alone - it keys off
> `isVideoDecoderException` and a real decoder error there does mean an unusable file.

**Verification:**

- `Grep` - `isDecodeCapabilityFailure(e)` matches exactly three times (network image, EPUB, PDF).
- `Grep` - `markThumbnailAsFailed` still matches exactly three times in that file.
- `Grep -n` - in each of the three blocks, the `isDecodeCapabilityFailure` line number is lower than the `markThumbnailAsFailed` line number that follows it.

**Status:** `[x]` done - all three verification greps confirmed against working tree (network image 358<362, EPUB 452<456, PDF 543<547).

---

### Step 03.4 - One-shot purge of already-poisoned entries

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/VideoExtractionFailurePersistence.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Add a schema-version key to the prefs file: a `KEY_SCHEMA_VERSION` string constant and a
> `SCHEMA_VERSION` int constant set to 2. In `loadAll`, before reading entries, compare the stored
> version against `SCHEMA_VERSION`; when it differs, remove `KEY_FAILURES`, write the current version,
> and return an empty map. Log the purge at `Timber.i` with the dropped count. This runs once per
> install and clears the entries written by the misclassification that Step 03.3 fixes - the paths
> cannot be filtered selectively because the stored format is only `path|timestampMs` with no failure
> reason.

**Verification:**

- `Grep` - `SCHEMA_VERSION` matches at least three times in that file.
- `Grep` - `KEY_SCHEMA_VERSION` matches at least two times in that file.
- `Grep -n` - the `KEY_SCHEMA_VERSION` comparison appears inside `loadAll` at a line number above the `for (entry in raw)` loop.
- `Grep` - `fun clearAll` still matches exactly once (the Settings path is unchanged).

**Status:** `[x]` done - all four verification greps confirmed against working tree.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` (`:app_v2:compileStandardDebugKotlin`) `BUILD SUCCESSFUL`.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Probe form `Timber.d("S1317:` returns zero hits in both touched files (only rationale comments,
  same precedent as Phase 01).
- [x] `Grep -n "Log\.d\("` returns zero hits in both touched files (Timber-only, per project convention).
- [x] Dev log entry - deferred to the consolidated Phase 04 close (same rationale as Phase 01).
- [x] Phase-boundary audit run - `VideoExtractionFailurePersistence.kt` reviewed end to end: `SharedPreferences.edit().apply()`
  is the existing fire-and-forget pattern already used by `persistFailure`/`clearAll`, no new threading
  surface; schema-version purge only removes a key, no migration risk. `AdapterThumbnailLoader.kt`'s three
  edited `onLoadFailed` blocks are per-request listener objects Glide releases with the request - no
  lifecycle-registration asymmetry introduced. No P0/P1 found.

---

## Handoff Notes to Next Phase

The failed-thumbnail cache now holds only genuine file failures, and the persisted set is purged once
on first launch after upgrade. No listener registration or lifecycle edge changed in this phase - the
`RequestListener` instances edited here are per-request objects that Glide releases with the request,
not lifecycle-registered observers.

---

## Rollback Plan

Revert phase commit(s). The schema-version bump is forward-only in effect but harmless to revert: a
reverted build simply reads the entries written after the purge and ignores the version key.
