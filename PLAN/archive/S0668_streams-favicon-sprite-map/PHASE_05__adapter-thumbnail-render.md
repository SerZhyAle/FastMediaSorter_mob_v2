# Phase 05 - Adapter leading-thumbnail render + empty-slot fallback

**Strategic spec:** [`../S0668_streams-favicon-sprite-map.md`](../S0668_streams-favicon-sprite-map.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 03 (sidecar), Phase 04 (slicer)
**Blocks:** Phase 06
**Steps done:** 0 / 4

---

## Objective

Render a leading favicon thumbnail in each stream row, sliced from the atlas via the slicer + coords sidecar, coexisting with the existing play-status bullet (`ivPlayStatus`) and kind icon (`ivKind`). A row with no favicon shows an empty slot (no placeholder, owner decision). No row-scroll jank.

---

## Prerequisites

- [ ] Phase 04 merged: `FaviconAtlasSlicer.tileFor(index)` available.
- [ ] Decision read: empty slot fallback, keyed by `url`.
- [ ] NOTE: `res/layout/item_stream_source.xml` has NO `layout-land` counterpart - this is intentional (RecyclerView row reused across orientations). Do NOT create a land layout (CLAUDE.md Rule 11 N/A here; see research §3.3).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/item_stream_source.xml` | Modified | ≤ 150 |
| `app_v2/.../ui/streams/StreamSourceAdapter.kt` | Modified | ≤ 260 |
| `app_v2/.../ui/streams/StreamsFragment.kt` (or the screen that builds the adapter + observes import) | Modified | ≤ 30 |

> Confirm the fragment/screen that constructs `StreamSourceAdapter` and triggers import via `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*Streams*"` before wiring `invalidate()`.

---

## Steps

### Step 05.1 - Add the leading favicon ImageView to the row layout

**Files:** `app_v2/src/main/res/layout/item_stream_source.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add an `ImageView id=ivFavicon` at the LEADING edge of the row (before `ivPlayStatus`, or between `ivPlayStatus` and `ivKind` - pick the order that reads as "channel logo, then status, then kind" and keep it visually balanced). Size ~24-28dp with a small `layout_marginEnd`. `android:contentDescription` = a new string `streams_favicon` (Phase 06 adds EN/RU/UK). It coexists with `ivPlayStatus` (14dp) and `ivKind` (32dp) - do NOT remove or repurpose either. Use `?attr`/`@color`, never a hex literal (Rule 19). Default `android:visibility="gone"` (shown only when a tile exists -> empty slot otherwise). Ensure the row min-height and the existing leading icons still align centre-vertically.

**Verification:**

- `Grep` - `@+id/ivFavicon` exists in `item_stream_source.xml`.
- `Grep` - both `@+id/ivPlayStatus` and `@+id/ivKind` STILL exist (not removed).
- `Grep` - no raw hex `#` color is introduced in the row layout by this change.
- `.\a.ps1 fr` - resources/manifest pass (exit 0).

**Status:** `[ ]`

---

### Step 05.2 - Bind the favicon tile in the ViewHolder

**Files:** `app_v2/.../ui/streams/StreamSourceAdapter.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Give the adapter access to the favicon slicer + coords (constructor param `faviconResolver: (String) -> Int?` returning the index for a url, plus the `FaviconAtlasSlicer`, OR a single injected collaborator - keep it a plain function/interface so the adapter stays test-friendly and does not reach into DI). In `bind()`: look up `index = faviconResolver(source.url)`; if null -> `ivFavicon.visibility = GONE` (empty slot). If non-null -> launch on a view-bound scope, `tileFor(index)`; on a non-null bitmap set it and `VISIBLE`, on null keep `GONE`. Cancel/guard the async load on rebind so a recycled row does not show a stale tile (track the bound url and drop the result if the holder was rebound - mirror the existing `setPlayingId` rebind-safety discipline). Do NOT block `onBindViewHolder`. Do NOT collect a view-bound Flow with a bare `lifecycleScope.launch{collect{}}` (Rule 19).

**Verification:**

- `Grep` - `ivFavicon` is bound in `StreamSourceAdapter.kt` and set to `GONE` on the null-index branch.
- `Grep` - the async tile load guards against rebind (a bound-url/position check before applying the bitmap).
- `Grep` - `bindPlayStatus(` is still called (the status bullet is untouched).
- `.\a.ps1 fk` - compiles (exit 0).

**Status:** `[ ]`

---

### Step 05.3 - Wire the resolver + invalidate-on-import at the screen

**Files:** `app_v2/.../ui/streams/StreamsFragment.kt` (or the adapter-owning screen)
**Depends on:** Step 05.2

**Prompt for developer:**

> Where the screen builds `StreamSourceAdapter`, supply the `faviconResolver` from the loaded `FaviconAtlasStore.coords()` (load the coords map when the list loads / after an import) and the `FaviconAtlasSlicer` bound to `FaviconAtlasStore.atlasFile()`. After a successful catalog import refreshes the list, call `slicer.invalidate()` and reload the coords map so new favicons appear. Keep all of this off the UI thread (coords read is suspend). No business logic in the fragment beyond wiring - if it grows, push it into a `helpers/*Manager.kt` per Rule 3.

**Verification:**

- `Grep` - the screen passes a `faviconResolver`/coords source into `StreamSourceAdapter(`.
- `Grep` - `invalidate(` is called after an import refresh.
- `.\a.ps1 fc` - code + resources compile (exit 0).

**Status:** `[ ]`

---

### Step 05.4 - Adapter render unit/UI test (empty slot + present tile)

**Files:** `app_v2/src/test/java/.../StreamSourceAdapterFaviconTest.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> Test that the bind logic: (a) with `faviconResolver` returning null for a url, leaves `ivFavicon` GONE (empty slot); (b) with a resolver returning an index and a slicer stub returning a 32x32 bitmap, sets `ivFavicon` VISIBLE with a non-null drawable; (c) a rebind to a null-favicon url after a present one returns to GONE (no stale tile). Use a fake slicer/resolver (no real atlas needed) so the test is deterministic and fast. Robolectric for the ViewHolder inflate.

**Verification:**

- `Glob` - `StreamSourceAdapterFaviconTest.kt` exists.
- `.\gradlew.bat :app_v2:testStandardDebugUnitTest --tests "*StreamSourceAdapterFaviconTest*"` - the class passes (read per-class XML).

**Status:** `[ ]`

---

## Phase Done Criteria

- [ ] Every `Step 05.*` is `[x] done`.
- [ ] A row with a favicon index shows the sliced thumbnail; a row without one shows an empty slot (no placeholder).
- [ ] The play-status bullet and kind icon are unchanged and still render.
- [ ] No land layout was created; the async tile load is rebind-safe and off the main thread.
- [ ] `.\a.ps1 fc` passes; the adapter render test passes.
- [ ] Dev log entry added; `catalog_sync.ps1 -Module app_v2` run.

---

## Handoff Notes to Next Phase

The user-visible string `streams_favicon` (contentDescription) is referenced here and ADDED in Phase 06 (EN/RU/UK). Phase 06 also records the capability and refreshes docs/catalog. On-device proof of real favicons rendering is the EXTERNAL closure step (needs the real published atlas).

---

## Rollback Plan

Revert the phase commit: remove `ivFavicon` from the layout, restore the adapter constructor + `bind()`, drop the wiring + test. Phases 01-04 stay (sidecar persisted but unrendered).
