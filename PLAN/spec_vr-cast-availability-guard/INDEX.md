# Tactical Spec: vr-cast-availability-guard

**Status:** Approved
**Strategic spec:** `PLAN/spec_vr-cast-availability-guard.md`
**Created:** 2026-04-26

## Goal

Eliminate repeated Cast SDK initialization warnings on Quest 3 / Horizon OS by introducing a compile-time `BuildConfig.SUPPORT_CAST` flag that gates all Cast SDK init and UI on non-VR flavors. VR flavor gets `SUPPORT_CAST = false`, making the decision once at build time rather than failing at runtime on every player launch.

## Phases

| Phase | File | Description |
|-------|------|-------------|
| [Phase 1](phase_1_build_flag.md) | `build.gradle.kts` | Add `SUPPORT_CAST` BuildConfig field to all flavors |
| [Phase 2](phase_2_app_init_guard.md) | `FastMediaSorterApp.kt` | Guard app-level CastContext init |
| [Phase 3](phase_3_cast_manager_guard.md) | `CastMediaManager.kt` | Guard player-level Cast init with early return |
| [Phase 4](phase_4_ui_guard.md) | `CommandPanelLayoutPlanner.kt` + `CommandPanelController.kt` | Hide cast button on unsupported flavors |

## Open Research Items

All resolved inline during F1:

- **Q1 (capability storage):** `BuildConfig.SUPPORT_CAST` is the process-level verdict — compile-time, no runtime singleton needed.
- **Q2 (UI hide vs disable):** Hidden entirely when `SUPPORT_CAST = false` — same pattern as `SUPPORT_VR_PLAYER`.
- **Q3 (flavor vs runtime guard):** Strict flavor gate: vr/vrUnlicensed always lack Google Play Services, runtime probe adds no value.
