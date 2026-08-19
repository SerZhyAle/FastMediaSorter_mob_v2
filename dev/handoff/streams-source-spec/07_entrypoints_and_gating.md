# Streams Source Spec - 07 - Entry Points & Feature Gating

Part of the FastMediaSorter "Трансляции" (Streams) source-documentation set. This file documents how the
Streams feature is **exposed** (menu, main-window panel, settings, onboarding, launcher gadget, extensions
download, app-launch panel) and **gated** (flavor BuildConfig, user master toggle, device profile).

The browse screen is in `05_ui_streams_screen.md`; the player in `06`; the catalog in `01`/`03`. Facts
cite `path:line` (root `p:\ANDROID\FastMediaSorter_mob_v2`). Module `app_v2` only (no `wear` Streams).

---

## 1. The two gates

### Gate A - `SUPPORT_STREAMS` (compile-time, per flavor)

Per-flavor BuildConfig field (`app_v2/build.gradle.kts`), read through a single point
`CapabilityAvailability.isStreamsAvailable() = BuildConfig.SUPPORT_STREAMS` (`:47`) - the only place any
Streams gating reads the flag (CLAUDE.md Rule 14: no raw `BuildConfig.*` flavor guards elsewhere).

| Flavor | SUPPORT_STREAMS |
|---|---|
| standard | **true** |
| noLegal | **true** |
| legacy | **true** |
| vr | **true** |
| lite | **false** (UI hidden; the unrelated S0116 "streamingDisabled" download pipeline is unchanged) |
| photos | **false** (no entry point) |

A second derived flag gates the Extensions screen:
`isExtensionsScreenAvailable() = isOcrCompiledIn() || isTranslationAvailable() || isStreamsAvailable()`.

*(Do not confuse `SUPPORT_STREAMS` with the `streamingEnabled`/`streamingDisabled` source-set pair - the
latter gates the S0116 network-file streaming/offload subsystem, a different thing that shares the word.)*

### Gate B - `AppSettings.enableStreams` (user master toggle, S0575)

`enableStreams: Boolean = false` (default **OFF**; a device-profile preset raises it - section 11).
Persisted in a dedicated `StreamsSettingsStore` (key `enable_streams`), mirrored into `AppSettings`.

### How the gates combine (every call site)

