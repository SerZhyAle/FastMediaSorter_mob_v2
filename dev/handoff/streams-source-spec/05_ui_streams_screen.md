# Streams Source Spec - 05 - Streams Screen (browse, filter, sort, pin, dialogs, input)

Part of the FastMediaSorter "Трансляции" (Streams) source-documentation set. This file documents the
user-facing Streams **screen** - the browsing, filtering, sorting, pinning, and dialog behavior. Playback
routing (what happens when a channel is played) is in `06_player_routing.md`; favicon/grid-frame internals
are in `04_favicon_atlas.md`; entry points / gating are in `07_entrypoints_and_gating.md`.

This describes the Android UI as-is *(impl detail throughout, except where a behavior is a product
contract worth preserving)*. Facts cite `path:line` (root `p:\ANDROID\FastMediaSorter_mob_v2`).

---

## 1. Screen identity

- `StreamsActivity` is a **standalone Activity** (a `BaseActivity` subclass), not a fragment of
  `MainActivity`. Manifest: `exported=false`, `launchMode=singleTop`,
  `configChanges=orientation|screenSize|keyboardHidden`, parent `MainActivity`. Ships in **every** flavor
  (no flavor manifest overlay removes it; UI gating is elsewhere - see `07`).
- Because of `configChanges`, **Android never recreates it on rotation** - all portrait/landscape
  adaptation is done in code (`onConfigurationChanged`), sections 2 and 17.
- Play-intent factories (used by the home-screen shortcut and the main-window panel):
  `createPlayShortcutIntent(url)` (action `com.sza.fastmediasorter.action.PLAY_STREAM`, extra
  `EXTRA_STREAM_URL`, `NEW_TASK|CLEAR_TOP`) and `createPlayIntent(url)` (same action, no task flags, so Back
  returns to the caller). `onNewIntent` resolves the URL via `GetStreamSourceByUrlUseCase` and either plays
  it or shows "This stream is no longer in your list".

---

## 2. Screen structure

Layout `activity_streams.xml` (root `CoordinatorLayout`, `fitsSystemWindows`):
- **Toolbar** (`MaterialToolbar`, `colorPrimary`): back arrow, title "Streams", `menu_streams` (section 3),
  plus an empty `headerControlsHost` FrameLayout (landscape-only relocation target, section 17).
- **Controls bar** (`streamControls`): a search `TextInputLayout` (`etSearch`), a Filter `ImageButton`
  (`btnFilter`), a Sort `ImageButton` (`btnSort`). The **same** view group is reparented into
  `headerControlsHost` in landscape (section 17) - reparented, not duplicated.
- **List area** (`weight=1`): a `SwipeRefreshLayout` (`swipeStreams`, pull-to-refresh enabled only in GRID
  mode) wrapping the `RecyclerView` (`rvStreams`), plus the empty-state view (section 14) and the
  scroll-button FABs (section 17) stacked on top.
- **Inline-audio mini-control** (`streamMiniControl`, hidden until first play): a title + a stop button
  (section 15).
- An off-screen capture host (`streamCaptureHost`, `translationX=-10000dp`) for grid frame capture (see `04`).

### 2.1 Portrait / landscape / wide

- `layout-land/` and `layout-w600dp/` are byte-identical to each other and differ from portrait only in:
  list horizontal padding (16dp vs none), mini-control `minHeight` (48dp vs 56dp), mini-title `maxLines`
  (1 vs 2). The search/filter/sort relocation into the toolbar is done **in code**, not via a layout
  override.
- **Column counts** (one shared item layout, span varies): LIST mode uses a `GridLayoutManager` with span
  1 unless the layout is wide, then `floor(screenWidthDp / 360dp)` (min 2). GRID mode span is always
  `floor(screenWidthDp / 160dp)` (min 2), orientation-independent.

---

## 3. Toolbar menu (`menu_streams.xml`)

