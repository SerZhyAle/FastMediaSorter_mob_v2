# S0964 PHASE 02 - Panel model: track rows, controller, dispatcher, strings

**Goal:** `HudCanvasRenderer` panel carries AUDIO/SUBS cycle rows backed by `VideoTrackSelectionManager`; interaction dispatcher routes arrow clicks.

## Steps

- [x] 02.1 Strings (hand-edit, `src/vr/res/values{,-ru,-uk}/strings.xml` - create files if absent; project string tools cover main/res only):
  - `vr_hud_audio_label` = "AUDIO" / "АУДИО" / "АУДІО"
  - `vr_hud_subs_label` = "SUBS" / "СУБТИТРЫ" / "СУБТИТРИ"
  - `vr_hud_subs_off` = "Off" / "Выкл" / "Вимк"
  - `vr_hud_no_tracks` = "-" (same all locales)
  - Verify parity: grep each key in all three files.
- [x] 02.2 New `ui/xr/helpers/HudTrackController.kt` (src/vr):
  - Constructor `(getPlayer: () -> ExoPlayer?)`; owns a `VideoTrackSelectionManager(getPlayer, { null })`.
  - `audioLabel(): String` - label of selected audio track or no-tracks placeholder.
  - `subtitleLabel(offLabel: String): String` - selected text track label, `offLabel` when TEXT disabled/none selected.
  - `cycleAudio(step: Int)` - move selection through `getAvailableAudioTracks()` (wrap around); no-op when < 2 tracks.
  - `cycleSubtitle(step: Int)` - cycle through OFF + `getAvailableSubtitleTracks()` (OFF maps to `selectSubtitleTrack(-1, -1)`); no-op when list empty.
  - Main-thread marshalling mirrors `HudPlaybackController` (`Handler(Looper.getMainLooper())`) - track selection touches ExoPlayer.
- [x] 02.3 `HudCanvasRenderer.kt`:
  - `HEIGHT` 512 -> 640 (room for two rows; UV mapping in dispatcher follows the const).
  - New rects: `audioPrevRect`/`audioNextRect`/`subsPrevRect`/`subsNextRect` (arrow buttons >= 60x60 px hit zones) + label areas; place rows between the status line and the transport buttons; shift transport/slider rows down accordingly.
  - New state: `audioTrackLabel: String`, `subtitleTrackLabel: String`, `audioRowEnabled: Boolean`, `subsRowEnabled: Boolean`, plus static row captions passed in (localized) - renderer stays Context-free.
  - Render rows: caption, `◀` arrow, current label, `▶` arrow; disabled row drawn with dimmed paint.
- [x] 02.4 `HudInteractionDispatcher.kt`: extend `InteractionListener` with `onAudioTrackCycle(step: Int)` and `onSubtitleTrackCycle(step: Int)`; hit-test the four arrow rects on `clickTriggered` (respect enabled flags via renderer state).

## Verification

- No `BuildConfig` flavor guards; all new code in `src/vr` except zero changes to `src/main`.
- Renderer has no Android Context/resources dependency (labels injected).
- detekt-clean authoring: no bare numeric literals in logic (layout px consts allowed as named companion consts), log lines <= 120 chars.