| Entry point | Gate formula |
|---|---|
| Main-window menu item | `isStreamsAvailable() && enableStreams` |
| Main-window pinned-channel panel | `isStreamsAvailable() && enableStreams && showStreamsPanelInMainWindow` |
| Settings > Media > Streams section exists | `isStreamsAvailable()` (Gate A; its own master toggle is Gate B, inside it) |
| General settings "Show streams panel" row visibility | `isStreamsAvailable() && enableStreams` |
| Welcome Streams row | `isStreamsAvailable()` (Gate A; the row writes Gate B) |
| Extensions screen reachable | `isExtensionsScreenAvailable()` (Gate A OR'd with OCR/translation) |
| Extensions screen's Streams-catalog row | `isStreamsAvailable()` (Gate A only - the row exists even with Streams turned off) |
| **App Launch Panel "Streams" tile (S0663)** | `isStreamsAvailable()` only - **Gate B NOT checked** (section 10) |
| **Launcher gadget empty-state "Feature" tap** | same App-Launch-Panel route - Gate B NOT checked |
| Launcher-desktop `StreamsGadget` presence | a **third** gate: the `launcherEnabled` source set, not `SUPPORT_STREAMS` (section 8) |

The App Launch Panel tile and the launcher gadget's feature-route are the **only** two entry points that do
not re-check `enableStreams` (section 10).

---

## 2. Main-window menu entry (`MainStreamsMenuManager`)

A stateless 36-line manager: when enabled, adds one item ("Streams", `ic_cast`) to the main window's
"Programs & Scenarios" dropdown / programs-panel; tapping it launches `Intent(StreamsActivity)`. The caller
passes `enabled = !excludeStreams && (isStreamsAvailable() && enableStreams)`. `excludeStreams` is true
whenever the pinned-channel panel (section 3) is visible, so the same entry never appears twice. An
"Open in new window" variant exists.

---

## 3. Main-window pinned-channel panel (`MainStreamsPanelManager` + adapter + menu actions)

S0756 (panel), S0777 (inline audio), S0770/S0779 (menus), S0782 (hide vs disable), S0808 (collapse).

- **Shows**: a wide "Streams" entry button + a horizontally-scrolling strip of the user's **pinned**
  channels (from `ObservePinnedStreamSourcesUseCase`), each chip a favicon thumbnail + name truncated to 10
  chars (labels in landscape, icon-only in portrait). Favicons reuse the shared atlas (see `04`).
- **Three-way visibility**: `available` (the Gate-A/B/showPanel triple), `collapsed`
  (`streamsPanelCollapsed`, a labelled strip chip), `pinnedEmpty` (S1061, an empty-state hint "Pin streams
  to see them on this panel").
- **Inline audio from the panel** (S0777): tapping an AUDIO chip plays it inline via the shared
  `StreamInlineAudioManager` engine (a mini-control in the home window, ICY metadata, re-tap-to-stop);
  VIDEO/RTSP chips defer to `StreamsActivity.createPlayIntent(url)`. Background continuation via
  `enablePersistentAudioPlayback && ENABLE_PERSISTENT_AUDIO_PLAYBACK`.
- **Per-channel menu** (long-press): Open / Open in new window / Add-Remove favorite / Remove (unpins).
- **Entry menu** (S0779): Open / Open in new window / **Configure** (deep-links Settings > Media > Streams
  via `openStreamsSectionIntent`) / **Collapse panel** (persists `streamsPanelCollapsed`) / **Hide panel**
  (sets `showStreamsPanelInMainWindow = false`; Streams stays enabled, falls back to the menu item) /
  **Disable** (flips Gate B `enableStreams = false` - same as unchecking the Settings toggle).

The "Hide panel" vs "Disable" distinction (S0782) is important: hiding is panel-only; disabling is the full
master-toggle flip.

---

## 4. Settings - `StreamsSettingsFragment` (S0575 master toggle + S0659 defaults)

A collapsible child section of `MediaSettingsFragment` (Media tab), attached only under Gate A; deep-linkable
(`SettingsActivity.openStreamsSectionIntent`). Six rows, in order:

1. **Enable Streams** (Gate B master toggle) - `enableStreams`. Everything below is visibility-gated on it.
2. **Default order** (dropdown) - `streamsDefaultSort` (`StreamDefaultSort`, default `NAME`): By name / By
   topic / By language / By country / Recently added.
3. **Show by default** (dropdown) - `streamsDefaultMediaFilter` (`StreamMediaTypeFilter`, default `ALL`):
   All / Audio / Video.
4. **Updating the channel list** (dropdown) - `streamsCatalogRefreshPolicy` (default `ON_OPEN`): "Only when I
   ask" (MANUAL) / "Suggest when I open" (ON_OPEN) / "Automatically on Wi-Fi" (PERIODIC_WIFI).
5. **Clear play marks** (button) - confirm dialog, then `ClearStreamPlayOutcomesUseCase` nulls every row's
   OK/FAIL bullet (channels kept).
6. **Streams** (button) - opens `StreamsActivity`.

The **"Show streams panel in main window"** toggle was moved out of this fragment (S0911) to
**General > Interface** (`rowShowStreamsPanel`), visibility-gated on `isStreamsAvailable() && enableStreams`.
These 6 rows + the relocated toggle are documented in `docs/settings/settings-manifest.json` /
`SETTINGS_REFERENCE.md` (verified consistent).

---

## 5. Welcome onboarding (S0575)

`WelcomeFunctionalityController.bindStreamsRow` (row "Streams - Play internet streams and import the
built-in catalog"): checking it **commits `enableStreams = true` immediately** and then best-effort imports
the catalog. This is deliberately **not** the OCR/translation install-gated pattern - a refused or failed
import leaves the feature ON (manual sources still work) and never blocks navigation.

The onboarding import runs `ImportStreamCatalogUseCase()` under a hard **90 s UI deadline**
(`STREAMS_IMPORT_DEADLINE_MS`, S1106) so it always resolves as done/failed and never hangs (this deadline
wraps the use case's own 30 s OkHttp `callTimeout` plus the post-download parse/merge/sidecar work). Status
strings: "Downloading.." -> "Catalog downloaded" / "Catalog not downloaded, you can add streams manually".

**Enable-all** (`WelcomeEnableAllManager`): sets `enableStreams = true` unconditionally and fire-and-forgets
the import (no deadline, no UI reflection); uses the mutex-serialized settings write (S0876) because it
races the concurrent OCR/translation writers.

---

## 6. Launcher desktop gadget + picker (`launcherEnabled` only)

The launcher-mode home surface (S0404, a desktop/taskbar replacement) is a **third gate**: the `StreamsGadget`
and `LauncherStreamPickerDialogFragment` live in the `src/launcherEnabled/` source set, mounted only for
**standard** and **noLegal** (the HOME intent-filter is injected only for those two). **legacy and vr have
`SUPPORT_STREAMS = true` yet ship no launcher gadget** (they mount `launcherDisabled`); lite/photos lack it
and fail Gate A anyway.

- **`StreamsGadget`** (`key=streams`, 2x2, label "Channels", `ic_cast`): shows up to 10 channels from the
  **full** catalog (pinned-first), reusing the atlas. Empty-state tap runs the App-Launch-Panel feature
  route (opens `StreamsActivity`, **bypassing Gate B**). A populated row tap runs
  `LauncherCellCommand.Stream(id)`.
- **`LauncherStreamPickerDialogFragment`**: a static snapshot of the catalog in the shared searchable
  picker; on pick it returns the channel id, and the desktop pins a `LauncherCellCommand.Stream(streamId)`
  cell. Executing a pinned channel resolves it by id via `getById`; a since-deleted channel fails **soft**
  ("cannot open" message, no crash).

---

## 7. Extensions (Downloadable Extensions) - catalog download entry

The Extensions screen (`ExtensionSection.STREAMS`) has a `ExtensionItem.Catalog` row ("Stream sources
catalog"), shown under Gate A. Its download dispatches **directly** to `ImportStreamCatalogUseCase` (a flow
emitting `Queued` -> `Installed`/`Failed`), bypassing the generic deliverable-download machinery. Uninstall
is a no-op reset (catalog rows are re-importable; it never deletes manually-added sources).

The Extensions screen is reachable from three places (all under `isExtensionsScreenAvailable()`): the
Welcome "Elements" button, Settings > General > Downloadable Extensions, and Settings > Media > Other >
Downloadable Extensions.

**Known defect (parked S1110), partially patched, drifted again:** the row's size is a hardcoded
`STREAM_CATALOG_SIZE = 2_500_000` bytes (`DeliverableInventoryImpl.kt`) - manually bumped once to match a
2026-07-19 measurement (per its own comment), not wired to the real download size. `formatBytes()` now
shows **"2 MB"** (not the literal "0 MB" this file originally reported against an older `200_000`-byte
stand-in). The real ZIP is 7,557,268 bytes (~7.56 MB) as of 2026-08-19 - the hardcoded value is stale again
and will keep drifting every time the catalog grows, because nothing re-measures it automatically.

---

## 8. App Launch Panel route (S0663) - the Gate-B-bypassing entry

A fourth, independent Streams entry point (a user-configurable quick-launch panel reached by a left-edge
gesture). Its route (`InternalRouteCatalog.KEY_STREAMS`) opens `StreamsActivity` directly. Its availability
(`ResolvePanelRouteAvailabilityUseCase`) is `Availability(capability.isStreamsAvailable(), enabledAtRuntime =
true)` - a **hardcoded `true`**, unlike every other settings-backed route (game/favorites/voice read their
flag). So this route reduces to Gate A only; **the `enableStreams` toggle is structurally unreachable from
it**. The panel also **auto-seeds a Streams tile** on a fresh install (one of 4 default routes), so a new
Gate-A install gets a live Streams tile before the user ever visits Settings or Welcome.

*(This is a verified asymmetry - a reuse should decide deliberately whether an app-panel/gadget shortcut
respects the master toggle. It is documented here as behavior, not filed as a defect.)*

Explicitly unrelated: `ResourceLaunchWidgetProvider` is a generic home-screen AppWidget that can point at any
Resource (including a manually-added `HTTP_STREAM`/`RTSP_STREAM` resource) - part of the older Resources
model, not the Трансляции catalog.

---

## 9. Home-screen shortcut to one channel (S0637)

A standard Android `ShortcutManager` pinned shortcut (id `stream_<id>`, tap intent `ACTION_PLAY_STREAM`),
built from the Streams screen's overflow (see `05` §13). It works on every Gate-A flavor (no
`launcherEnabled` dependency), distinct from both the launcher gadget (section 6) and the app-launch panel
(section 8).

---

## 10. Per-device-profile default (`device_profile_presets.csv`)

A first-run device-profile picker seeds defaults. Of the Streams-related rows, **only `enableStreams`**
carries real per-profile values (`enableStreams`, line 151):

| Profile | enableStreams |
|---|---|
| personal_smartphone, home_tablet, tv_media_box, car_head_unit, media_player, video_player, audio_player, vr_headset | TRUE |
| photo_frame, ebook_reader | FALSE |
| Other | (empty = code default, false) |

The other five rows (`streamsDefaultSort`, `streamsDefaultMediaFilter`, `streamsCatalogRefreshPolicy`,
`showStreamsPanelInMainWindow`, `streamsPanelCollapsed`) are blank for every profile, so code defaults apply
(NAME / ALL / ON_OPEN / false / false). This preset is a **Gate-B-only** default - it cannot turn Streams on
where Gate A fails (lite/photos never show the UI regardless of profile, because every consumer re-checks
`isStreamsAvailable()`).

---

## 11. Favicon atlas sharing across surfaces (S0668)

The main-window panel, the launcher gadget, the launcher picker, the Favorites list, and the full Streams
screen each decode the **same** favicon atlas (`FaviconAtlasStore` + `FaviconAtlasSlicer`) **once per host
instance**, keyed by URL -> tile index (see `04` for internals and the per-instance invalidation gap).

---

## 12. Ticket index for this file

S0327 (device presets), S0386/S0401/S0547 (extensions framework), S0404 (launcher surface), S0565 (Gate A),
S0570 (catalog import), S0575 (Gate B master toggle + onboarding), S0577/S0777/S0778 (panel inline audio),
S0659 (settings defaults), S0663/S0912 (app-launch panel), S0668 (atlas sharing), S0756/S0770/S0779/S0780/
S0782/S0783/S0807-S0810 (panel), S0876 (serialized onboarding writes), S0911 (panel toggle relocation),
S1037/S1061/S1068 (panel layout), S1106 (onboarding deadline). Parked during this pass: **S1109**
(ARCHITECTURE.md stale streams section), **S1110** (Extensions "0 MB").
