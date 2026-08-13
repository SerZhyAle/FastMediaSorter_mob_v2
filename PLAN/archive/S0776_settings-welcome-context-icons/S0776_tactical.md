# Tactical spec: S0776 - Context icons on Settings & Welcome

**Ticket:** S0776
**Strategic:** `PLAN/S0776_settings-welcome-context-icons.md`
**Status mirror:** see catalog (`select.ps1 -Id S0776`)

> Scope: TACTICAL. Concrete files, widgets, attributes, and the canonical concept->drawable map. Owner decisions locked in strategic §3.3: curated feature surfaces, monochrome theme tint, toggles + group headers only.

---

## 1. Foundation (shared widgets - single owner, hard barrier before wiring)

Add an optional leading icon slot to the two widgets that lack one, mirroring the existing `SettingsSelectionRow` slot (`ssr_icon` / `setIcon()`), then add the few missing neutral mono icons.

### 1.1 attrs.xml
- `SettingsToggleRow` styleable: add `<attr name="str_icon" format="reference" />`.
- `CollapsibleSectionHeader` styleable: add `<attr name="csh_icon" format="reference" />`.

### 1.2 view_settings_toggle_row.xml
- Insert a leading `ImageView` `@+id/str_icon` BETWEEN `str_switch` and `str_textGroup` (preserves switch-leftmost Pattern A; icon sits next to the title).
- Style mirrors `ssr_icon`: `layout_width/height=@dimen/settings_help_icon_size`, `layout_gravity=center_vertical`, `layout_marginEnd=@dimen/settings_help_icon_margin`, `contentDescription=@null`, `importantForAccessibility=no`, `app:tint=?attr/colorOnSurfaceVariant`, `visibility=gone`.

### 1.3 SettingsToggleRow.kt
- Add `private val iconView: ImageView` + `findViewById(R.id.str_icon)`.
- Add `setIcon(icon: Drawable?)` and `setIcon(@DrawableRes resId: Int)` mirroring `SettingsSelectionRow`.
- In `applyAttributes`: read `str_icon` resourceId, call `setIcon` when `!= 0`.

### 1.4 view_collapsible_section_header.xml
- Insert a leading `ImageView` `@+id/csh_icon` BETWEEN `csh_chevron` and `csh_prefix`.
- Style: `layout_width/height=@dimen/settings_help_icon_size`, `layout_marginEnd=@dimen/settings_padding_vertical` (match chevron), `importantForAccessibility=no`, `app:tint=?attr/colorOnSurfaceVariant`, `visibility=gone`.

### 1.5 CollapsibleSectionHeader.kt
- Add `private val iconView: ImageView` + `findViewById(R.id.csh_icon)`.
- Add `setIcon(icon: Drawable?)` / `setIcon(@DrawableRes resId: Int)`.
- In `applyAttributes`: read `csh_icon` resourceId, call `setIcon` when `!= 0`.

### 1.6 New mono drawables (tintable 24dp VD, mirror ic_settings.xml style)
- `ic_apps.xml` - 2x2 grid (Additional Programs group).
- `ic_gesture.xml` - tap/swipe glyph (Screen Gestures group + gesture toggles).
- `ic_cloud.xml` - plain cloud (cloud network source; neutral mono, not the coloured `ic_resource_cloud`).

---

## 2. Canonical concept -> drawable map

All existing unless marked NEW. All rendered monochrome via the widget's `?attr/colorOnSurfaceVariant` tint.

- Images -> `ic_image`
- Video -> `ic_video`
- Audio -> `ic_audio`
- Documents -> `ic_book`
- Slideshow -> `ic_slideshow`
- VR -> `ic_vr_headset`
- Streams / broadcast -> `ic_cast`
- Remote sources (group) -> `ic_wifi`
- Network SMB/LAN -> `ic_wifi`
- Network FTP -> `ic_storage`
- Network Cloud -> `ic_cloud` (NEW)
- File browser / file manager -> `ic_folder`
- Calculator -> `ic_calculator`
- OCR -> `ic_ocr`
- Translation -> `ic_translate`
- Gestures -> `ic_gesture` (NEW)
- Programs panel -> `ic_apps` (NEW)
- Scheduled operations -> `ic_schedule`
- Statistics -> `ic_history`
- Camera capture -> `ic_camera_capture`
- Video capture -> `ic_video`
- Mic recording -> `ic_microphone`
- Screen recording -> `ic_display`
- Background audio -> `ic_audio`

---

## 3. Wiring (parallel, disjoint file sets - each agent mirrors its layout-land counterpart, Rule 11)

