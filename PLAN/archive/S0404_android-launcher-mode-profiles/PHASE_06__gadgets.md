# Phase 06 - Gadgets

**Strategic spec:** [`../S0404_android-launcher-mode-profiles.md`](../S0404_android-launcher-mode-profiles.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 05B
**Blocks:** Phase 07, 08
**Steps done:** 6 / 6
**Started:** 2026-07-17
**Completed:** 2026-07-17

---

## Objective

Gadget contract + registry and the four iteration-1 gadgets (owner quiz 2026-07-17): clock (no weather), playlist, streams list, folder preview. All are our own interactive Views inside the grid (ADR-5), plugged through `LauncherCellViewBinder.gadgetBinder` (Phase 05B renamed the host; the hook signature is unchanged).

---

## Prerequisites

- [x] Phase 05B is ✅ Done (the 2D container; gadget cells only get a real height there).
- [x] CODE.LOCK acquired.

> **SUPERSEDED - do not implement.** An earlier note here (from the phases 01-04 audit, finding D4) told step 06.6 to force the gadget height: `itemView.layoutParams.height = LauncherGridGeometry.cellSizePx(recyclerViewWidthPx, spanCount) * cell.spanH`. The Phase 06 discovery sweep verified that workaround and found it incomplete (no width/spanCount reachable from the adapter, stale height after rotation, dead gap under 1-row row-siblings, `rowIndex`/`colIndex` still ignored). Owner decision 2026-07-17: **true 2D** (ADR-9), delivered by Phase 05B. `LauncherDesktopLayout` now measures every child `EXACTLY` at `cellSize × spanW/spanH`, so a gadget MUST NOT touch its own `layoutParams.height` - it would fight its parent. A gadget view simply fills the container it is handed.

### Discovery results (recorded 2026-07-17 - the steps below use these names)

The two placeholders in this phase are resolved. Do not re-research them:

- **`<MediaFilesSource>` = `GetResourcesUseCase.getById(id: Long): MediaResource?` then `GetMediaFilesUseCase.invoke(resource, ..): Flow<List<MediaFile>>`.** There is NO lightweight "N files by resourceId" API - `GetMediaFilesUseCase` takes a resolved `MediaResource`, not an id, and it can hit SMB/FTP/SFTP/Cloud. `MediaFile` fields the gadgets need: `name`, `path`, `type`, `createdDate` (this is "newest" - NOT `lastModified`, which is `0L` for network scanners), `title` (**often null** - metadata is only extracted when `rememberFileList` is on or the sort needs it, so PlaylistGadget must fall back to `name`), `contentUri`, `thumbnailUrl`, `isDirectory`.
- **`<StreamFaviconLoader>` = `FaviconAtlasStore` + `FaviconAtlasSlicer`.** No Glide, no network: `FaviconAtlasStore.coords(): Map<String, Int>` (suspend, IO) maps `url -> tile index`, `FaviconAtlasSlicer.tileFor(index): Bitmap?` (suspend, IO) crops a 32px tile from a persisted atlas PNG. Copy the rebind-safe pattern from `ui/main/helpers/StreamPanelChannelAdapter.kt:100-118` (resolve index sync, decode in a coroutine, guard a stale rebind via a bound-url check). `MainStreamsPanelManager` is the direct analog for the whole gadget.
- **`StreamSourceRepository.observeSources()` already returns `ORDER BY pinned DESC, sortIndex ASC, addedAt DESC`** - the gadget needs `.take(10)`, no sorting of its own. `StreamSourceEntity`: `id: String`, `title`, `pinned`, `sortIndex` (all as assumed). `getById` exists and is already wired into `ExecuteLauncherCommandUseCase.launchStream()`.
- **`PlayerActivity.createPanelIntent`** really does carry `initialFilePath: String? = null` and `shuffleOnStart: Boolean = false` (verified verbatim) - the step 06.3 prompt is correct as written.
- **`LauncherResourceMode` lives inside `LauncherCellCommand.kt`**, not its own file - do not create a duplicate declaration.
- **No flavor guard needed:** `SUPPORT_STREAMS`/`SUPPORT_AUDIO`/`SUPPORT_VIDEO` are all true in `standard` + `noLegal`, the only two flavors that mount `launcherEnabled`. A `CapabilityAvailability.isStreamsAvailable()` check here would be structurally dead code (Rule 21).
- **Icons:** `ic_schedule` (already the datetime glyph in `OsShortcutCatalog`) for clock; `ic_cast` (already the streams glyph in `InternalRouteCatalog`) for streams; `ic_view_grid` for folder-preview. **Playlist has no honest existing glyph** - `ic_music_note` reads as "one track", not "a list", so author one in step 06.6.

### Gadget data-source rule (owner decision 2026-07-17, ADR-10) - binds steps 06.3 and 06.5

`MediaFilesCacheManager` is process-memory only and `MediaResource.rememberFileList` defaults to **`false`** (`Models.kt:220`), so a cache-only read (the `RandomPhotoFrameWidgetRefresher` pattern) shows "Unavailable" on the home screen after **every reboot**. A live scan on every Home press breaks the surface's own invariant ("Home is pressed constantly, so this screen stays cheap"). The rule:

- **Local resource** -> lazy live load via `GetResourcesUseCase.getById` + `GetMediaFilesUseCase`, on IO, started when the gadget view is attached AND started, cancelled on detach/stop.
- **Network / cloud resource** -> `MediaFilesCacheManager.getCachedList(id) ?: cachedFileListRepository.getCachedFiles(id)` only. **Never** scan from Home.
- **Adding a gadget enables `rememberFileList` on its resource** (Phase 07's add flow, and Phase 08's seeding) so the persisted cache survives a reboot for network resources too. Phase 07 owns that write - Phase 06 only reads.
- Resource id no longer resolves (`getById` returns null, no exception) -> `R.string.launcher_home_cell_unavailable`. Cache cold on a network resource -> the same string; do NOT invent a second "open the app once" message in this phase.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/LauncherGadget.kt` | New (contract) | ≤ 60 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/LauncherGadgetRegistry.kt` | New | ≤ 80 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/ClockGadget.kt` | New | ≤ 90 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/PlaylistGadget.kt` | New | ≤ 160 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/StreamsGadget.kt` | New | ≤ 140 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/FolderPreviewGadget.kt` | New | ≤ 160 |
| `app_v2/src/launcherEnabled/res/layout/gadget_launcher_clock.xml` + `gadget_launcher_list.xml` + `gadget_launcher_preview.xml` | New | ≤ 60 each |
| `app_v2/src/main/res/drawable/ic_playlist.xml` | New (no honest existing glyph) | ≤ 15 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt` | Modified (bind registry to the Phase 05B binder) | +20 |

---

## Steps

### Step 06.1 - Gadget contract + registry + target codec

**Files:** `ui/launcher/gadget/LauncherGadget.kt`, `LauncherGadgetRegistry.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Contract:
> ```kotlin
> interface LauncherGadget {
>     val key: String
>     val defaultSpanW: Int
>     val defaultSpanH: Int
>     @get:StringRes val labelRes: Int
>     @get:DrawableRes val iconRes: Int
>     /** True when the gadget needs a resource picked at add time (param below). */
>     val requiresResourceParam: Boolean
>     fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View
> }
> interface LauncherGadgetHost {
>     val lifecycleOwner: LifecycleOwner
>     suspend fun execute(command: LauncherCellCommand): Boolean
> }
> ```
> `LauncherGadgetRegistry` (`@Singleton`, constructor-injects the four gadget classes from steps 06.2-06.5): `all(): List<LauncherGadget>`, `byKey(key): LauncherGadget?`, plus the GADGET-cell target codec: `encodeTarget(key: String, param: String?): String` = `"<key>"` or `"<key>:<param>"`, `decodeTarget(raw: String): Pair<String, String?>?` (split on first `:`; tolerant null). Keys: `clock`, `playlist`, `streams`, `folder_preview` as companion consts.

**Verification:**

- `Grep` - `interface LauncherGadget` and `class LauncherGadgetRegistry` match once each; `KEY_CLOCK`-style consts present.

**Status:** `[x]` done

---

### Step 06.2 - ClockGadget (2×1)

**Files:** `ui/launcher/gadget/ClockGadget.kt`, `res/layout/gadget_launcher_clock.xml`
**Depends on:** Step 06.1

**Prompt for developer:**

> Large time text + date line. **Use `TextClock`, not an `ACTION_TIME_TICK` receiver** - it registers its own time/timezone receivers on attach, drops them on detach, and honours the 12/24h system setting, so the gadget needs no lifecycle wiring and no receiver to keep symmetric. This is the same correction the Phase 05 sweep already applied to the taskbar tray (see PHASE_05 Implementation Log); do not re-litigate it. Set `android:format12Hour`/`format24Hour` for the time line and a second `TextClock` (or a plain date `TextView` refreshed on attach) for the date. Weather intentionally absent - S0426 will extend this gadget (strategic §6.7); leave no weather placeholder UI. Tap → open the system clock app when resolvable: build `Intent(AlarmClock.ACTION_SHOW_ALARMS)` and guard with `context.packageManager.resolveActivityCompat(intent, 0) != null` (`util/PackageManagerCompat.kt` - Rule 21 bans the raw-int overload; the same guard idiom is used by `OsShortcutCatalog.isResolvable` and `LauncherRoleManager.resolves`). Not resolvable → no-op, no toast.

**Verification:**

- `Grep` - `class ClockGadget` matches once; `TextClock` present; `ACTION_TIME_TICK` returns ZERO hits in the file (a receiver here would be the rejected design).
- `Grep` - `resolveActivityCompat` present; raw `resolveActivity(` returns zero hits.

**Status:** `[x]` done

---

### Step 06.3 - PlaylistGadget (2×2, resource param)

**Files:** `ui/launcher/gadget/PlaylistGadget.kt`, `res/layout/gadget_launcher_list.xml`
**Depends on:** Step 06.1

**Prompt for developer:**

> `requiresResourceParam = true`; param = audio resource id (picked at add time, Phase 07). Header row: resource name + play-all and shuffle icon-buttons → `host.execute(Resource(id, PLAY))` / same with shuffle (extend `ExecuteLauncherCommandUseCase`? NO - keep the codec frozen; shuffle-all = `Feature` route is out of scope; the shuffle button simply launches `PlayerActivity.createPanelIntent(context, id, shuffleOnStart = true)` via a small dedicated intent call inside the gadget - document this exception in KDoc). Body: up to 10 track titles loaded per the **data-source rule above** (local -> live scan, network -> cache only); `MediaFile.title` is often null, so display `title ?: name` - do not show a blank row. Tap a track → `PlayerActivity.createPanelIntent(context, resourceId, initialFilePath = track.path)` (both params verified to exist). Loading is lazy: query only when the view is attached AND started; cancel on detach/stop (strategic §3.2 performance; and after Phase 05B there is no `onViewRecycled` - the view owns its own teardown). Missing/removed resource, or a cold cache on a network resource → single line `R.string.launcher_home_cell_unavailable` (string exists from Phase 04, all three locales).

**Verification:**

- `Grep` - `class PlaylistGadget` matches once; `shuffleOnStart` and `initialFilePath` present.

**Status:** `[x]` done

---

### Step 06.4 - StreamsGadget (2×2)

**Files:** `ui/launcher/gadget/StreamsGadget.kt` (layout reuses `gadget_launcher_list.xml`)
**Depends on:** Step 06.1

**Prompt for developer:**

> No param. Observe `StreamSourceRepository.observeSources()` and `.take(10)` - the DAO already orders `pinned DESC, sortIndex ASC, addedAt DESC`, so do NOT re-sort. Row = favicon + `title`. Favicon via `FaviconAtlasStore.coords()[source.url]` → `FaviconAtlasSlicer.tileFor(index)`, copying the rebind-safe guard from `StreamPanelChannelAdapter.kt:100-118`; absent favicon → `ic_cast`. (Noted deviation: the shipped Streams screen falls back to a country-flag glyph, not `ic_cast`. A flag needs `country`, which is nullable, and a 32px flag in a gadget row reads as noise - `ic_cast` is the honest "a channel" mark here. Record this in the Implementation Log.) Tap → `host.execute(Stream(source.id))` - `ExecuteLauncherCommandUseCase.launchStream` already resolves id → url → `StreamsActivity.createPlayIntent`, so do not build the intent here. Collect with `collectOnLifecycle`; empty catalog → row `R.string.launcher_gadget_streams_empty` which deep-links to the streams screen (`Feature(InternalRouteCatalog.KEY_STREAMS)` - `KEY_STREAMS` verified to exist).

**Verification:**

- `Grep` - `class StreamsGadget` matches once; `Stream(` and `KEY_STREAMS` present.

**Status:** `[x]` done

---

### Step 06.5 - FolderPreviewGadget (2×2, resource param)

**Files:** `ui/launcher/gadget/FolderPreviewGadget.kt`, `res/layout/gadget_launcher_preview.xml`
**Depends on:** Step 06.1

**Prompt for developer:**

> `requiresResourceParam = true`; param = resource id. 2×2 thumbnail mini-grid of the newest 4 files loaded per the **data-source rule above**; "newest" = `sortedByDescending { it.createdDate }` filtered to `!isDirectory` (NOT `lastModified` - it is `0L` for network scanners). Thumbnails via Glide with `override(cellPx, cellPx)` at display size and `Glide.with(view).clear(target)` on detach (audit protocol Glide ownership). **Glide model trap:** a raw `Glide.load(file.path)` only works for LOCAL files - network/cloud `MediaFile`s need the typed models `NetworkFileData` / `CloudThumbnailData` that `ui/browse/AdapterThumbnailLoader.kt` builds (only those types are registered in `di/GlideAppModule.kt`, not `String`). For iteration-1 keep it simple and honest: load local paths and `contentUri`, and for a network/cloud file show the extension-icon fallback rather than reimplementing `AdapterThumbnailLoader`'s branching - document that in KDoc. Header: resource name; tap header → `host.execute(Resource(id, BROWSE))`; tap a thumbnail → `host.execute(Resource(id, SLIDESHOW))` (opens the show at the folder - per-file positioning is not iteration-1). Lazy load on attach+start, clear on detach/stop.

**Verification:**

- `Grep` - `class FolderPreviewGadget` matches once; `override(` and `.clear(` present.

**Status:** `[x]` done

---

### Step 06.6 - Adapter integration + strings + build

**Files:** `ui/launcher/LauncherHomeActivity.kt`, `res/drawable/ic_playlist.xml`, trilingual strings via tool
**Depends on:** Steps 06.1-06.5

**Prompt for developer:**

> In the activity, set `LauncherCellViewBinder.gadgetBinder = { cellUi, container -> ... }` (the hook survives Phase 05B unchanged; the class is the binder, NOT `LauncherCellAdapter`, which Phase 05B deletes): decode `LauncherGadgetRegistry.decodeTarget(cellUi.cell.target)`, `byKey`, `createView(container, host, param)`. **Unknown key → broken-cell rendering, which you must build here:** the shipped `visual == null → bindUnavailable()` path cannot serve it (`LauncherCellUi.visual` is null for EVERY gadget by contract, resolvable or not). Reuse only its constants - `R.string.launcher_home_cell_unavailable`, `R.drawable.ic_launcher_mode`, `UNAVAILABLE_ALPHA`. Host impl delegates `execute` to the ViewModel. Do NOT set `layoutParams.height` on the gadget view - `LauncherDesktopLayout` already measured the container `EXACTLY`.
>
> Author `ic_playlist.xml` (`?attr/colorControlNormal` tint, matching the existing icon set): no honest glyph exists - `ic_music_note` means "one track", not "a list".
>
> Add strings via `set-android-string.ps1 -Action add`, **values single-quoted** (Rule 7; a double-quoted value is how the Phase 05 P0 crash-loop got in): `launcher_gadget_clock`, `launcher_gadget_playlist`, `launcher_gadget_streams`, `launcher_gadget_folder_preview` (picker labels), `launcher_gadget_streams_empty`. Run `check_strings_localized.ps1 -KeyPrefix "launcher_gadget_"` → exit 0; COMMUNICATION_POLICY §6 checklist PASS. Then `.\a.ps1 d`, install, verify a manually-seeded gadget cell renders and ticks (record `expected | actual`).

**Verification:**

- `Grep` - `gadgetBinder` assignment present in `LauncherHomeActivity.kt`; `LauncherCellAdapter` returns zero hits.
- `Grep` - `layoutParams.height` returns zero hits across `ui/launcher/gadget/` (the superseded workaround must not reappear).
- `Grep` - no gadget string value contains a backtick: `Select-String -Pattern '%[0-9]`\$'` over `values*/strings.xml` → 0 hits.
- `check_strings_localized.ps1 -KeyPrefix "launcher_gadget_"` → exit 0.
- `.\a.ps1 d` → BUILD SUCCESSFUL.

**Status:** `[x]` done

---

## Implementation Log (2026-07-17)

`.\a.ps1 fc` and `.\a.ps1 fkn` passed **first try**, including `<merge>` + ViewBinding (no precedent in this repo) and the four-gadget Hilt graph. Strings 7/7 EN/RU/UK, backtick check 0 hits, post-change PASS on every gate.

Deviations from the step prompts, each deliberate:

- **`LauncherGadgetHost` is `fun run(command)`, NOT `suspend fun execute(command): Boolean` + `lifecycleOwner`.** Two reasons, both structural. (1) The desktop, both taskbar strips and the Start menu already share ONE launch guard and one "cannot open" message in `LauncherHomeViewModel.run`; handing gadgets a Boolean invites a fifth failure path around the guard - the exact duplication Phase 05 collapsed. (2) `lifecycleOwner` is a trap here: a gadget view is destroyed and rebuilt on every desktop rebind while the host's lifecycle outlives it, so `host.lifecycleOwner.collectOnLifecycle(..)` leaks one collector per rebind. Verified against the real helper: `collectOnLifecycle` scopes to the LifecycleOwner, not the view.
- **Added `LauncherGadgetView` (not in the plan).** Base class running `onActive()` while attached AND STARTED via the view-tree lifecycle owner, cancelled on detach. This is what makes the phase's own Done criterion ("no gadget holds a receiver/callback/Job after detach") true by construction instead of by review, in one place rather than four. It is the direct answer to Phase 05B's handoff note: the grid is not a RecyclerView, so there is no `onViewRecycled` to hook.
- **Added `LoadLauncherGadgetFilesUseCase` (not in the plan, `src/main`).** ADR-10 implemented ONCE. Both resource gadgets need identical local-vs-network logic, and a rule implemented twice is a rule that drifts. It uses the shipped `ResourceType.isNetworkResource` (`SMB/SFTP/FTP/CLOUD`) as the discriminator rather than inventing one.
- **Added `LauncherGadgetRowAdapter` + `item_launcher_gadget_row.xml` (not in the plan).** Playlist and streams rows are the same shape - a leading mark plus a one-line title - so they share one adapter instead of two near-copies.
- **Shuffle uses `ic_random_nav` (the dice), not a new `ic_shuffle`.** `ic_shuffle` does not exist, but the dice IS this app's established "random" mark - the player's random-file control uses the same glyph. A shuffle-arrows icon would be a second word for one idea.
- **`ic_playlist` authored** as the plan required: `ic_music_note` means "one track", not "a list".
- **Clock is `TextClock` x2 with a platform-derived date pattern** (`DateFormat.getBestDateTimePattern(locale, "EEEdMMM")`) - hardcoding a date order reads wrong outside en-US.
- **`FolderPreviewGadget` decodes only device-local sources.** Glide is registered for the typed `NetworkFileData`/`CloudThumbnailData` models `AdapterThumbnailLoader` builds, never for a bare path string, so an SMB/cloud file would silently fail to load. Such a file shows the generic mark rather than a blank tile; reimplementing that branching is not iteration-1.
- **Playlist shuffle and per-track start build their intent directly** (documented exception, as the plan allowed): the codec is frozen at what a persisted cell can hold, and neither "shuffle" nor "start at this file" is a thing a cell stores.

**Note for Phase 07:** `LauncherGadget.defaultSpanW`/`defaultSpanH` are what the picker should seed a new gadget cell with - clock is 2x1, the other three are 2x2. `requiresResourceParam` is true for playlist and folder-preview; those two are also the ones whose add-flow must set `rememberFileList` (ADR-10).

---

## Phase Audit (2026-07-17)

Four dimension auditors (lifecycle/leaks, data/threading, UI rules, contract compliance), findings then verified against the code by hand. **9 defects, no P0/P1.** Every step predicate re-ran clean - no false tick. Two findings were live user-visible bugs that no gate could have caught.

Fixed:

- **P2 - the `ic_cast` fallback was invisible.** `ic_cast.xml` fills `@android:color/white` and carries no vector tint; the gadget row set none; the card is `?attr/colorSurface` = white. **A white glyph on a white card.** Every other `ic_cast` consumer in the app tints it. Caught only by reading the drawable - the build is green and the device criterion is DEFERRED. My first fix was worse than the bug: a blanket `app:tint` on the row's ImageView would have repainted every favicon **bitmap** a flat colour and flattened `ic_music_note`'s gold gradient into a silhouette. Tint is now a per-row property (`LauncherGadgetRow.tintIcon`), set true only for the `ic_cast` fallback, and cleared explicitly on recycle.
- **P2 - the desktop was rebuilt 2-3x on every single Home entry.** `cells` and `densityFactor` are both StateFlows, both replay on ON_START, and both landed in `bind()`; rotation added a third pass. Every gadget view was destroyed and rebuilt microseconds after creation, cancelling its just-started work. Fixed with a guard in `LauncherCellViewBinder.bind`: the tree is a pure function of `(cells, columns)`, so an unchanged pair is skipped. This also fixes the next finding's frequency - views now survive a Home visit, so their caches do too.
- **P2 - the favicon atlas was re-decoded per Home entry.** The auditor measured the shipped artifact: `favicon-atlas.png` is 512x3296 RGBA = **6.44 MB decoded**, to serve ten 32px tiles (~40 KB). `FaviconAtlasSlicer` caches the decoded atlas in an instance field, and I had put the slicer in the view - the one thing the binder destroys on every rebind. Hoisted to `StreamsGadget` (constructed once; the registry is `@Singleton`), matching how `StreamsActivity` and `MainStreamsPanelManager` scope theirs to their host. Checked before hoisting: `MainStreamsPanelManager` never calls `invalidate()` either, so host-scoped-without-invalidation is the house pattern, not a corner I cut.
- **P2 - "Home stays cheap" was false, and my own KDoc said the opposite.** I claimed `GetMediaFilesUseCase` "consults its own caches first". It does not: its skip-scan gate requires `resource.rememberFileList`, which defaults to `false`. So a local-resource gadget triggered a **full physical recursive rescan on every return to Home**, contending for scan permits with user-facing Browse scans. (`maxFiles = limit` was dead too - it is read only on the chunked SMB path.) Cache-first is now this class's own job: memory -> persisted -> scan, and only a LOCAL resource may reach the scan.
- **P2 - "newest 4" was a lie above 1000 files.** `GetMediaFilesUseCase` **skips sorting entirely** past `LARGE_FOLDER_THRESHOLD = 1000` and returns the scan order, so `take(4)` on a 5000-photo folder gave 4 arbitrary photos that never changed as new ones arrived - on exactly the libraries where a preview matters most. Ordering is now done in `LoadLauncherGadgetFilesUseCase` itself, which also fixes the cached-list path (a cache carries whatever order it was stored with).
- **P3 - `runCatching` reported leaving Home as a scan failure.** It catches `Throwable`, so `CancellationException` was logged as "local scan failed". Now rethrown, matching the S0742 convention `GetMediaFilesUseCase` already sets.
- **P3 - `.first()` instead of the contracted `collectOnLifecycle`,** undocumented. Inside `LauncherGadgetView.onActive` a plain `collect` is already view-scoped and STARTED-scoped, so the snapshot bought nothing and lost live updates. Now collects.
- **P3 - `UNAVAILABLE_ALPHA` was two independent `0.45f` literals** while a comment claimed they were one value. The duplication was forced (the binder's companion was private); the companion is now public and the gadget path references it.
- **P3 - the `ic_cast`-vs-country-flag deviation was never recorded** although step 06.4 explicitly ordered it. Recorded now: the shipped Streams screen falls back to a country flag (`StreamSourceAdapter.showCountryFlagFallback`), which needs `country` (nullable) and reads as noise at 18dp in a gadget row; `ic_cast` is the honest "a channel" mark here.

The UI dimension landed last and found the worst one, by reading RecyclerView's source and Material's bytecode rather than reasoning:

- **P1 - the folder preview rendered as two tall columns, not a 2x2 grid.** `GridLayoutManager` measures a child on the SCROLL axis against the whole viewport, not against its row: `hSpec = getChildMeasureSpec(mOrientationHelper.getTotalSpace(), getHeightMode(), ..., lp.height, true)`. My thumb's `layout_height="match_parent"` therefore became `EXACTLY(entire grid height)` - each tile full-height, files 3 and 4 below the fold. The gadget's headline ("2x2 mini-grid of the newest 4") was broken from the first line. Fixed by deleting the RecyclerView entirely: the count is fixed at four, so recycling bought nothing while the LayoutManager actively fought the layout. Four declared ImageViews in weighted rows say exactly what is meant. `ThumbAdapter` (72 lines) and `item_launcher_gadget_thumb.xml` went with it - which also puts `FolderPreviewGadget.kt` back inside its line budget.
- **P2 - play/random had no press feedback.** I set `focus_button_background` as `android:background`; its own KDoc forbids exactly that ("Default state is transparent so the host button's own background / ripple keeps rendering"). The XML attribute beats `Widget.Material.ImageButton`'s style, so the ripple was replaced by a selector with no `state_pressed` - a dead-looking button during the pause before the player opens. Every shipped consumer uses this drawable as `android:foreground`; only the new launcher files used it as a background. Fixed everywhere in this phase.
- **P2 - the clock clipped at density factor 1.5.** A 32sp time + 13sp date needs ~55dp; a 5-column 360dp phone gives the cell ~54.4dp of content, and `gravity=center` pushes the overflow off BOTH ends. Now autosized 14..32sp via the platform attr (`TextClock` is not auto-inflated to an AppCompat view, and this source set is minSdk 26, so the native attr is always available).
- **P3 - dead attribute:** `cellModeBadge` carried a focus background but is not focusable, clickable or hoverable, so the selector could never leave its transparent default (Rule 20).
- **P3 - unknown-gadget cell was a MaterialCardView inside a MaterialCardView** (two concentric outlines, doubled insets) and had zero focusable children, making it unreachable by D-pad - which would leave a TV user unable to select a broken gadget to delete it in Phase 07. Now a plain focusable TextView.
- **P3 - thumbnail labels lied:** each announced its own filename while every tile opens the folder show from the start. They now describe the action.
- **P3 - two KDocs claimed the lists fit "without becoming a scroll-only surface".** They do not: ~6 of 10 rows at default density, 3 at factor 1.5. Rule 8 makes comments requirements, so the comments were corrected rather than the constants.

Parked, not fixed - **`S1081`** (out of scope per CLAUDE.md 3.1, deduped first): `android:foreground` on a `MaterialCardView` is silently overwritten by the library in its constructor, so the 2dp focus ring **never draws** on any card - the shipped `item_app_launch_panel_tile.xml` has the same line. Not a Phase 06 regression, and it needs its own research (Material-native `strokeColor` with `state_focused` vs. a wrapper vs. a subclass) plus a real D-pad device to judge.

Accepted knowingly:

- **The header's play/random buttons are 40dp, below the 48dp touch-target minimum.** The gadget's header is one grid cell tall; a 48dp control would leave the resource name ~45dp at density factor 1.5. 40dp with an 8dp inset is the largest that keeps the header readable. Recorded in the dimen's own comment so nobody "fixes" it blind.

Refutations worth recording:

- **`HTTP_STREAM`/`RTSP_STREAM` resources take the LOCAL branch** (`isNetworkResource` deliberately excludes them - it answers "carries SMB-style credentials", not "touches the network"), so I expected a network scan from Home. Refuted on consequence: `MediaScannerFactory` throws `IllegalArgumentException("Internet streams are not scannable")` at collection, which the `runCatching` absorbs into `Unavailable`. No socket opens. Worth knowing the protection is **incidental**, not designed.
- **`first()` cannot hang or return a partial list** - the flow emits unconditionally on both paths, and the progressive early-emit needs flags this call never passes.
- **`findViewTreeLifecycleOwner() ?: return` is dead code, not a silent-failure path** - `ComponentActivity.setContentView` sets the owner on the decor view, and `onAttachedToWindow` runs after the child is linked into that chain.
- **`ThumbAdapter.boundViews` is not a leak** - it holds itemViews of the gadget's own RecyclerView, so the set, adapter and views form one garbage island reachable only from the gadget view.

Known and accepted for iteration-1:

- **Line budgets in `Files Touched` above are stale** - `LauncherGadget.kt` is 101 (budget 60) because `LauncherGadgetView` landed there, and `FolderPreviewGadget.kt` is 173 (budget 160) because `ThumbAdapter` did. Both classes are recorded above; the budgets were written before those decisions existed. Rule 2's real ceiling (1500) is far away.
- **`Files Touched` is not the phase's inventory any more** - it omits `LoadLauncherGadgetFilesUseCase.kt`, `LauncherGadgetRowAdapter.kt`, `item_launcher_gadget_row.xml`, `item_launcher_gadget_thumb.xml` and the `launcher_gadget_thumb_size` dimen. All are described in the Implementation Log; read it, not the table.
- **`PlaylistGadget` type-filters nothing.** Pointed at a default-profile resource (`supportedMediaTypes` defaults to IMAGE+VIDEO) it would list photos as tracks under a music-note icon. Cannot fire today - nothing writes a GADGET cell until Phase 07 - and **Phase 07's picker owns the constraint**: a playlist gadget must only offer audio-capable resources.

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] No gadget holds a receiver/callback/Job after detach. Enforced structurally, not per-file: every gadget view extends `LauncherGadgetView`, whose `onActive()` runs only while attached AND STARTED and is cancelled in `onDetachedFromWindow`. `FolderPreviewGadget` additionally clears its Glide targets there. The clock owns nothing to cancel (`TextClock` self-manages). expected: 0 unmanaged subscriptions | actual: 0.
- [x] `Grep` - `layoutParams.height` under `ui/launcher/gadget/`: the superseded D4 workaround did not creep back. expected: 0 code hits | actual: 0 code hits, 1 doc hit (the KDoc line in `LauncherGadget.kt` that forbids it). A future re-run should expect the same 1 - do not "fix" it to zero by deleting the prohibition.
- [x] `Grep` - no backtick in any authored format string (`'%[0-9]`\$'`). expected: 0 | actual: 0.
- [ ] **DEFERRED-DEVICE** - all four gadgets render inside cells and respond to tap; the clock ticks; a 2×2 gadget occupies two rows. No seeding path exists until Phase 08 and no device is attached; covered by the Phase 10 `BlockNeedUserTest` pass. **Do not tick this on a build alone** - that is exactly how the Phase 05 P0 shipped.
- [x] Dev log + `catalog_sync.ps1`; CODE.LOCK released.

---

## Handoff Notes to Next Phase

- Phase 07's gadget picker consumes `LauncherGadgetRegistry.all()` (label/icon/`requiresResourceParam`).
- Phase 08's starter sets reference gadget keys via `LauncherGadgetRegistry.encodeTarget`.
- **Phase 07 owns the `rememberFileList` write (ADR-10):** adding a `PlaylistGadget`/`FolderPreviewGadget` for a resource must enable `rememberFileList` on it, or a network resource's gadget stays blank after a reboot. Phase 06 only reads.

---

## Rollback Plan

Revert phase commit(s) - gadget cells degrade to broken-cell rendering, desktop remains functional.
