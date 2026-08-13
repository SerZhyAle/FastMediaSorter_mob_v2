**Status:** Archived

# S1169 - Unified update policy for stream (video-broadcast) thumbnails

## 0. Origin

Owner observation (remote log review, 2026-07-24): stream thumbnails do "excessive magic" -
blink/flicker and refresh redundantly. Logs (`fastmediasorter_20260723_115133/115325/180744`)
confirm active streams usage on Samsung SM-S731B / Android 16 but carry no thumbnail error lines -
the defect is visual/architectural, not a logged crash. Root-caused via read-only research over the
stream-frame subsystem.

## 1. Root cause (single lever)

Captured-frame lifecycle (transient, cache-only) and play-outcome/status lifecycle (Room-backed,
DB-observed) are entangled on the SAME `StreamSourceEntity` row and the SAME `StreamsUiState.sources`
list.

Chain: a frame capture completes -> `onOutcome` unconditionally writes `lastPlayOutcomeAt =
System.currentTimeMillis()` (`StreamSourceRepository.recordPlayOutcome`, unconditional UPDATE) ->
Room `InvalidationTracker` fires -> `StreamsViewModel.combine(observeStreamSources(), filter)`
re-emits the whole `StreamsUiState` -> `submitList` full DiffUtil pass (no `getChangePayload`) ->
every changed row full-rebinds AND `prewarmPersistedFrames` cancel-restarts from index 0.

## 2. Concrete defects (from research)

- Unthrottled re-capture loop for unreachable channels: `bind()` sees `frame == null` ->
  `requestCapture(url)` with no backoff; each failed probe writes DB -> re-emit -> rebind ->
  requestCapture, at ~12 s capture-timeout cadence, not the intended 60 s. (High) Anchors:
  `StreamGridAdapter.kt:156-162`, `StreamFrameSnapshotManager.kt:117-122`,
  `StreamSourceRepository.kt:77-78`.