Four always-visible icons (no overflow), re-tinted to `colorOnPrimary` in code (because `android:iconTint`
needs API 26 and legacy is minSdk 23):

| id | title | action |
|---|---|---|
| `action_stream_add` | "Add stream" | add-stream dialog |
| `action_stream_import` | "Import list" | import chooser (catalog / from URL) |
| `action_stream_display_toggle` | "Grid view" / "List view" (shows the target mode) | toggle list/grid |
| `action_stream_refresh` | "Refresh" | reachability sweep of on-screen rows (**not** a catalog download) |

Catalog refresh (download) lives under **Import list -> Update FastMediaSorter catalog** (section 12) or the
automatic refresh policy (section 7.1), never under the Refresh icon.

---

## 4. ViewModel state (`StreamsViewModel`)

`@HiltViewModel`, exposes a single `state: StateFlow<StreamsUiState>` built by combining the unfiltered
`observeStreamSources()` list with an internal `_filter` MutableStateFlow.

- `StreamsUiState`: `sources` (filtered), `filter`, `facets`, `isLoading` (starts true), `isImporting`
  (true only during catalog import), `displayMode` (`LIST`/`GRID`, starts LIST). `isEmpty = !isLoading &&
  sources.isEmpty()`.
- `StreamsFacets`: sorted distinct non-blank `categories`, `topics`, `languages`, `countries` computed from
  the unfiltered list. `languages` are **tokenized** (each row's comma-separated `language` cell is split);
  `countries` are not split (one ISO code per row).
- `StreamsFilter`: `query`, `category?`, `topic?` (no UI - covered by the query box), `language?`,
  `country?`, `mediaKind` (`ALL`/`AUDIO`/`VIDEO`), `pinnedOnly`, `sort` (`NAME` default).
- One-shot `events: Flow<StreamsEvent>`: `Message`, `ImportFinished`, `CatalogUpdated`, `PlayRequested`,
  `RestoreScroll`, `SuggestCatalogRefresh`.

---

## 5. Filtering (S0580 / S0761 / S0696)

Pure logic in `StreamsViewModel.applyFilter` (unit-tested). After the free-text query, all facets are
**ANDed**: `queryHit && categoryHit && languageHit && countryHit && mediaHit && topicHit && pinnedHit`.

- **Query** (`etSearch`, live via `doAfterTextChanged`, no button): case-insensitive substring against
  `title` OR `topic` OR `language`. Empty passes.
- **Category**: exact string equality; `null` = "All" = facet disabled.
- **Language**: the row's tokenized languages `.any { equals(filter.language, ignoreCase) }`. A row with a
  **null** language is excluded when a specific language is selected (only appears under "All").
- **Country**: exact ISO-code equality (not tokenized).
- **Media kind** (`ALL`/`AUDIO`/`VIDEO`): `AUDIO` -> `mediaKind == "AUDIO"`; `VIDEO` -> `mediaKind == "VIDEO"
  || "RTSP"` (**RTSP is folded into Video**). A 3-button toggle group (always exactly one selected) at the
  top of the filter dialog.
- **Topic**: in the data class and AND chain but has no UI control (the query box covers it) - always null
  in practice.
- **Pinned-only** (S0696): a checkbox; `!pinnedOnly || source.pinned`.
- **"All disables a facet"**: every default (null/ALL/false) is the pass-everything branch, so there is no
  separate match-mode toggle.
- **Active-filter marker**: the Filter icon swaps `ic_tune` <-> `ic_tune_active` (a shape - a dot overlay,
  not just a tint) and flips its `contentDescription` whenever any facet is non-default.

### 5.1 Facet option mappers
- **Language** (`StreamLanguageOptionMapper`): id = lowercase name (matches the filter value), label
  title-cased; a flag glyph if the name is in the translator catalog, else plain text; `english`, `russian`,
  `ukrainian` pinned to the top in that order.
- **Country** (`StreamCountryOptionMapper`): id = uppercased ISO code; `RU`/`BY` use a custom bitmap flag,
  others a Unicode flag emoji prefixed to the code, unmapped codes render bare.

### 5.2 Filter dialog + searchable picker
- Filter dialog (`StreamsFilterDialogManager`, layout `dialog_streams_filter.xml`): media-kind toggle, then
  Category/Language (50/50 row), then Country (full width), then the Pinned-only checkbox. Each facet row
  opens a **searchable picker** (`SearchableOptionPickerDialog`); the pick is applied **live** on every
  change. Buttons: neutral "Clear filters" (resets all facets in place), positive OK. No Cancel - changes
  are already applied, so Escape/Back just close.
- Searchable picker (shared component, S0580/S0947): a leading "All" reset row (reported as a null pick); a
  search field shown only when the list overflows; case-insensitive substring match on the label; row tap
  (or D-pad center/Enter/Space) both picks and dismisses. Escape dismisses.

---

## 6. Sort (S0659)

5 modes (`SortMode { NAME, TOPIC, LANGUAGE, COUNTRY, RECENT }`, default `NAME`; a separate UI-agnostic
`StreamDefaultSort` mirror is bridged by an explicit `when`). Comparators apply **only to the non-pinned
remainder** (pinned rows keep their manual order, section 8):
- `NAME` - case-insensitive title; `TOPIC`/`LANGUAGE`/`COUNTRY` - case-insensitive, nulls last;
  `RECENT` - `addedAt` descending (insertion recency, not last-played).

UI: a single-choice `AlertDialog`; picking an item applies and dismisses immediately.

---

## 7. Session persistence (S0659 / S0697 / S0699)

A dedicated DataStore file `"streams_session"` (separate from `AppSettings`). Persisted: `lastSort`,
`lastMediaFilter`, `lastCategory`, `lastLanguage`, `lastCountry`, `lastPinnedOnly`, `lastCatalogRefreshAt`,
`lastDisplayMode`, `lastScrollPosition`. **The free-text search query is deliberately NOT persisted**
(S1054) - always empty on a fresh open.

- Restore once per ViewModel (guarded against clobbering a fast interaction): sort/mediaKind fall back to the
  Settings defaults; facets fall back to null/false. A restored facet that no longer exists just yields an
  empty filtered list with the filter still shown (no crash, no auto-clear).
- Scroll restore (S0699): a buffered `RestoreScroll(position)` event, applied once, clamped to range.

### 7.1 Catalog auto-refresh policy (`StreamsCatalogRefreshPolicy`, default `ON_OPEN`)

Enforced once per screen open:
- `MANUAL` - nothing automatic.
- `ON_OPEN` (default) - if `now - lastCatalogRefreshAt > 6h`, show a **dismissible** Snackbar ("Update the
  channel list?" + "Update" action); dismissing does nothing (never auto-downloads).
- `PERIODIC_WIFI` - if on WiFi and `> 24h` since last refresh, silently call catalog import. No WorkManager
  job backs this - opportunistic on-open only.

---

## 8. Pin-to-top and reorder (S0588 / S0695 / S0938)

- DB order: `ORDER BY pinned DESC, sortIndex ASC, addedAt DESC`. The ViewModel partitions into
  (pinned, unpinned) and re-sorts only the unpinned half by the chosen `SortMode` - pinned rows keep their
  manual `sortIndex` order regardless of the active sort.
- Pin-to-top: `sortIndex = MIN(sortIndex) - 1` -> a newly-pinned row is always topmost.
- **List row** has a dedicated pin button (filled/outline icon). A **grid tile** has no pin button - pin is
  a non-interactive "Pinned" badge (top-left) toggled only via the tile overflow menu or long-press.
- **Long-press** on a row/tile toggles pin (S0695 moved destructive Remove out of long-press so it's safe).
- Reorder within the pinned set (S0938, `PinnedStreamMove { UP, DOWN, TO_TOP }`): renumbers the whole pinned
  set contiguously in one transaction; "Move up/down/to top" appear in the overflow only when pinned and
  `>1` pinned, disabled (not hidden) at the edges.

---

## 9. List row content (`StreamSourceAdapter`, `item_stream_source.xml`)

Horizontal row, `minHeight 56dp`: leading 24dp favicon-or-country-flag slot, a 14dp play-status bullet, a
32dp media-kind icon, a text column (title / url / chip row / now-playing caption), a 48dp pin button, a
48dp overflow button.
- **Title** - `StreamTitleFormatter.display()` (section 9.1). **URL** - raw, always shown. **Kind icon** -
  `ic_audio` (AUDIO) / `ic_video` (VIDEO+RTSP); hosts the now-playing spin animation.
- **Chips** (up to 3, fixed order Topic/Country/Language, each hidden when blank). Country chip = "flag +
  CODE".
- **Now-playing caption** - shown only on the currently inline-playing row.

### 9.1 Title dedup (S0691, `StreamTitleFormatter`) *(pure, never mutates the stored title)*
`Name (Name)` -> `Name` (drop a duplicate trailing parenthetical, case-insensitive); a differing
parenthetical (`Euronews (FR)`) is kept. Only the trailing parenthetical is considered. Applied identically
on the list row, grid tile, and mini-control.

### 9.2 Play-status bullet (S0593) **[product contract worth preserving]**
From `StreamSourceEntity.lastPlayOutcome`. Distinct **icon shape** per state (not colour only) + a distinct
`contentDescription`:

| value | icon | colour | contentDescription |
|---|---|---|---|
| null (never tried) | hollow ring | amber `#FFF9A825` | "Not played yet" |
| "OK" | filled circle + check | green `#FF2E7D32` | "Verified online" |
| "FAIL" | filled circle + exclamation | red `#FFC62828` | "Last playback failed" |

Two write paths for `lastPlayOutcome` (the field is shared; the list reflects whatever was last written):
(1) `RecordStreamPlayOutcomeUseCase.invoke(id, ok)` writes OK/FAIL for a **real play** - on the Streams
**screen** it is wired only to the inline-audio callbacks (so an **AUDIO** row goes red on inline-audio
failure), while the **fullscreen player** (`PlayerActivity`, VIDEO/RTSP) writes OK/FAIL through its own
callbacks (so a **VIDEO/RTSP** row goes red after a failed fullscreen play). See `06` §10 for the full
outcome model. (2) `recordProbe(id, reachable)` writes OK/UNKNOWN (UNKNOWN renders as amber) from the
health probe and grid frame capture - a probe can promote to green but **never** writes red. So red is
reserved for a real failed play from either surface; a probe never regresses a row to red.

---

## 10. Grid mode (S0675, `StreamGridAdapter`, `item_stream_grid_cell.xml`)

16:9 tile: a frame image with overlays - bottom-left status dot, bottom scrim + title, top-left pin badge,
top-right overflow. Thumbnail precedence (details in `04` §9): cached live frame -> favicon tile -> blank
tile (no country-flag fallback in grid, unlike the list). Only http(s) VIDEO tiles are captureable
(`mediaKind=="VIDEO"` && http/https); AUDIO/RTSP always show the favicon. Toggling list/grid swaps the
adapter + span and starts/stops the capture engine; grid enables pull-to-refresh + a 60 s periodic
re-capture of visible tiles.

---

## 11. Overflow (three-dot) menu (S0660 / S0938 / S0783 / S1062)

Both the list overflow and the grid overflow build a `PopupMenu` in this order:

| # | item | condition |
|---|---|---|
| 1 | Pin / Unpin | **grid only** (the list uses its dedicated pin button) |
| 2-4 | Move up / down / to top | pinned and `>1` pinned; disabled at edges |
| 5 | Add/Remove favorite | only if favorites enabled (default true) |
| 6 | Add to home screen | always (S0637, section 13) |
| 7 | Edit | only `sourceOrigin == "MANUAL"` (S0660) |
| 8 | Send link | always - shares `source.url` as `text/plain` |
| 9 | Remove | always -> confirmation dialog |

Favorites-enabled and is-favorite are read lazily at menu-open time.

**Input-parity gap (parked S1111):** the list row wires mouse right-click -> overflow via
`setOnGenericMotionListener` (`StreamSourceAdapter.kt:145-154`), but `StreamGridAdapter` has no equivalent
- right-click on a grid tile does nothing. See section 18.

---

## 12. Add / Edit / Import dialogs

All `MaterialAlertDialog`, all keyboard-wired (Escape dismiss, Enter = primary).
- **Add stream** (`dialog_add_stream.xml`): URL field (hint "Stream URL (http, https, rtsp)") + optional
  Title. `AddStreamSourceUseCase` rejects a non-http/https/rtsp scheme; blank title defaults to the URL
  host; `mediaKind` classified from the URL (see `03`); row is `MANUAL`.
- **Import from URL**: same dialog, title hidden, hint "Playlist URL (.m3u)". `ImportStreamPlaylistUseCase`
  (rows `IMPORTED`, duplicates by URL ignored). See `03` §5 for the m3u parser.
- **Import list chooser**: a 2-item dialog - "Update FastMediaSorter catalog" (-> `ImportStreamCatalogUseCase`,
  see `01`) or "Import from URL".
- **Edit channel** (S0660): reuses the add layout, both fields pre-filled; only for MANUAL rows; re-classifies
  `mediaKind` from the new URL; preserves `pinned`/`sortIndex`/`sourceOrigin`/`lastPlayOutcome`.
- **Remove confirmation**: message = the channel title; positive Remove, negative Cancel.
- **Stream unavailable** (S0581): fired only by inline-audio failure. Retry / Remove (unconfirmed) / Cancel.
  The row bullet flips red before the dialog is even built.

Catalog import result toasts: "Catalog: +N new, M updated, K removed" (`CatalogUpdated`), "Catalog is empty
or unavailable" (`Empty`), network error (`Failure`). On update it reloads the favicon atlas/coords and
repaints (see `04`).

---

## 13. Home-screen shortcut (S0637 / S1067)

`onAddShortcut` resolves the channel's favicon tile, then `StreamShortcutPinManager.requestPin` builds a
`ShortcutInfoCompat` (id `stream_<id>`, label = title, icon = the favicon tile via
`IconCompat.createWithBitmap` - **not** adaptive, to avoid cropping the square favicon ~25%, falling back to
a kind vector) whose tap intent is `createPlayShortcutIntent(url)`. Refused with a toast if the launcher
doesn't support pinning.

---

## 14. Empty state (S0673)

Shown whenever `isEmpty` (also when a filter zeroes the list): an `ic_cast` icon, "No streams yet. Add a URL
or import a list.", a primary "Add stream" button, and a secondary "Import list" button - both calling the
same handlers as the toolbar icons.

---

## 15. Inline-audio mini-control (S0577 / S0690 / S0778) *(UI level; routing in 06)*

Governs only AUDIO rows (VIDEO/RTSP launch the fullscreen player, see `06`).
- Tapping an AUDIO row plays it inline; **re-tapping the already-playing row stops it** (S0690, checked
  before the network gate so stop never needs connectivity). Fully offline -> a toast, no attempt (S0711).
- Two backends by `enablePersistentAudioPlayback && BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK` (true for
  standard/noLegal/legacy/vr; false for lite/photos): a foreground-service ExoPlayer (survives leaving the
  screen) or a local in-app ExoPlayer (released on `onStop`).
- Mini-control shows the title, and " - <track>" once ICY metadata arrives; its button is always Stop
  (start only ever happens via a row tap). Only the list adapter reflects "now playing" (spin + caption);
  the grid adapter has no now-playing indicator.
- Exit-with-audio-check (S0577): leaving the screen resolves `backgroundAudioExitBehavior` (`ASK` default /
  `ALWAYS_STOP` / `ALWAYS_CONTINUE`) - only service-mode audio can continue; a 4-choice dialog can persist a
  new default. See `06` for the shared player-side behavior.

---

## 16. Health probe (S0700, `StreamHealthProbeManager`)

Triggered only by the Refresh icon. Probes only **on-screen** rows, **sequentially** (one muted surfaceless
ExoPlayer at a time, 8 s timeout, released in `finally`). In GRID mode, VIDEO tiles are covered by the
frame-capture (which itself reports reachability) and the probe covers only visible AUDIO tiles. The whole
sweep is one cancellable job, cancelled by playing a row, a **drag** scroll (not a programmatic scroll),
typing in search, or opening Filter/Sort. Result writes OK/UNKNOWN only (never red).

---

## 17. Scroll buttons + controls placement

- **Scroll buttons** (S0587): 4 FABs (scroll-to-top/page-up top, page-down/scroll-to-bottom bottom), shown
  only when the list overflows; the two groups are independent (mid-list shows all 4). Page = one screenful.
- **Controls placement** (S0940): in landscape, the `streamControls` group is reparented into the toolbar
  header (search pinned to a fixed width, box background `colorSurface`, filter/sort tint `colorOnPrimary`
  for legibility on the primary-colored bar); in portrait it sits in its original full-width slot. Reparent
  is a no-op when already in the target host. Driven from `onConfigurationChanged` (no Activity recreate).

---

## 18. TV-remote / keyboard / mouse parity (S0664, audited 2026-06-24)

- **Mouse right-click** on a **list** row opens its overflow (`setOnGenericMotionListener` on
  `BUTTON_SECONDARY`). **Gap**: the **grid** tile has no such handler - right-click does nothing (parked as
  **S1111**).
- **Dialogs**: all 7 streams dialogs are keyboard-wired via `DialogKeyboardDelegate` (Escape dismiss, Enter
  = primary).
- **D-pad focus chain**: explicit `nextFocus*` across `etSearch -> btnFilter -> btnSort -> rvStreams ->
  btnMiniPlayStop`, mirrored in both `layout/` and `layout-land/`; `rvStreams` keeps native intra-list
  traversal (D-pad center on a row plays it). `BaseActivity` provides shared keyboard/gamepad/mouse-wheel
  routing.

---

## 19. Key strings (EN source, subset)

`streams_title`="Streams", `streams_add`="Add stream", `streams_import`="Import list",
`stream_status_ok`="Verified online", `stream_status_failed`="Last playback failed",
`stream_status_unknown`="Not played yet", `streams_now_playing`="Now playing",
`streams_error_invalid_url`="Enter a valid http, https or rtsp address.",
`streams_catalog_updated`="Catalog: +%1$d new, %2$d updated, %3$d removed",
`streams_empty`="No streams yet. Add a URL or import a list.",
`streams_import_catalog`="Update FastMediaSorter catalog". (Full list in the app's `strings.xml`, key
prefix `streams_`/`stream_status_`.)

---

## 20. Ticket index for this file

S0565, S0570, S0577, S0580 (filter/picker), S0581 (unavailable dialog), S0587 (scroll buttons), S0588/S0695
(pin), S0593 (status bullet), S0637/S1067 (shortcut), S0659 (session + refresh policy), S0660 (overflow +
edit), S0664 (input parity), S0673 (empty state), S0675/S0701/S1062 (grid), S0690 (re-tap stop), S0691
(title dedup), S0692 (rotation recompute), S0696 (pinned-only), S0697 (facet session), S0699 (scroll
restore), S0700 (health probe), S0711 (offline gate), S0761 (country facet), S0778 (mini-control insets),
S0783 (favorites), S0940 (controls placement), S0947 (searchable picker), S1054 (search not persisted).
Parked during this pass: **S1111** (grid right-click parity).
