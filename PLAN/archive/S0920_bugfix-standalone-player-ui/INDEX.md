# Tactical plan: S0920 - Standalone player UI fixes

**Ticket:** S0920
**Strategic spec:** `PLAN/S0920_bugfix-standalone-player-ui.md`
**Research:** `research/01__standalone-player-defects.md`

## Scope

Five standalone-player defects, fixed inside the standalone family (`ui/player/standalone/*`, `ui/player/helpers/Standalone*`, `DestinationButtonsManager`, shared menu + bottom-panel include, four host layouts). No in-app `PlayerActivity` change.

## Phases

- **Phase 01** - Copy/Move panel expand (#1): bind `CollapsibleSectionHeader` toggles in all four hosts via a `DestinationButtonsManager` binder.
- **Phase 02** - Menu draw-overlay hide (#3): add `menu_draw_overlay` to hide lists in text + audio hosts.
- **Phase 03** - Unified top-toolbar order (#5): move `btnRenameCmd` in the text layout (portrait + landscape) to the canonical position.
- **Phase 04** - Video fullscreen gate + autoplay (#2, #4): gate `PhotoVideoStandaloneActivity` video fullscreen on `openVideoInFullscreen`, use panel-aware entry, verify autoplay.

## Finalization

- Add a top-toolbar order convention note to `docs/ARCHITECTURE.md`.
- Record FIX in `docs/ALL_FEATURES.jsonl`.
- Insert `Timber.d("S0920: ...")` probes at changed entry points, set `BlockNeedUserTest`, run device-test gate.
