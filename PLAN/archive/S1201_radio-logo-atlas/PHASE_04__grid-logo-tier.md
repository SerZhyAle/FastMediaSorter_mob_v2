# Phase 04 - Grid logo tier

**Strategic spec:** [`../S1201_radio-logo-atlas.md`](../S1201_radio-logo-atlas.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** -
**Completed:** 2026-07-26

---

## Objective

Insert the logo tile between the preview-atlas tier and the favicon tier in the stream grid, so a channel with no capturable frame gets a full-size picture instead of an upscaled 32 px icon.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (`StreamLogoAtlasStore` + `StreamLogoAtlasSlicer` exist).
- [ ] Phase 03 is ✅ Done (`DeliverableSet.STREAM_LOGO_ATLAS` exists).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamGridAdapter.kt` | Modified | ≤ 450 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt` | Modified | ≤ 1500 |

> No `res/layout/*.xml` edit - the tile reuses `ivFrame` and its existing constraints, so no landscape counterpart is due (`item_stream_grid_cell.xml` has no `layout-land` variant, and this phase does not create the need for one). Both files are `src/main`; the tier is inert with no payload installed, so no flavor guard.

---

## Steps

### Step 04.1 - Add the logo tier to the adapter

**Files:** `ui/streams/StreamGridAdapter.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add ONE constructor lambda `logoTileLoader: suspend (url: String) -> Bitmap? = { null }` next to the existing `atlasPreviewLoader`. Do not add a second resolver/loader pair - the constructor already carries `@Suppress("LongParameterList")` and must not grow another suppression. In `bind()`'s no-frame path, after the atlas-preview attempt returns null (and for every media kind, not just VIDEO), fetch `logoTileLoader(url)` inside the SAME `faviconScope` job, guarded by the existing `boundUrl` rebind check. On a non-null bitmap render through a new `showLogo(..)`: the tile is SQUARE (strategic ADR-1), so it is fitted into the 16:9 cell with `FIT_CENTER` and a small inset (`R.dimen.stream_grid_logo_padding`) rather than cropped like a captured frame. On null fall through to the existing favicon path unchanged. The captured-frame branch still wins, and the preview-atlas branch still precedes this one.

**Verification:**

- `Grep` - `logoTileLoader` present in `StreamGridAdapter.kt` as a constructor parameter and at exactly one call site.
- `Grep` - the adapter still declares exactly one `@Suppress("LongParameterList")`.
- `Grep -n "lifecycleScope.launch"` - zero bare view-bound collections added.
- `Grep` - `showLogo` uses `FIT_CENTER`, not `CENTER_CROP` (a square tile cropped to 16:9 would slice the logo).
- `.\a.ps1 fk` compiles.

**Status:** `[x]` done

---

### Step 04.2 - Wire the store and slicer into StreamsActivity

**Files:** `ui/streams/StreamsActivity.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Inject `StreamLogoAtlasStore` as a field mirroring `channelPreviewAtlasStore`. Build a lazy `StreamLogoAtlasSlicer { streamLogoAtlasStore.atlasFile() }` and load `@Volatile private var logoAtlasCoords: Map<String, Int>` at the same site where `atlasPreviewCoords` is loaded, including the `onStart` re-read that recovers a payload installed while the screen was backgrounded. Pass `logoTileLoader = { url -> logoAtlasCoords[url]?.let { logoSlicer.tileFor(it) } }` to every grid adapter construction. Extend the existing `reloadAtlasPreviews()` to also invalidate the logo slicer and reload its coords, so installing either atlas refreshes the grid.

**Verification:**

- `Grep` - `StreamLogoAtlasStore` injected in `StreamsActivity.kt`.
- `Grep` - `logoTileLoader =` appears once per grid adapter construction; the count matches the `atlasPreviewLoader =` count.
- `Grep` - `logoAtlasCoords` reloaded at the same site as `atlasPreviewCoords`, and inside `onStart`.
- `(Get-Content StreamsActivity.kt).Count` < 1500 - record `expected: <1500 | actual: <n>`.
- `.\a.ps1 fk` compiles.

**Status:** `[x]` done

---

### Step 04.3 - Offer the logo atlas after a catalog refresh

**Files:** `ui/streams/helpers/StreamAtlasPromptManager.kt`, `ui/streams/StreamsActivity.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Generalise `StreamAtlasPromptManager` so it can offer either atlas: replace its hard-coded `CHANNEL_PREVIEW_ATLAS` lookup with a constructor-supplied `DeliverableSet` plus the string resource for the offer, keeping the existing indefinite-Snackbar behaviour, the offered-latch reset on non-action dismissal, and the `onAtlasInstalled` callback. Construct a second instance in `StreamsActivity` for `STREAM_LOGO_ATLAS`, offered after the preview-atlas offer is settled so the two Snackbars cannot stack. Reuse the strings added in Phase 03 - do not introduce new user-visible copy here.

**Verification:**

- `Grep` - `StreamAtlasPromptManager` constructor takes a `DeliverableSet` parameter.
- `Grep` - `STREAM_LOGO_ATLAS` referenced in `StreamsActivity.kt`.
- `Grep -n "LENGTH_INDEFINITE"` - still present in the prompt manager (the offer must not vanish behind a toast).
- `.\a.ps1 fk` compiles.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] Phase-boundary audit run - focus: rebind safety of the new tile load (bound-url guard, job cancelled on recycle), no `Bitmap` leaked on `ivFrame` across rebinds, no main-thread decode, and the two Snackbar offers not competing for the same anchor.

---

## Handoff Notes to Next Phase

- The grid renders the logo tier when a payload is present. End-to-end render is only observable once Phase 05 publishes the real binary and it is downloaded on device.

---

## Rollback Plan

Revert the phase commit(s). Removing the one lambda plus its wiring restores the prior frame > preview > favicon > glyph chain - no data or schema change.
