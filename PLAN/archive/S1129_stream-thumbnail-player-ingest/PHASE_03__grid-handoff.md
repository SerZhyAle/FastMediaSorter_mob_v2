# Phase 03 - Grid Handoff

**Strategic spec:** [`../S1129_stream-thumbnail-player-ingest.md`](../S1129_stream-thumbnail-player-ingest.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-07-20
**Completed:** 2026-07-20

---

## Objective

Return adopted-frame state to the Streams grid and route headless captures through the same ingest owner.

---

## Prerequisites

- [x] Phase 02 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | <= 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt` | Modified | <= 900 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamFrameSnapshotManager.kt` | Modified | <= 320 |

---

## Steps

### Step 03.1 - Publish the player result

**Files:** `PlayerActivity.kt`

**Prompt for developer:**

> Inject `StreamFrameIngestor`. When the manager reports a successful adoption, set an Activity result containing a stable `EXTRA_` URL key; do not finish the player or change normal Back behavior.

**Verification:**

- Successful adoption sets `RESULT_OK` plus the stream URL.
- Non-stream player launches do not emit the extra.

**Status:** `[x]` done

### Step 03.2 - Launch for result and repaint the tile

**Files:** `StreamsActivity.kt`

**Prompt for developer:**

> Replace the video/RTSP `startActivity` call with an Activity Result launcher. On a successful URL result, call `StreamGridAdapter.repaintUrl(url)`; preserve the current fullscreen and synthetic-resource extras.

**Verification:**

- AUDIO playback path remains inline and unchanged.
- VIDEO and RTSP use the launcher.
- Missing/cancelled result is a no-op.

**Status:** `[x]` done

### Step 03.3 - Unify path A ingestion

**Files:** `StreamFrameSnapshotManager.kt`, `StreamsActivity.kt`

**Prompt for developer:**

> Replace direct cache/store writes in the headless snapshot manager with `StreamFrameIngestor`. Keep cache freshness reads, queue behavior, outcome reporting, and player release semantics unchanged.

**Verification:**

- `StreamFrameSnapshotManager` has no direct `StreamFramePersistentStore.save` call.
- Both path A and path B call the same ingestor contract.

**Status:** `[x]` done

### Step 03.4 - Add the device verification probe

**Files:** `StreamPlaybackHelper.kt`

**Prompt for developer:**

> Add one temporary `Timber.d("S1129: ...")` success probe at the adopted-frame entry required for `BlockNeedUserTest`. Keep it under 120 characters and do not add ticket ids to permanent logs.

**Verification:**

- Exactly one `Timber.d("S1129:` line exists under app production Kotlin.
- The probe executes only after an accepted ingest.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every Step 03.* is `[x] done`.
- [x] `./a.ps1 fk` passes (`expected: 0 | actual: 0`).
- [x] `./a.ps1 fg` passes for the touched surface (`expected: 9 PASS | actual: 9 PASS`).
- [x] Lifecycle/listener/player ownership audit reports no P0/P1 finding.

---

## Handoff Notes to Next Phase

Implementation is device-gated: open a stream, return to grid, and verify immediate plus restart persistence.

Boundary audit: P0=0, P1=0, P2=0, P3=0. The Activity Result registration is lifecycle-owned,
the RecyclerView scroll listener is removed in `onDestroy`, and both capture paths retain their
existing player/view release contracts. The `architecture` registry record is affected and its
Internet Streams section is updated in Phase 04; the remaining matched architecture documents are
unchanged because no dependency, database schema, flavor matrix, or link-receive contract changed.

---

## Rollback Plan

Restore direct Activity launch and direct snapshot-manager cache/store writes; no data migration.
