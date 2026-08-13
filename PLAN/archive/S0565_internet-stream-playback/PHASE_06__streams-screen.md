# Phase 06 - Streams Screen

**Strategic spec:** [`../S0565_internet-stream-playback.md`](../S0565_internet-stream-playback.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04, Phase 05
**Blocks:** Phase 07
**Steps done:** 6 / 6
**Started:** 2026-06-21
**Completed:** 2026-06-21

> **Impl notes (2026-06-21, `/spec-all`).** Additions beyond the listed Files Touched, all additive:
> `res/menu/menu_streams.xml` (toolbar add/import/refresh), `res/drawable/ic_pin.xml`,
> `ic_import.xml`, `ic_stop.xml` (missing icons), extra strings `streams_title_hint`, `streams_stop`,
> `streams_import_done`. Video/RTSP fullscreen launch reuses the existing player via a stream
> short-circuit added to `PlayerMediaFilesLoader.loadMediaFiles()` (one-item synthetic list for a
> stream URL, resource id `-100L`) - the player was resource/file-centric with no arbitrary-URL entry,
> so this mirrors the existing staged-note short-circuit. ViewModel exposes localized failures via a
> `StreamsEvent` channel; the Activity holds no playback logic.

---

## Objective

Build the "Трансляции" list screen: a `StreamsActivity` + `StreamsViewModel` + adapter, an add-source / import dialog, inline audio via a sticky bottom mini-control (list stays visible), and fullscreen video launch with Back returning to the list. Portrait + landscape layouts. Trilingual strings.

---

## Prerequisites

- [ ] Phase 04 ✅ Done (`playHttpStreamVideo` + dispatch live; `AudioServiceController` http path available).
- [ ] Phase 05 ✅ Done (use cases injectable).
- [ ] `/ui-clarify` not required: inline-audio UX resolved to a sticky bottom mini-control reusing the existing audio surface (research §6 item 6); fullscreen return = system Back with list position restored (research §6 item 7). Exact pixel layout is this phase's own call within those decisions.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt` | New | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsViewModel.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamSourceAdapter.kt` | New | ≤ 160 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamInlineAudioManager.kt` | New | ≤ 180 |
| `app_v2/src/main/res/layout/activity_streams.xml` | New | ≤ 200 |
| `app_v2/src/main/res/layout-land/activity_streams.xml` | New | ≤ 200 |
| `app_v2/src/main/res/layout/item_stream_source.xml` | New | ≤ 120 |
| `app_v2/src/main/res/layout/dialog_add_stream.xml` | New | ≤ 120 |
| `app_v2/src/main/res/values/strings.xml` (+ `values-ru` + `values-uk`) | Modified | ≤ +60 |
| `app_v2/src/main/AndroidManifest.xml` | Modified | ≤ +6 |

> **Landscape parity (MANDATORY):** `activity_streams.xml` ships with a `layout-land/` counterpart in the same step. `item_stream_source.xml` and `dialog_add_stream.xml` are orientation-agnostic single items - no `-land` variant needed (note recorded). Use `?attr/`/`@color/` only - no hardcoded hex (Rule 19). All interactive rows/buttons set `focusable`/`clickable`/`nextFocus*` for D-pad/TV (Rule 16). Keep content inside `systemBars` + `displayCutout` insets (Rule 17).

---

## Steps

### Step 06.1 - Trilingual strings for the streams surface

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add the streams strings in EN/RU/UK lockstep via `scripts/utils/set-android-string.ps1 -Action add -Key <key> -En <..> -Ru <..> -Uk <..>` (one call per key, parity-enforced). Keys: `streams_title` ("Трансляции"), `streams_add` , `streams_import` , `streams_refresh` , `streams_pin_to_top` , `streams_remove` , `streams_add_url_hint` , `streams_import_url_hint` , `streams_error_invalid_url` , `streams_error_network` , `streams_error_unsupported_in_build` (rtsp/HLS on lite/photos), `streams_now_playing` , `streams_empty` , `streams_cleartext_note` (honest note that the app accepts public `http://` sources - owner requirement, strategic §3.3). Russian uses Ё where correct. Strings must pass `docs/COMMUNICATION_POLICY.md` §2 message formula + §6 tone checklist.

**Verification:**

- `Grep` - each new key present in all three of `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "streams_"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done (compileStandardDebug + compileLiteDebug + resources PASS 2026-06-21)

---

### Step 06.2 - List item + screen layouts (portrait + landscape)

**Files:** `res/layout/item_stream_source.xml`, `res/layout/activity_streams.xml`, `res/layout-land/activity_streams.xml`, `res/layout/dialog_add_stream.xml`
**Depends on:** Step 06.1

**Prompt for developer:**

> `item_stream_source.xml`: a row with title, a kind icon (audio/video/rtsp), a pin/favorite affordance, and a now-playing indicator slot. `activity_streams.xml` (and the `layout-land` counterpart): a toolbar (title + add/import/refresh actions), a `RecyclerView`, an empty-state view (`streams_empty`), and a bottom sticky mini-control container (initially gone) for inline audio. `dialog_add_stream.xml`: a single URL `TextInputLayout` + optional title field used for both manual-add and import. Use `?attr/colorOnSurface` etc. - no hex. Set `nextFocusDown`/`nextFocusUp` on the toolbar actions and rows.

**Verification:**

- `Glob` - all four layout files exist, including `res/layout-land/activity_streams.xml`.
- `Grep` - no `="#` hardcoded hex in any of the four files.
- `Grep` - the bottom mini-control container id (e.g. `streamMiniControl`) present in both portrait and land `activity_streams.xml`.

**Status:** `[x]` done (compileStandardDebug + compileLiteDebug + resources PASS 2026-06-21)

---

### Step 06.3 - `StreamsViewModel`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsViewModel.kt`
**Depends on:** Step 06.1

**Prompt for developer:**

> `@HiltViewModel class StreamsViewModel @Inject constructor(observeStreamSources, addStreamSource, importStreamPlaylist, pinStreamSource, removeStreamSource)`. Expose a `StateFlow<StreamsUiState>` (list + loading + empty) collected from `ObserveStreamSourcesUseCase`, and `onAdd`/`onImport`/`onPin`/`onRemove` functions launching on `viewModelScope`, mapping use-case failures to localized error events (`streams_error_*`). No Android/View types in the ViewModel. No business logic in the Activity.

**Verification:**

- `Glob` - file exists.
- `Grep` - `@HiltViewModel` + `class StreamsViewModel` present.
- `Grep` - `viewModelScope` used; `GlobalScope` returns zero hits.

**Status:** `[x]` done (compileStandardDebug + compileLiteDebug + resources PASS 2026-06-21)

---

### Step 06.4 - `StreamSourceAdapter`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamSourceAdapter.kt`
**Depends on:** Step 06.2

**Prompt for developer:**

> `ListAdapter<StreamSourceEntity, VH>` with `DiffUtil` binding `item_stream_source.xml`. Row tap -> `onPlay(source)`; pin affordance -> `onPin(source)`; long-press/overflow -> `onRemove(source)`. Show the kind icon by `mediaKind`. Mark the currently-playing inline-audio row with the now-playing indicator. Use ViewBinding; verify generated binding field types match the XML view classes before calling `.bind`.

**Verification:**

- `Glob` - file exists.
- `Grep` - `ListAdapter` + `DiffUtil` present.
- `Grep` - `onPlay`, `onPin`, `onRemove` callbacks present.

**Status:** `[x]` done (compileStandardDebug + compileLiteDebug + resources PASS 2026-06-21)

---

### Step 06.5 - `StreamInlineAudioManager` (sticky mini-control, list stays visible)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamInlineAudioManager.kt`
**Depends on:** Step 06.3, Step 06.4

**Prompt for developer:**

> Helper owning the bottom mini-control. On an AUDIO source tap it starts playback through the existing `AudioServiceController.playAudioWithMetadata(uri, title, mimeType = null, streamCredentials = null) { player -> ... }` (background-capable radio path, research §2/§3.7), shows the mini-control (source title + ICY now-playing + play/stop), and keeps the list visible/interactive (research §6 item 6). Stop releases via the controller. Observe ICY now-playing to update the control text. Collect any view-bound Flow with `collectOnLifecycle`/`repeatOnLifecycle`, never a bare `lifecycleScope.launch { collect }`. All Activity-side wiring delegates here - the Activity holds no playback logic (Rule 3).

**Verification:**

- `Glob` - file exists.
- `Grep` - `playAudioWithMetadata` invoked; `class StreamInlineAudioManager` present.
- `Grep` - `repeatOnLifecycle` or `collectOnLifecycle` present; bare `lifecycleScope.launch` wrapping a `.collect` returns zero hits.

**Status:** `[x]` done (compileStandardDebug + compileLiteDebug + resources PASS 2026-06-21)

---

### Step 06.6 - `StreamsActivity` (wire-up, video fullscreen, Back return)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt`, `app_v2/src/main/AndroidManifest.xml`
**Depends on:** Step 06.5

**Prompt for developer:**

> `@AndroidEntryPoint class StreamsActivity` inflating `activity_streams.xml`, hosting the RecyclerView + `StreamSourceAdapter`, delegating to `StreamsViewModel` and `StreamInlineAudioManager`. Toolbar actions open the add/import dialog (`dialog_add_stream.xml`). On a VIDEO/RTSP source tap, launch the existing player via its intent factory (`PlayerActivity.createIntent(...)`) passing the stream URL as the path extra - the player's `playVideo()` short-circuits on `StreamUri.isStream(path)` (Phase 04) and plays it as a stream. System Back returns to this list with scroll position restored (research §6 item 7). Register `StreamsActivity` in the manifest (`exported=false`). Keep all logic in ViewModel/managers - the Activity only wires views and forwards events (Rule 3, Rule 5).

**Verification:**

- `Glob` - `StreamsActivity.kt` exists.
- `Grep` - `@AndroidEntryPoint` + `class StreamsActivity` present.
- `Grep` - `PlayerActivity.createIntent` (or the verified intent factory) invoked for video/rtsp launch.
- `Grep` - `<activity` entry for `StreamsActivity` with `android:exported="false"` in `AndroidManifest.xml`.

**Status:** `[x]` done (compileStandardDebug + compileLiteDebug + resources PASS 2026-06-21)

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] Project compiles + resources resolve for standard (`.\a.ps1 fc`) and lite (Kotlin/resource compile) - screen, adapter, ViewModel, inline-audio manager, and layouts resolve in both.
- [ ] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "streams_"` exits 0.
- [ ] `Grep` for `="#` hardcoded hex in the new layouts returns zero hits.
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.
- [ ] Dev log entry added.

---

## Handoff Notes to Next Phase

- `StreamsActivity` is the single destination all entry-points open (research §6 item 5) - Phase 07 wires Settings + main-window to it.
- Inline audio uses the background-capable service path, so it survives screen-off (strategic §3.2 background lifecycle).

---

## Rollback Plan

Revert phase commit(s). New UI module + additive strings + one manifest activity entry; no schema. Remove the activity entry and strings on revert.
