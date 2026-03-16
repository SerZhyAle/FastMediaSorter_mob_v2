# Implementation Roadmap: Default Player & Standalone Activity

This document outlines the high-level strategic steps to implement the "Default Player" features based on the approved specification. Each phase produces a fully compilable and functioning build layer.

---

## 📅 Phase 1: StandalonePlayerActivity (Base UI & Core Logic)
- **Goal**: Create an independent viewer Activity completely detached from the main resource/database system.
- **Strategic Steps**:
  - Implement base display structure for all 7 media types (`IMAGE`, `GIF`, `VIDEO`, `AUDIO`, `PDF`, `EPUB`, `TEXT`) via type-routing stubs.
  - Parse incoming URI from `Intent.ACTION_VIEW`; resolve display name via `ContentResolver`; detect type via `MediaTypeUtils`.
  - Wire close button (`binding.btnBack`) and hardware Back to `finish()` — no navigation to Browse or Main.
  - Hide Next/Previous playlist controls (`View.GONE`) — standalone mode has no playlist.

---

## 📅 Phase 2: Intent Filters & Activity Aliases
- **Goal**: Register the application structure in target Android Intent channels.
- **Strategic Steps**:
  - Declare 4 `<activity-alias>` entries in `AndroidManifest.xml` (video, audio, image, document), each targeting `StandalonePlayerActivity`. Separate aliases allow per-type toggling via `PackageManager.setComponentEnabledSetting()`.
  - Add `ACTION_VIEW` `<intent-filter>` with appropriate MIME type groups to each alias, covering all types defined in `MediaTypeUtils`.
  - Declare both `content://` and `file://` schemes in each intent filter for maximum compatibility.

---

## 📅 Phase 3: Settings Screen Default Associations UI
- **Goal**: Provide manual triggers allowing users to map the application defaults easily.
- **Strategic Steps**:
  - Add a "Set as default player" button in `AudioSettingsFragment`, `VideoSettingsFragment`, `MediaSettingsFragment`. Tapping opens the system Default Apps screen via `Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)`.
  - For API 29+, check current default role state (`RoleManager`); dim/disable the button if the app is already the default.
  - Show a brief explanatory dialog before redirecting to system settings.

---

## 📅 Phase 4: Welcome Screen Onboarding Defaults
- **Goal**: Promote application defaults assignment triggers for new installations structure.
- **Strategic Steps**:
  - Add a one-time onboarding card on `WelcomeActivity` after first install, prompting the user to set the app as the default media player.
  - Provide "Set as default" and "Skip" buttons. "Set as default" opens the system Default Apps screen. The card is skippable but not suppressed on first launch.

---

## 📅 Phase 5: Hardware Buttons Control (MediaButtonReceiver)
- **Goal**: Intercept hardware media buttons (play/pause/next/prev on car stereo, headphones) so they control our `AudioPlaybackService` instead of the system default player.
- **Strategic Steps**:
  - Configure `<receiver>` for `MediaButtonReceiver` in `AndroidManifest.xml`, targeting `AudioPlaybackService`.
  - Register a `MediaSession` inside `AudioPlaybackService` with correct `setMediaButtonReceiver()` to claim priority over hardware buttons.
  - Implement quiet (silent) service restart when the app is killed and a hardware Play button is pressed — no notification shown, user is driving.
  - Add a new toggle **"Use as primary system media player"** in **Playback Settings**. `MediaButtonReceiver` is active **only** when this toggle is ON (independent of "Play in background" setting).
  - This toggle also controls enabling/disabling the `ACTION_VIEW` activity aliases via `PackageManager.setComponentEnabledSetting()`.

---

## 📅 Phase 6: ACTION_SEND & Sharing Intakes
- **Goal**: Allow other apps to open media files directly in FastMediaSorter via the system Share sheet (`ACTION_SEND`).
- **Strategic Steps**:
  - Add `ACTION_SEND` intent filter to the image/video/audio `<activity-alias>` entries.
  - Extract the URI from `Intent.EXTRA_STREAM` and route to the same playback flow as `ACTION_VIEW`.
  - Gate `ACTION_SEND` alias activation via a dedicated toggle in Playback Settings, controlled via `PackageManager.setComponentEnabledSetting()`.

---

## 📅 Phase 7: Build-Time Flavor Exclusion
- **Goal**: Exclude default player components from product flavors that do not support this feature (e.g. `lite`).
- **Strategic Steps**:
  - Use flavor-specific `AndroidManifest.xml` overlays (or manifest placeholders) to disable `StandalonePlayerActivity` aliases and `MediaButtonReceiver` for non-supporting flavors.
  - Gate the Default Player settings section visibility at compile time via a build config flag (`BuildConfig.SUPPORTS_DEFAULT_PLAYER`).