Wiring is pure XML: `app:csh_icon="@drawable/.."` on curated group headers, `app:str_icon="@drawable/.."` on curated toggle rows. No Kotlin, no new strings. `xmlns:app` already present in every target layout.

### W1 - General tab (`fragment_settings_general.xml` + layout-land)
- Header `headerFileBrowser` -> `ic_folder`.
- Header `headerRemoteSources` -> `ic_wifi`.
- Toggle `rowSourceSmb` -> `ic_wifi`; `rowSourceFtp` -> `ic_storage`; `rowSourceCloud` -> `ic_cloud`.
- (Interface / Authorization / App Data / System headers: skip - abstract.)

### W2 - Media tab (`fragment_settings_media_container.xml` + the 6 sub-fragment layouts)
- Container headers: `headerImages`->`ic_image`, `headerVideo`->`ic_video`, `headerVr`->`ic_vr_headset`, `headerAudio`->`ic_audio`, `headerDocuments`->`ic_book`, `headerStreams`->`ic_cast`. (`headerOther`: skip.)
- `fragment_settings_images.xml`: `rowSupportImages`->`ic_image`.
- `fragment_settings_video.xml`: `rowSupportVideos`->`ic_video`.
- `fragment_settings_audio.xml`: `rowSupportAudio`->`ic_audio`.
- `fragment_settings_documents.xml`: `rowSupportText`/`rowSupportPdf`/`rowSupportEpub`/`rowSupportOfficeDocuments` -> `ic_book`.
- `fragment_settings_other.xml`: `rowEnableTranslation`->`ic_translate`, `rowEnableOcr`->`ic_ocr`.
- `fragment_settings_streams.xml`: `rowEnableStreams`->`ic_cast`.
- (Verify each sub-fragment layout for a layout-land counterpart; mirror if present.)

### W3 - Playback tab (`fragment_settings_playback.xml` + layout-land)
- Header `headerSortingSlideshow` -> `ic_slideshow`.
- Header `headerBackgroundAudio` -> `ic_audio`.
- Toggle `rowEnablePersistentAudioPlayback` -> `ic_audio`.
- (File Operations / Player UI / Touch Zones / Send Commands headers: skip.)

### W4 - Operations tab (`fragment_settings_destinations.xml` + layout-land if present)
- Header `headerScheduled` -> `ic_schedule`.
- Header `headerOtherFeatures` -> `ic_camera_capture`.
- Header `headerAdditionalPrograms` -> `ic_apps`.
- Header `headerScreenGestures` -> `ic_gesture`.
- Toggles: `rowEnableScheduledOps`->`ic_schedule`, `rowCameraToResourceEnabled`->`ic_camera_capture`, `rowVideoCaptureEnabled`->`ic_video`, `rowMicRecordingEnabled`->`ic_microphone`, `rowScreenRecordingEnabled`->`ic_display`, `rowEnableCalculator`->`ic_calculator`, `rowGestureOverlayEnabled`->`ic_gesture`.
- (Safety / Copy&Move / Destinations / Behaviour / System Apps headers: skip. `rowEmbeddedGame`: skip - no clean asset.)

### W5 - Welcome (`page_welcome_functionality.xml` + layout-land, `page_welcome_networks.xml` + layout-land)
- Functionality toggles: `rowFileManager`->`ic_folder`, `rowAudio`->`ic_audio`, `rowVideo`->`ic_video`, `rowStreams`->`ic_cast`, `rowStatistics`->`ic_history`, `rowDocuments`->`ic_book`, `rowOcr`->`ic_ocr`, `rowTranslation`->`ic_translate`, `rowGestures`->`ic_gesture`.
- Networks toggles: `rowSourceSmb`->`ic_wifi`, `rowSourceFtp`->`ic_storage`, `rowSourceCloud`->`ic_cloud`.

---

## 4. Build & verify (central)

- `.\a.ps1 fc` (Kotlin compile + resources) on standard.
- Static gates: `.\a.ps1 fg` (neuroslop / listener / flavor-flag / ticket-log) + detekt on touched `.kt`.
- Settings docs gate (Rule 22): no setting presence/behaviour/position/naming changed (icons are decorative-only), so the manifest is unaffected; confirm `assert-settings-doc-sync.ps1` stays green.
- Add the `BlockNeedUserTest` debug tag at the changed entry flow before the final build, then transition to BlockNeedUserTest with a StatusNote describing the on-device check (light/dark + landscape, Settings + Welcome).

---

## 5. Phases

1. Foundation (§1) - one owner (central), shared files.
2. Wiring (§3) - five parallel agents, disjoint files, no build/git.
3. Build + gates (§4) - central.
4. Device test - BlockNeedUserTest.
