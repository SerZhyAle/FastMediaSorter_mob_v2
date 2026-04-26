# Tactical Spec: Player Lifecycle Cancellation Resilience

**Strategic spec:** `PLAN/spec_player-lifecycle-cancellation.md`
**Status:** Approved

## Phases

| # | File | Title | Status |
|---|------|-------|--------|
| 1 | [phase_01_cancellation_guard.md](phase_01_cancellation_guard.md) | Cancellation guard in playVideo catch | `[x]` |

## Acceptance (strategic-level)

- `JobCancellationException` during lifecycle destroy → no `Failed to play video` in user UI.
- Real playback failures → `Timber.e` + `showError` as before.
- Cancellation logged at `Timber.d` level.
