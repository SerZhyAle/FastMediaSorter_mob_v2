# Phase 03 - Channel icon for audio streams

**Strategic spec:** [`../S1382_background-audio-note-stream-icons.md`](../S1382_background-audio-note-stream-icons.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-08-05
**Completed:** 2026-08-05

---

## Objective

Show the channel's own favicon in the bar while an audio stream plays, and fall back to the rotating note when the channel has none.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `MediaItem.requestMetadata.mediaUri` is populated on the single-item service path (Step 01.1).
- [ ] `showStillArtwork` / `showRotatingNote` / `hideBar` exist in `NowPlayingManager` (Phase 02).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ 1450 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt` | Modified | ≤ 980 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/NowPlayingManager.kt` | Modified | ≤ 400 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern). `PlayerActivity.kt` (1410 LOC) and `PlayerManagerInitializer.kt` (953 LOC) both cross 500 LOC, so Step 03.1 opens with an explicit backup sub-step. `PlayerActivity.kt` is 90 lines under the 1500 ceiling and this phase adds two lines to it.
>
> No flavor source set is involved: `docs/FLAVOR_MATRIX.md` shows `SUPPORT_STREAMS` and `ENABLE_PERSISTENT_AUDIO_PLAYBACK` enabled in the same four flavors, so the existing `persistentAudioCompiledIn` constructor gate already covers this branch.

---

## Steps

### Step 03.1 - Hand the bar an atlas store and a coroutine scope

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/NowPlayingManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> First copy `PlayerActivity.kt` and `PlayerManagerInitializer.kt` to `temp/S1382/` with a timestamped name (CLAUDE.md Rule 5, both files exceed 500 LOC). Then add `@Inject lateinit var faviconAtlasStore: FaviconAtlasStore` to `PlayerActivity` alongside the other injected singletons. Add two constructor parameters to `NowPlayingManager`: `private val faviconAtlasStore: FaviconAtlasStore` and `private val scope: CoroutineScope`. In `PlayerManagerInitializer`, pass `activity.faviconAtlasStore` and `activity.lifecycleScope` at the single construction site.

**Why:**

Strategic §4 records that neither `FaviconAtlasStore` nor `FaviconAtlasSlicer` is reachable from `NowPlayingManager` today, and both the coords map and the tile decode are suspend functions, so the manager needs an injected store and a scope before the icon branch can exist at all.

**Verification:**

- `Glob` - a timestamped copy of each file exists under `temp/S1382/`.
- `Grep` - `faviconAtlasStore: FaviconAtlasStore` matches exactly once in `PlayerActivity.kt`.
- `Grep` - `private val faviconAtlasStore: FaviconAtlasStore` and `private val scope: CoroutineScope` each match exactly once in `NowPlayingManager.kt`.
- `Grep` - `activity.lifecycleScope` present in `PlayerManagerInitializer.kt` at the `NowPlayingManager(` call.
- `.\a.ps1 fk` exits 0 - the constructor change must not leave the single call site behind.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 5\5 PASS. Backups: temp/S1382/PlayerActivity.kt.20260805_121710.bak, temp/S1382/PlayerManagerInitializer.kt.20260805_121710.bak. `fk` exit 0. `FaviconAtlasStore` imported rather than written as a 109-char FQN, so the injected field stays on one line.

---

### Step 03.2 - Resolve the channel tile for the playing URL

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/NowPlayingManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add `private val faviconSlicer by lazy { FaviconAtlasSlicer { faviconAtlasStore.atlasFile() } }`, a `private var faviconCoords: Map<String, Int>? = null` cache and a `private var appliedIconUrl: String? = null` marker. Add `private fun Player.isLiveStreamItem(): Boolean = isCurrentMediaItemLive || !isCurrentMediaItemSeekable`. Add `private fun tryShowStreamIcon(player: Player, isPlaying: Boolean)`: read the URL from `player.currentMediaItem?.requestMetadata?.mediaUri?.toString()`, return early with `showRotatingNote(isPlaying)` when it is null or the item is not a live stream, return with no change when the URL already equals `appliedIconUrl`, otherwise call `showRotatingNote(isPlaying)` and launch on `scope`: load `faviconCoords` once from `faviconAtlasStore.coords()`, look up the index, fetch `faviconSlicer.tileFor(index)`, and only when `observedPlayer`'s current URL still equals the captured one call `noteAnimator?.stopNote()`, set `noteShown = false`, `miniBar?.miniArtwork?.setImageBitmap(tile)` and `appliedIconUrl = url`. A missing index or a null tile leaves the rotating note in place. Clear `appliedIconUrl` in `hideBar()`.

