# S1115 - Tactical INDEX

**Ticket:** S1115
**Strategic spec:** `PLAN/S1115_player-exit-fullscreen-button.md`
**Status:** BlockNeedUserTest
**Phases:** 2/2 done

## Pre-Implementation Blockers

- [x] Active standalone video host confirmed: `PhotoVideoStandaloneActivity` (its own layout `activity_standalone_photo_video.xml`; legacy `StandalonePlayerActivity` is `@Deprecated`, out of scope).
- [x] Main-host exit-button visibility already recomputed centrally on every panel toggle; click already exits fullscreen.
- [x] No new strings: reuse `R.string.exit_fullscreen`.

## Phases

| Phase | Title | Status | Steps |
| --- | --- | --- | --- |
| 01 | main-host-video-exit | ✅ Done | 1/1 |
| 02 | standalone-video-exit | ✅ Done | 2/2 |

## Completion Gate

- Project compiles (standard debug).
- Exit-fullscreen overlay button appears in video fullscreen on both hosts and restores the command panel.

## Change Log

- 2026-07-19 - tactical plan created.
