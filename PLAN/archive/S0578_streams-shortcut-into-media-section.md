**Status:** Archived

# S0578 - Move Streams shortcut into the Media > Streams section

## 0. Origin

- Owner request: move the "Трансляции" button from Settings > Player into the collapsible "Media" > "Streams" group (portrait and landscape).
- Follow-up refinement on S0575, which introduced the Media > Streams section (master toggle) but kept the Player-tab Streams shortcut in place.

## 1. Problem

- The Streams entry-point button (`btnStreams`, opens `StreamsActivity`) lives at the top of the Playback (Player) settings tab.
- The new home for everything Streams-related is the Media > Streams collapsible section (`StreamsSettingsFragment`), which currently hosts only the Enable-Streams master toggle.
- Keeping the shortcut on the Player tab splits the Streams settings across two tabs.

## 2. Latent coupling discovered

- `fragment_settings_streams` is listed in `SettingsSearchLayoutCatalog` but missing from `SettingsSearchTabMapping`.
- The manifest exporter drops every row of a layout with no tab assignment, so `rowEnableStreams` is currently absent from `settings-manifest.json`, the settings search index, and `SETTINGS_REFERENCE*.md` (a gap left by S0575).
- Therefore a naive move would also drop `btnStreams` from the manifest/search/docs - the relocation must add the tab mapping, which simultaneously surfaces the streams toggle into the docs.

## 3. Solution

- Remove the `btnStreams` `MaterialButton` block from both `res/layout/fragment_settings_playback.xml` and `res/layout-land/fragment_settings_playback.xml`.
- Add the same Streams shortcut button into `res/layout/fragment_settings_streams.xml`, below the Enable-Streams toggle (single layout serves both orientations - no `layout-land` variant exists).
- Move the click wiring out of `PlaybackSettingsFragment` into `StreamsSettingsFragment` (open `StreamsActivity`).
- Drop the `SUPPORT_STREAMS` visibility guard - the whole section (and thus the fragment) is attached only when `isStreamsAvailable()` (== `SUPPORT_STREAMS`), so the button is inherently gated.
- Add `R.layout.fragment_settings_streams -> TabAssignment(MEDIA, "streams")` to `SettingsSearchTabMapping` (tag `media_streams` already exists for section expansion).
- Regenerate `settings-manifest.json`; add the `rowEnableStreams` annotation; re-render `SETTINGS_REFERENCE*.md`.

## 4. Behaviour

- Owner refinement (2026-06-21): the shortcut follows the Enable-Streams master toggle - `View.GONE` when the toggle is off, visible when on. Reverses the earlier "stays visible regardless" decision so the feature is absent everywhere while disabled. Also supersedes S0575 device-test point 5 for the relocated shortcut.
- Gating unchanged: hidden in flavors without `SUPPORT_STREAMS` (photos).

## 5. Files

- `app_v2/src/main/res/layout/fragment_settings_playback.xml`
- `app_v2/src/main/res/layout-land/fragment_settings_playback.xml`
- `app_v2/src/main/res/layout/fragment_settings_streams.xml`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/StreamsSettingsFragment.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchTabMapping.kt`
- `docs/settings/settings-manifest.json` (regenerated)
- `docs/settings/settings-annotations.json` (add `rowEnableStreams`)
- `docs/SETTINGS_REFERENCE*.md` (re-rendered)

## 6. Device test

- Settings > Media > Streams shows the Enable-Streams toggle and, below it, a "Трансляции" button that opens the Streams screen.
- With Enable-Streams OFF the "Трансляции" button is hidden; toggling it ON makes the button appear immediately (and OFF hides it again).
- Settings > Player no longer shows a Streams button.
- Both checks hold in portrait and landscape.
- Settings search for "Трансляции"/"Streams" returns a result that navigates to Media > Streams and expands the section.
- Relationship: supersedes S0575 device-test point 5 (the Player-tab Streams shortcut is removed, not kept).

## 7. Incidental repairs (not part of the streams move)

- Regenerating the settings manifest surfaced two pre-existing gate failures on the working tree, fixed here to keep the shared gates green:
- Camera `btnGalleryThumbnail` (gallery preview) had no focus indication - added a borderless ripple in portrait and landscape (Rule 16).
- `btnTakeScreenshotNow` (S0559 screenshot button) shipped without a settings annotation - added en/ru/uk so the regenerated manifest passes annotation coverage.
- Both are logged as a separate dev-log entry and are unrelated to the Streams shortcut.
