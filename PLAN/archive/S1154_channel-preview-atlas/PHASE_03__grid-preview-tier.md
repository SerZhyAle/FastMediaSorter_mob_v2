# Phase 03 - Grid preview tier

**Strategic spec:** [`../S1154_channel-preview-atlas.md`](../S1154_channel-preview-atlas.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (code; visual render device-gated -> BlockNeedUserTest)
**Depends on:** Phase 02
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-07-23
**Completed:** 2026-07-23

---

## Objective

Insert the atlas-preview tier into the grid fallback chain - captured frame > atlas preview (VIDEO only) > favicon > empty (ADR-3) - via a single new adapter lambda, and wire the store/slicer into every grid adapter instance in `StreamsActivity`. A captured frame keeps winning, so a user's own frame evicts the atlas preview automatically.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (`ChannelPreviewAtlasStore` + `ChannelPreviewAtlasSlicer` exist).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamGridAdapter.kt` | Modified | ≤ 340 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt` | Modified | ≤ 1050 |

> No `res/layout/*.xml` edits (the tile reuses `ivFrame`; `item_stream_grid_cell.xml` has no landscape variant and is untouched) - no landscape-parity obligation.
>
> **Flavor placement.** Both files are `src/main`; the atlas loader is inert where no atlas is present. No `BuildConfig.*` guard.
>
> **Note.** `StreamsActivity` is ~1010 LOC (limit 1500). This phase adds only wiring lines; keep it under 1500. If it would exceed, extract the atlas wiring into `StreamsSectionsManager` / a small helper rather than growing the Activity.

---

## Steps

### Step 03.1 - Add the atlas-preview tier to the adapter

**Files:** `ui/streams/StreamGridAdapter.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add ONE new constructor lambda `atlasPreviewLoader: suspend (url: String) -> Bitmap? = { null }` (do not add a separate resolver + loader pair - the adapter already carries `@Suppress("LongParameterList")`; keep it to one lambda, risk §7). In `bind()`'s no-frame branch, BEFORE `bindFavicon(url)`, and only for a captureable VIDEO source, launch (reuse the existing `faviconScope`/job pattern, rebind-safe via the `boundUrl` guard) to fetch `atlasPreviewLoader(url)`; if non-null, set it on `binding.ivFrame` and set a preview `contentDescription` (`R.string.streams_grid_cell_cd` with the title - the tile is no longer "no frame"). On null, fall through to the existing favicon path. The captured-frame branch is unchanged and still wins (own frame evicts the atlas preview).

**Verification:**

- `Grep` - `atlasPreviewLoader` present in `StreamGridAdapter.kt` (constructor param + call site).
- `Grep` - the adapter still declares exactly one `@Suppress("LongParameterList")` (no new suppressions added).
- `Grep -n "lifecycleScope.launch"` - zero bare view-bound collections added (reuse `faviconScope`).
- `.\a.ps1 fk` compiles.

**Status:** `[x]` done

---

### Step 03.2 - Wire the store/slicer into StreamsActivity

**Files:** `ui/streams/StreamsActivity.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Inject `ChannelPreviewAtlasStore` (field, mirroring `faviconAtlasStore`). Build a lazy `ChannelPreviewAtlasSlicer { channelPreviewAtlasStore.atlasFile() }` and load an `@Volatile private var atlasPreviewCoords: Map<String, Int>` alongside `faviconCoords` (same reload point after import). Pass `atlasPreviewLoader = { url -> atlasPreviewCoords[url]?.let { atlasSlicer.tileFor(it) } }` to ALL FOUR grid adapters (`gridAdapter`, `pinnedGridAdapter`, and the two managers' adapters if separate). Invalidate the slicer where `faviconSlicer`/coords are reloaded post-import.

**Verification:**

- `Grep` - `ChannelPreviewAtlasStore` injected in `StreamsActivity.kt`.
- `Grep` - `atlasPreviewLoader =` appears for every grid adapter construction (count matches the grid adapter count, i.e. 2).
- `Grep` - `atlasPreviewCoords` reloaded at the same site as `faviconCoords`.
- `.\a.ps1 fk` compiles; `StreamsActivity.kt` LOC < 1500 (`(Get-Content ...).Count`).

**Status:** `[x]` done

---

### Step 03.3 - Insert the BlockNeedUserTest probe tag

**Files:** `ui/streams/StreamGridAdapter.kt`
**Depends on:** Step 03.1, Step 03.2

**Prompt for developer:**

> At the atlas-preview bind entry point (the branch that applied an atlas tile), add exactly one `Timber.d("S1154: grid atlas-preview tile applied")`. This is the changed-flow entry probe required before the ticket enters `BlockNeedUserTest` (CLAUDE.md §2). One tag only; it is removed by `/spec-check` on `Verified`.

**Verification:**

- `Grep` - exactly one `Timber.d("S1154:` line across all `.kt` files.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] Phase-boundary audit - no unresolved P0/P1. Focus: coroutine/rebind safety of the new tile load (bound-url guard, job cancel on recycle), Bitmap ownership on `ivFrame`, no main-thread decode.
- [ ] **Device-gated (defer to BlockNeedUserTest):** visual verification that a VIDEO tile with no captured frame shows the atlas preview, and that opening the channel then returning shows the captured frame in its place. Requires the real atlas binary from Phase 06.

---

## Follow-up defect (2026-07-26, owner screenshot)

The tier chain assumed a tile always has SOMETHING to show. It does not: about a third of catalog
channels carry no `favicon_index` at all (2438 of 3688 in the published CSV), and those tiles rendered
as an empty grey rectangle - `setImageBitmap(null)` with no fallback. The owner's radio grid was a
screen of blank cells. Two fixes in `StreamGridAdapter`:

- the bind now starts from the media-kind glyph (`ic_audio` / `ic_video`, tinted `colorOnSurfaceVariant`,
  inset by `stream_grid_placeholder_padding`), which the async favicon/atlas paths overwrite when they
  resolve, so a tile is never empty;
- a favicon is a 32 px source, so it is drawn `FIT_CENTER` with the same inset instead of `CENTER_CROP` -
  upscaling it edge to edge turned every icon into a blur.

Verified on emulator-5554: a radio channel with a favicon shows a crisp inset icon, and one without
(`* COUNTRY || Country Po..`) shows the note glyph.

## Handoff Notes to Next Phase

- The grid now renders the atlas tier when an atlas is present. End-to-end render is only observable once Phase 06 publishes a real binary and it is downloaded on device.

---

## Rollback Plan

Revert the phase commit(s). Removing the one lambda + wiring restores the prior frame>favicon>empty chain - no data or schema change.