**Why:**

Strategic §2 goal 3 wants the stream recognised by its own icon while §11 criterion 3 requires a channel without one to keep the rotating note rather than an empty slot, and strategic §7 warns that a blind edit here is only caught by the user - the staleness check is what stops a slow tile decode from landing on a track that has already changed.

**Verification:**

- `Grep` - `private fun tryShowStreamIcon` matches exactly once.
- `Grep` - `isCurrentMediaItemLive || !isCurrentMediaItemSeekable` matches exactly once.
- `Grep` - `requestMetadata?.mediaUri` matches at least twice (initial read and staleness re-read).
- `Grep` - `appliedIconUrl = null` present inside `hideBar`.
- `Grep` - `FaviconAtlasSlicer {` matches exactly once in `NowPlayingManager.kt`.
- `Grep` - `GlobalScope` returns zero hits in `NowPlayingManager.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 6\6 PASS. Files: ui/player/helpers/NowPlayingManager.kt (+45 LOC).

---

### Step 03.3 - Order the three states in `populateBarContent`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/NowPlayingManager.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Rewrite the artwork selection in `populateBarContent` to try the stream branch first: when `player.isLiveStreamItem()` is true call `tryShowStreamIcon(player, player.isPlaying)`; otherwise keep the existing choice between `showStillArtwork(artworkUri)` and `showRotatingNote(player.isPlaying)`, and set `appliedIconUrl = null` on that non-stream path so a later stream re-resolves its icon. Add a comment explaining that a local file can read as non-seekable for one frame while the timeline is still empty, which is why the stream branch falls back to the note instead of clearing the slot.

**Why:**

Strategic §5 fixes the precedence as channel icon, then album art, then note, and strategic §11 criterion 4 requires existing album art to keep showing motionless for local files.

**Verification:**

- `Grep` - `tryShowStreamIcon(player` present inside `populateBarContent`.
- `Grep` - `showStillArtwork(` matches at least twice (declaration plus call site).
- `Grep` - `isLiveStreamItem()` matches at least three times (declaration plus two call sites).
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 4\4 PASS. `fk` exit 0. Debug tags for `BlockNeedUserTest` inserted after this step, before the final build: four `Timber.d("S1382: ..")` probes at the changed flow entries (`populateBarContent` state choice, `tryShowStreamIcon` atlas lookup, `applyNoteRotation`, `hideBar`).

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 d` exit 0, APK `FastMediaSorter_standard_debug_v2.60.8041.533-DEBUG.apk`. Single build validating implementation plus the four debug probes.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added via `scripts/post-change.ps1` - verdict `post-change: PASS`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated by the same facade run.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

**Phase notes:**

- UI-phase screenshot gate (S1338): placement decision recorded (strategic §6 "Quiz decisions (2026-08-05)", owner rulings quoted verbatim; no layout file touched in any phase). Screenshot captured this phase: `temp/scratch/emulator-5554_20260805_122427.png`. **What it proves and what it does not:** the build installs and launches on emulator-5554 with no crash (`adb.ps1 log` grep for `FATAL|AndroidRuntime` returned 0 lines), but a fresh install lands in the onboarding wizard, so the frame does not show the background-playback bar. Reaching the bar needs indexed media or a live stream, and strategic §7 already records that an AVD cannot prove the rotation at all. That evidence is what `BlockNeedUserTest` hands to the device test - it is not claimed here.
- Gate ordering: `assert-no-ticket-logs` rejects `S1382:` probes while the ticket is not `BlockNeedUserTest`, so the status flip was made before the closure run rather than after Phase 04.

---

## Handoff Notes to Next Phase

All three visual states of `miniArtwork` are live and the rotation stops on every hide path. What remains is the inventory record and the catalog/dev-log closure.

---

## Rollback Plan

Revert phase commit(s) - the constructor widening and the icon branch are additive; reverting leaves Phase 02's rotating note working on its own.
