# Research 01 - Inventory of settings fragments and element classification

Resolves strategic §6.1. Source: codebase inventory of `app_v2` settings layouts (2026-06-22).

## Fragments in scope

Settings UI = a pager of tab fragments plus a media-container shell hosting child fragments.

| Fragment | Portrait layout | Landscape variant | Structure |
|---|---|:---:|---|
| general | `layout/fragment_settings_general.xml` (~755) | yes (~600+) | cards + CollapsibleSectionHeader |
| playback | `layout/fragment_settings_playback.xml` (~435) | yes (~453) | cards |
| destinations | `layout/fragment_settings_destinations.xml` (~988) | yes (~1207) | cards |
| images | `layout/fragment_settings_images.xml` (~137) | yes | flat LinearLayout (no cards) |
| video | `layout/fragment_settings_video.xml` (~197) | yes | flat |
| audio | `layout/fragment_settings_audio.xml` (~144) | yes | flat |
| other | `layout/fragment_settings_other.xml` (~291) | yes | flat |
| documents | `layout/fragment_settings_documents.xml` (~96) | **NO** | flat, hosted in media_container FrameLayout |
| streams | `layout/fragment_settings_streams.xml` (~32) | **NO** | flat, hosted in media_container FrameLayout |
| media_container | `layout/fragment_settings_media_container.xml` (~122) | **NO** | shell: cards each wrapping a FrameLayout child slot |

## Element classification

Per row-widget type, eligibility for landscape column grouping:

- COMPACT - `SettingsToggleRow` (toggle): best candidate, group 2 per row.
- BUTTONSET - groups of `MaterialButton` / `RadioGroup` / chips: group 3-4+ per row (Flow left-pack or horizontal).
- WIDE - `SettingsDropdownRow` / `SettingsInputRow` / `SettingsSelectionRow` with a value: keep single column (TextInput becomes too narrow in a 50% phone-landscape column).
- HELP - descriptive `TextView`: place beside its control where a natural control sibling exists; otherwise leave full-width.

## Already-paired (no work needed)

- general/land: separate-window+favorites, hide-grid+file-ops-overflow, smb+ftp, network-parallelism+prefetch-cache.
- images/land: load-full-size+crop, support-images+gifs.
- audio/land: covers-wifi+save-metadata.
- playback/land: hide-system-ui+follow-rotation, show-command-panel+detailed-errors, show-hint+always-show-zones.

## Gaps (work for S0609)

- images/land: `rowDynamicBackground`, `rowSlideshowBackgroundMusic` still full-width solo COMPACT.
- video/land: minor; mostly already compacted.
- audio/land: mostly compacted; verify remaining solo toggles.
- general/land System: `rowEnableThumbnailPreload` + `rowThumbnailPreloadWifiOnly` still solo; doc-link buttons use a plain non-wrapping horizontal LinearLayout (should wrap/left-pack like portrait Flow).
- playback/land Player UI: `rowShowBlackScreenButton`, `rowEnablePip`, `rowPanelStereoSingleEye`, `rowBigButtonsMode`, `rowSmallControls` full-width solo; Background-Audio exit-behavior `RadioGroup` is vertical (candidate for horizontal 3-up).
- destinations/land: additional toggle pairs possible, but file already ~1207 lines - guard against the 1500 LOC limit.
- documents / streams: no land variant - create.
- media_container: no land variant - the shell cards each wrap a FrameLayout; decide minimal land shell (see research 05).

## Long-label risk strings (watch EN/RU/UK)

`restore_from_google_drive`, `open_source_licenses_title`, `show_command_panel_by_default`, `always_show_touch_zones_overlay`, `setting_follow_system_rotation_player_title`, `save_audio_metadata_locally`, `enable_slideshow_background_music`.
