# Tactical Plan: S0995 - Manual 90° rotation in players

**Ticket:** S0995
**Status:** Tactical
**Strategic spec:** `PLAN/S0995_player-manual-rotate-90.md`
**Research:** `research/01__player-family-rotation-map.md` (mechanism verdict: all crux dimensions CODE-DETERMINABLE)

---

## Goal (RU)

Низкоприоритетная overflow-команда «Повернуть на 90» в обоих семействах плееров (внутренний `PlayerActivity` + standalone `PhotoVideoStandaloneActivity`), для видео и изображений. Чисто визуальный поворот кадра по часовой (90->180->270->360 сброс), пропорции сохранены, контролы не крутятся, файл не меняется, датчик/настройки экрана не трогаются. Состояние - на сессию плеера (переносится на след. файл, сбрасывается при выходе).

## Mechanism (from research - no device experiment needed)

- **Video:** reuse the existing Media3 `setVideoEffects()` GL pipeline (already `texture_view`, no DRM). Add a rotation `Effect` (`androidx.media3.effect.ScaleAndRotateTransformation`, already on classpath) to the existing effect list in both engines. Respect the 3 documented Media3 1.2.1 crash-workarounds (80ms debounce, defer-until-videoSizeKnown, drain-before-release). Manually compensate aspect-fit at 90/270 (PlayerView won't auto-swap w<->h for post-decode effect rotation).
- **Image:** `photoView.setRotationTo(angle)`; plain `imageView` via `View.rotation` + explicit scale-to-fit-container math at 90/270.
- **State:** two per-family owners - `PlayerViewModel.PlayerState.sessionRotationAngle` and `StandalonePlayerViewModel.StandalonePlayerState.sessionRotationAngle`. Apply-layer via new rotation get/set on shared `VideoPlayerHandle`.

## Phase overview

| Phase | Title | Status | Files (primary) |
|-------|-------|--------|-----------------|
| 01 | Session state + VideoPlayerHandle rotation contract | Pending | PlayerViewModel, StandalonePlayerViewModel, VideoPlayerHandle (+2 impls) |
| 02 | Image rotation apply | Pending | ImageLoadingManager, StandaloneViewManager |
| 03 | Video rotation apply (effect pipeline + aspect-fit) | Pending | VideoColorProcessor→rotation effect, PlayerSetupHelper, StandaloneViewManager, VideoPlayerLifecycleHelper |
| 04 | Command/menu wiring + resources + debug tags | Pending | CommandPanelLayoutPlanner, CommandPanelController, PhotoVideoStandaloneActivity, overflow_menu_standalone_player.xml, ic_rotate_90 drawable, strings ×3 |

## Blockers

- None to start implementation (all mechanism decisions resolved from code).
- **Device-verification gate (F3 terminal):** the video-effect half cannot be validated without a device (crash-resurface with a 4th concurrent effect; aspect-fit clean at 90/270 frame-to-frame; PiP/fullscreen/cast). -> ticket lands `BlockNeedUserTest` after build; `/spec-test-device` drains it when a device is attached.

## Completion gate

- All 4 phases implemented, `standard debug` builds green (detekt-clean-first).
- One `Timber.d("S0995: …")` probe per changed-flow entry (command tap in each family) - present only while `BlockNeedUserTest`.
- Command visible in overflow of internal + `PhotoVideoStandaloneActivity` for video and images; cumulative angle; controls unaffected; new strings EN/RU/UK; new `ic_rotate_90`.
- `docs/ALL_FEATURES.jsonl` record (ADD) on transition out of impl.

## Non-goals (from strategic §2)

- No destructive file rotation (that is `RotateImageUseCase`).
- No screen-orientation-sensor interaction (`toggleRotationSensor`).
- No control rotation. No persistence across player exit.
- Do NOT touch deprecated `StandalonePlayerActivity` (S0393) or audio/document/text hosts.