- Prewarm sweep restarts on every unrelated catalog DB write (incl. the capture's own outcome),
  may never finish for large catalogs. (High) `StreamGridModeManager.kt:123-131`, `:160-173`.
- No `getChangePayload` on either stream `DiffUtil.ItemCallback` -> any field change forces full row
  rebind (menu rebuild, listener re-attach, redundant `setImageBitmap`). (Med) In-repo precedent to
  copy: `MediaFileDiffCallback.kt:19-42` + `MediaFileAdapter.kt:450-482`.
- Grid-open placeholder flash despite a persisted disk thumbnail: `submitList` runs before the async
  prewarm restores frames, so tiles flash favicon -> real thumbnail on every open. (Med)
  `StreamGridModeManager.kt:94-101`.
- `StreamPanelChannelAdapter.notifyDataSetChanged()` on `setShowLabels`/`refreshFavicons` blanks then
  reloads every pinned chip on every rotation/atlas-load (bind clears image first). (Med)
  `StreamPanelChannelAdapter.kt:38-45,82`; callers `MainStreamsPanelManager.kt:95,164`.
- `StreamFrameCache.MAX_ENTRIES = 64` LRU can evict still-visible tiles in catalogs > 64 rows,
  forcing re-capture. (Med) `StreamFrameCache.kt:28-31,76`.
- Two fully duplicated pipelines (main + pinned sections) - any fix must be applied twice. (Low)
  `StreamsActivity.kt:179-251`.

## 3. Proposed unified policy

- Single source of truth: catalog-metadata Flow (names, order, pin, status) is separate from the
  frame stream. A frame capture/probe MUST NOT re-emit the catalog list. Move `lastPlayOutcome*` off
  the list-driving path (separate table/DAO, or make the UPDATE conditional + exclude the timestamp
  column from the observed query, or deliver frames purely out-of-band via the cache + single-item
  `notifyItemChanged` and never write the DB from a capture).
- Capture cadence: capture only on (a) first appearance of a tile with no cached frame, (b) explicit
  pull-to-refresh, (c) the 60 s periodic sweep for visible tiles. Never from an incidental rebind.
- Backoff: a tile whose last capture failed gets a cooldown (fixed or exponential) before any
  re-capture; a dead channel must not re-probe faster than the periodic sweep.
- Diffing: both stream adapters override `getChangePayload`; a status-only change repaints just the
  status bullet, never the frame `ImageView`, menu, or listeners.
- No blank-swap: never clear an already-loaded frame/favicon before its replacement is ready; a
  recycled cell keeps its last image until the new bitmap lands (guard by `boundUrl`).
- Prewarm: restore persisted frames once per GRID entry (or track a per-item "attempted" set), not
  once per `submitCurrentList`; do not cancel-restart on unrelated DB writes.
- Panel adapter: `StreamPanelChannelAdapter` moves to `submitList`/DiffUtil for label/favicon
  refresh instead of `notifyDataSetChanged()`.
- Cache sizing: replace/augment the fixed `MAX_ENTRIES = 64` with the disk-budget model so a visible
  tile is not evicted mid-view.

## 4. Resolved decisions (proposed defaults; owner may override at tactical review)

- Decouple lever: YES. Keep `lastPlayOutcome*` off the list-driving path. Preferred mechanism -
  make `markPlayOutcome` a conditional UPDATE and EXCLUDE the outcome/timestamp columns from the
  `observeStreamSources()` query projection, so a probe write cannot invalidate the catalog Flow.
  Frame delivery stays out-of-band (cache + single-item `notifyItemChanged`). This avoids a schema
  migration while cutting the feedback loop.
- Backoff: hybrid - a failed capture is NOT retried from an incidental rebind at all (rebind reads
  cache only); re-capture happens solely via the 60 s visible-tile sweep, and a per-url failure
  timestamp enforces an exponential floor (60 s -> 5 min cap) so chronically dead channels back off.
- LIST mode (`StreamSourceAdapter`, favicon-only) stays favicon-only - deliberate; out of scope for
  frame rendering. It still gains `getChangePayload` + no blank-swap.
- Section pipelines: consolidate into one parameterized `StreamGridModeManager`/snapshot manager
  instance driven by a section key, sharing one `StreamFrameSnapshotManager` queue - removes the
  duplicated policy surface. Kept as a dedicated phase so it can be dropped if risk is too high.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1137 (FFmpeg AAC radio fix, same streams area, present in the analysed build), S1118 (radio-stream buffer tolerance), S1146 (radio seek/rebuffer), S1148 (radio LoadControl) - adjacent streams-subsystem work, no code overlap with the thumbnail pipeline.
- **Scope decisions:** per §4 - decouple `lastPlayOutcome*` from the observed catalog query (no schema migration), hybrid backoff (sweep-only re-capture + exponential floor), LIST mode stays favicon-only, consolidate the two section pipelines into one parameterized manager.
- **Delivered capability:** stream thumbnails update without flicker or redundant re-capture; dead channels back off instead of re-probing every ~12 s.

**Tactical plan:** `PLAN/S1169_stream-thumbnail-update-policy/INDEX.md`

## Last Audit

### Manual (device test, 2026-07-24)

- Verdict: PASS on all four status-note checks. Evidence bundle: `temp/S1169/EVIDENCE.md`, raw logs `temp/S1169/logcat-grid1.txt` (grid run) and `temp/S1169/logcat-panel.txt` (rotation run).
- Environment: emulator-5554, sdk_gphone64_x86_64, Android 15 (SDK 35), 1080x2424 @ 420dpi, network reachable. Build under test `com.sza.fastmediasorter.debug` 2.60.7241.433-DEBUG.
- Flavor deviation from the status note: tested on **standard**, not noLegal. Standard also ships `SUPPORT_STREAMS=true` and exercises the same `src/main` classes (`StreamGridAdapter`, `StreamFrameSnapshotManager`, `StreamPanelChannelAdapter`), so the policy surface under test is identical.
- Catalog used: bundled channel list filtered to Video (real IPTV urls, naturally mixed reachable/unreachable) plus 6 manually added channels (3 live HLS, 3 unroutable).
- Check 1 (no favicon<->thumbnail flashing): PASS indirectly. Three `screencap` grabs 3 s apart on an idle grid were byte-identical (md5 `d12a441df130b1f9b23713d000d3da5e`); no full-list refresh path ran (status changes arrived as payloads, see check 3) and no `notifyDataSetChanged` exists in either stream adapter. Not proven frame-accurately - no video capture was taken.
- Check 2 (dead channels do not re-probe continuously): PASS. `S1169: capture skipped by backoff` fired 216 times. Per-url re-attempt gaps for channels whose capture failed: 165 s (`91.146.94.234:10001/play/a088`), 189 s (`live.x2.co.th/live/13livetv-th.m3u8`), 223 s (`cdn.jmvstream.com/.../playlist.m3u8`) - all far above the 60 s backoff floor and nowhere near the old ~12 s capture-timeout cadence.
- Check 3 (status dot repaints in place): PASS. `S1169: grid status-only repaint` fired 7 times over the grid run, i.e. every observed outcome write reached the tile through `StreamAdapterPayloads.STATUS`, never a full rebind.
- Check 4 (pinned chips do not blank on rotation): PASS. With 3 pinned channels and the main-window streams panel enabled, 4 portrait<->landscape rotations produced exactly 12 `S1169: panel label-only repaint` lines (4 x 3 chips); all 3 chips still carried their favicon after the sweep.
- Stability: zero `FATAL EXCEPTION` / `ANR in` / `E/AndroidRuntime` lines in either run.
- Test-setup finding (not a defect of this ticket): `StreamsActivity` opens in LIST display mode on every create, so GRID must be re-selected explicitly. A first scenario run that assumed GRID persisted produced zero probes because no frame capture runs in LIST mode.

## 5. Notes

- Whole subsystem is unit-test-blind (`StreamFrameCache`, `StreamFrameSnapshotManager`,
  `StreamGridAdapter`, `StreamGridModeManager`, `StreamPanelChannelAdapter`) - add coverage with the
  fix.
- Build in logs (2.60.7230.329-NoLegal-DEBUG) already carries the S1137 AAC fix
  (`S1137: playback renderers factory - extension mode=ON`); radio mp3 stream decoded cleanly - not
  related to this ticket, recorded to avoid confusion.
